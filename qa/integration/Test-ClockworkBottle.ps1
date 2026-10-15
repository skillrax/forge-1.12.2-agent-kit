[CmdletBinding()]
param([string]$ArchiveManifest,[ValidateSet('Reproduce','Regression')][string]$Mode='Reproduce')
$ErrorActionPreference='Stop'
[Console]::OutputEncoding=New-Object Text.UTF8Encoding($false)
$OutputEncoding=[Console]::OutputEncoding
$root=Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$shell=(Get-Process -Id $PID).Path
$fixture=Join-Path $root 'qa/fixtures/clockwork-bottle'
$cases=New-Object 'System.Collections.Generic.List[object]'
function Invoke-Check([string]$Profile,[int]$ExpectedExit=0,[string]$Marker='') {
    $output=& $shell -NoProfile -File (Join-Path $workspace 'agent.ps1') -Action check -Profile $Profile -TimeoutSeconds 180
    $exitCode=$LASTEXITCODE
    $result=($output -join "`n") | ConvertFrom-Json
    $cases.Add(@{profile=$Profile;status=$result.status;runId=$result.runId;elapsedSeconds=$result.elapsedSeconds;log=$result.log})
    if($exitCode -ne $ExpectedExit -or $result.status -ne $(if($ExpectedExit -eq 0){'PASS'}else{'FAIL'})){throw "Unexpected $Profile result: $output"}
    if($Marker -and -not [IO.File]::ReadAllText($result.log).Contains($Marker)){throw "Missing marker: $Marker"}
    return $result
}
function Set-Phase([string]$Phase) {
    [IO.File]::WriteAllText($buildPath,$buildBase+$overlay.Replace("'-Dclockwork.phase=save'",("'-Dclockwork.phase="+$Phase+"'")))
}
try {
    if(-not $ArchiveManifest){$active=Get-Content -Encoding UTF8 (Join-Path $root 'kit/kit.json') -Raw | ConvertFrom-Json;$ArchiveManifest=Join-Path $root ('releases/'+$active.version+'/manifest.json')}
    $archive=Get-Content -Encoding UTF8 -LiteralPath $ArchiveManifest -Raw | ConvertFrom-Json
    if((Get-FileHash -LiteralPath $archive.archive).Hash -ne $archive.archiveSha256){throw 'Frozen archive mismatch'}
    Add-Type -AssemblyName System.IO.Compression.FileSystem
    $destination=Join-Path $root ('.work/clockwork-repeat/'+[Guid]::NewGuid().ToString('N'))
    [IO.Compression.ZipFile]::ExtractToDirectory($archive.archive,$destination)
    $workspace=Join-Path $destination $archive.archiveRoot
    foreach($name in @('main','test','evaluation')){Copy-Item -LiteralPath (Join-Path $fixture ('src/'+$name)) -Destination (Join-Path $workspace 'src') -Recurse -Force}
    $buildPath=Join-Path $workspace 'build.gradle'
    $metadata=Get-Content -Encoding UTF8 -LiteralPath (Join-Path $fixture 'src/main/resources/mcmod.info') -Raw | ConvertFrom-Json
    $buildBase=[IO.File]::ReadAllText($buildPath).Replace("group = 'com.example'","group = 'dev.agentic.clockworkbottle'").Replace("version = '1.0.0'",("version = '"+$metadata[0].version+"'"))+"`n"
    $overlay=[IO.File]::ReadAllText((Join-Path $fixture 'build-overlay.gradle'))
    [IO.File]::WriteAllText((Join-Path $workspace 'settings.gradle'),"rootProject.name = 'clockwork-bottle'`n")
    Set-Phase save
    $save=Invoke-Check server 0 'CLOCKWORK_ASSERTIONS_PASS phase=save'
    Set-Phase restore
    $restore=Invoke-Check server 0 'CLOCKWORK_ASSERTIONS_PASS phase=restore'
    if($Mode -eq 'Regression') {
        $mathPath=Join-Path $workspace 'src/main/java/dev/agentic/clockworkbottle/ChargeMath.java'
        $mathText=[IO.File]::ReadAllText($mathPath)
        try {
            if(-not $mathText.Contains('safe+1')){throw 'Fault injection target absent'}
            [IO.File]::WriteAllText($mathPath,$mathText.Replace('safe+1','safe+2'))
            $bad=Invoke-Check server 1 'CLOCKWORK_ASSERTIONS_FAIL: Inventory tick cadence wrong'
            $errors=& $shell -NoProfile -File (Join-Path $workspace 'agent.ps1') -Action errors -RunId $bad.runId
            if($errors -notmatch 'Inventory tick cadence wrong'){throw 'Bounded diagnostics lost accounting cause'}
        } finally {[IO.File]::WriteAllText($mathPath,$mathText)}
        $restore=Invoke-Check server 0 'CLOCKWORK_ASSERTIONS_PASS phase=restore'
    }
    # Phase changes are inputs: obtain fresh mod evidence before exporting.
    $finalMod=Invoke-Check mod
    $zip=[IO.Compression.ZipFile]::OpenRead($finalMod.artifact.path)
    try {
        if($zip.Entries.FullName -match 'clockworkchecks|ChargeMathTest|EnvironmentProbe|\.java$'){throw 'Acceptance/test code leaked into JAR'}
        foreach($entry in @('mcmod.info','assets/clockworkbottle/recipes/clockwork_bottle.json','assets/clockworkbottle/models/item/clockwork_bottle.json','assets/clockworkbottle/lang/en_us.lang')){if(-not $zip.GetEntry($entry)){throw "Missing resource: $entry"}}
    }finally{$zip.Dispose()}
    $result=[ordered]@{status='PASS';mode=$Mode;faultCoverage=if($Mode -eq 'Regression'){'PASS'}else{'NOT_RUN'};workspace=$workspace;kitArchiveSha256=$archive.archiveSha256;cases=$cases;artifact=$finalMod.artifact;scope='Seeded deterministic reproduction; actual inventory hooks, vanilla save/load across server JVMs; no live client/login proof';graphicalClientTested=$false;networkedRelogTested=$false}
    $exitCode=0
}catch{$result=[ordered]@{status='FAIL';message=$_.Exception.Message;workspace=$workspace;cases=$cases};$exitCode=1}
$evidenceDir=Join-Path $root 'evidence/clockwork-bottle'
New-Item -ItemType Directory -Force -Path $evidenceDir | Out-Null
$json=$result | ConvertTo-Json -Depth 8 -Compress
[IO.File]::WriteAllText((Join-Path $evidenceDir ('acceptance-'+$Mode.ToLowerInvariant()+'-'+[DateTime]::UtcNow.ToString('yyyyMMddTHHmmssfff')+'.json')),$json)
$json
exit $exitCode
