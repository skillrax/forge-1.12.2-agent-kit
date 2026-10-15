[CmdletBinding()]
param([string]$Workspace,[string]$ArchiveManifest,[switch]$ReuseEvidence,[ValidateSet('Reproduce','Regression')][string]$Mode='Reproduce',[ValidateSet('clockwork-furnace','clockwork-labels')][string]$FixtureName='clockwork-furnace')
$ErrorActionPreference='Stop'
[Console]::OutputEncoding=New-Object Text.UTF8Encoding($false)
$OutputEncoding=[Console]::OutputEncoding
$root=Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$fixture=Join-Path $root ('qa/fixtures/'+$FixtureName)
$cases=New-Object 'System.Collections.Generic.List[object]'
$createdWorkspace=-not $Workspace
function Invoke-Native([string]$Phase,[int]$ExpectedExit=0,[switch]$Force) {
    $result=$null
    $status=& powershell -NoProfile -File (Join-Path $Workspace 'agent.ps1') -Action status
    if($LASTEXITCODE -ne 0){throw 'Cannot read input identity'}
    $identity=($status -join "`n") | ConvertFrom-Json
    if($ReuseEvidence -and -not $Force -and $ExpectedExit -eq 0) {
        $reportDir=Join-Path $Workspace 'reports/furnace-acceptance'
        if(Test-Path $reportDir) {
            $candidate=Get-ChildItem -LiteralPath $reportDir -Filter '*.json' | ForEach-Object {Get-Content -Encoding UTF8 -LiteralPath $_.FullName -Raw | ConvertFrom-Json} | Where-Object {$_.phase -eq $Phase -and $_.inputFingerprint -eq $identity.inputFingerprint -and $_.toolchainFingerprint -eq $identity.toolchainFingerprint} | Sort-Object runId -Descending | Select-Object -First 1
            if($candidate -and $candidate.status -eq 'PASS'){$result=$candidate;$reused=$true}
        }
    }
    if(-not $result) {
        $output=& powershell -NoProfile -File (Join-Path $PSScriptRoot 'Invoke-FurnaceServer.ps1') -Workspace $Workspace -Phase $Phase
        $nativeExit=$LASTEXITCODE;$result=($output -join "`n") | ConvertFrom-Json;$reused=$false
        if($nativeExit -ne $ExpectedExit){throw "Unexpected native exit: $output"}
    }
    if($result.status -ne $(if($ExpectedExit -eq 0){'PASS'}else{'FAIL'})){throw 'Native status mismatch'}
    $cases.Add(@{phase=$Phase;status=$result.status;runId=$result.runId;reused=$reused;elapsedSeconds=$result.elapsedSeconds;log=$result.log})
    return $result
}
try {
    if(-not $ArchiveManifest){$active=Get-Content -Encoding UTF8 (Join-Path $root 'kit/kit.json') -Raw | ConvertFrom-Json;$ArchiveManifest=Join-Path $root ('releases/'+$active.version+'/manifest.json')}
    $archive=Get-Content -Encoding UTF8 -LiteralPath $ArchiveManifest -Raw | ConvertFrom-Json
    if((Get-FileHash -LiteralPath $archive.archive).Hash -ne $archive.archiveSha256){throw 'Kit ZIP mismatch'}
    if(-not $Workspace) {
        Add-Type -AssemblyName System.IO.Compression.FileSystem
        $destination=Join-Path $root ('.work/furnace-repeat/'+[Guid]::NewGuid().ToString('N'))
        [IO.Compression.ZipFile]::ExtractToDirectory($archive.archive,$destination);$Workspace=Join-Path $destination $archive.archiveRoot
        foreach($name in @('main','test','evaluation')){Copy-Item -LiteralPath (Join-Path $fixture ('src/'+$name)) -Destination (Join-Path $Workspace 'src') -Recurse -Force}
        $metadata=Get-Content -Encoding UTF8 (Join-Path $fixture 'src/main/resources/mcmod.info') -Raw | ConvertFrom-Json
        $buildPath=Join-Path $Workspace 'build.gradle'
        $text=[IO.File]::ReadAllText($buildPath).Replace("group = 'com.example'","group = 'dev.agentic.clockworkbottle'").Replace("version = '1.0.0'",("version = '"+$metadata[0].version+"'"))
        [IO.File]::WriteAllText($buildPath,$text+"`n"+[IO.File]::ReadAllText((Join-Path $fixture 'build-overlay.gradle')))
        [IO.File]::WriteAllText((Join-Path $Workspace 'settings.gradle'),"rootProject.name = 'clockwork-bottle'`n")
        Copy-Item -LiteralPath (Join-Path $fixture 'baseline-1.0.1-player.dat') -Destination $Workspace
    }
    $Workspace=[IO.Path]::GetFullPath($Workspace)
    foreach($file in (Get-ChildItem -LiteralPath (Join-Path $fixture 'src/main') -Recurse -File)) {
        $relative=$file.FullName.Substring((Join-Path $fixture 'src/main/').Length)
        if((Get-FileHash -LiteralPath $file.FullName).Hash -ne (Get-FileHash -LiteralPath (Join-Path $Workspace ('src/main/'+$relative))).Hash){throw ('Production fixture differs: '+$relative)}
    }
    $baseline=Get-Content -Encoding UTF8 (Join-Path $fixture 'baseline-1.0.1-player.json') -Raw | ConvertFrom-Json
    if((Get-FileHash -LiteralPath (Join-Path $Workspace 'baseline-1.0.1-player.dat')).Hash -ne $baseline.fixtureSha256){throw 'Saved baseline input differs'}
    $live=Invoke-Native live;$restore=Invoke-Native restore
    if($Mode -eq 'Regression') {
        if($FixtureName -eq 'clockwork-labels') {
            $faultFile='FurnaceBoosts.java';$faultTarget='if (!world.isRemote) BoostNetwork.CHANNEL.sendToDimension'
            $faultReplacement='if (false) BoostNetwork.CHANNEL.sendToDimension';$faultMessage='Boost label was not broadcast'
        }else{
            $faultFile='BottleItem.java';$faultTarget='state.setInteger("ChargeTicks",remainder)'
            $faultReplacement='state.setInteger("ChargeTicks",chargeTicks(stack))';$faultMessage='First boost cost incorrect'
        }
        $path=Join-Path $Workspace ('src/main/java/dev/agentic/clockworkbottle/'+$faultFile);$original=[IO.File]::ReadAllText($path)
        try {
            if(-not $original.Contains($faultTarget)){throw 'Regression fault target absent'}
            [IO.File]::WriteAllText($path,$original.Replace($faultTarget,$faultReplacement))
            $fault=Invoke-Native live 1 -Force
            if(-not [IO.File]::ReadAllText($fault.log).Contains('FURNACE_ASSERTIONS_FAIL: '+$faultMessage)){throw 'Wrong fault cause'}
        }finally{[IO.File]::WriteAllText($path,$original)}
        $live=Invoke-Native live 0 -Force;$restore=Invoke-Native restore 0 -Force
    }
    $output=& powershell -NoProfile -File (Join-Path $Workspace 'agent.ps1') -Action check -Profile mod -TimeoutSeconds 180
    if($LASTEXITCODE -ne 0){throw "Final mod check failed: $output"}
    $mod=($output -join "`n") | ConvertFrom-Json
    if($mod.inputFingerprint -ne $live.inputFingerprint -or $mod.inputFingerprint -ne $restore.inputFingerprint){throw 'Final runtime/build input mismatch'}
    Add-Type -AssemblyName System.IO.Compression.FileSystem
    $jar=[IO.Compression.ZipFile]::OpenRead($mod.artifact.path)
    try{if($jar.Entries.FullName -match 'furnacechecks|clockworkchecks|Test\.class|EnvironmentProbe|\.java$'){throw 'Acceptance/test code leaked into JAR'}}finally{$jar.Dispose()}
    $result=[ordered]@{status='PASS';mode=$Mode;createdWorkspace=$createdWorkspace;workspace=$Workspace;kitArchiveSha256=$archive.archiveSha256;cases=$cases;modRun=$mod.runId;artifact=$mod.artifact;faultCoverage=if($Mode -eq 'Regression'){'PASS'}else{'NOT_RUN'};scope='Seeded extension reproduction, actual340 server ticks and vanilla saved-data compatibility/restart; no graphical/networked-login claim'};$exitCode=0
}catch{$result=[ordered]@{status='FAIL';message=$_.Exception.Message;workspace=$Workspace;cases=$cases};$exitCode=1}
$evidence=Join-Path $root ('evidence/'+$FixtureName);New-Item -ItemType Directory -Force -Path $evidence | Out-Null
$json=$result | ConvertTo-Json -Depth 8 -Compress
[IO.File]::WriteAllText((Join-Path $evidence ('acceptance-'+[DateTime]::UtcNow.ToString('yyyyMMddTHHmmssfff')+'.json')),$json);$json
exit $exitCode
