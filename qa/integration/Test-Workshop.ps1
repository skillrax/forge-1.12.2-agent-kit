[CmdletBinding()]
param([string]$Workspace,[ValidateSet('Reproduce','Regression')][string]$Mode='Reproduce',[switch]$ReuseEvidence,[ValidateSet('Processor','Pouch','NPC','Quest','Projectile','WorldContent','Crop','Placement','Dimension')][string]$Fault='Crop')
$ErrorActionPreference='Stop'
[Console]::OutputEncoding=New-Object Text.UTF8Encoding($false)
$taskRoot=Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$fixturePath=Join-Path $taskRoot 'qa/fixtures/workshop'
$version=(Get-Content -Encoding UTF8 (Join-Path $fixturePath 'src/main/resources/mcmod.info') -Raw | ConvertFrom-Json)[0].version
$cases=New-Object 'System.Collections.Generic.List[object]'
$active=Get-Content -Encoding UTF8 (Join-Path $taskRoot 'kit/kit.json') -Raw | ConvertFrom-Json
$archive=Get-Content -Encoding UTF8 (Join-Path $taskRoot ('releases/'+$active.version+'/manifest.json')) -Raw | ConvertFrom-Json
if((Get-FileHash -LiteralPath $archive.archive).Hash -ne $archive.archiveSha256){throw 'Kit archive differs'}
if(-not $Workspace) {
    Add-Type -AssemblyName System.IO.Compression.FileSystem
    $destination=Join-Path $taskRoot ('.work/workshop/'+[Guid]::NewGuid().ToString('N'))
    [IO.Compression.ZipFile]::ExtractToDirectory($archive.archive,$destination)
    $Workspace=Join-Path $destination $archive.archiveRoot
    foreach($set in @('main','evaluation')){Copy-Item -LiteralPath (Join-Path $fixturePath ('src/'+$set)) -Destination (Join-Path $Workspace 'src') -Recurse -Force}
    $buildPath=Join-Path $Workspace 'build.gradle'
    $text=[IO.File]::ReadAllText($buildPath).Replace("version = '1.0.0'",("version = '"+$version+"'")).Replace("group = 'com.example'","group = 'dev.agentic.workshop'")
    [IO.File]::WriteAllText($buildPath,$text+"`n"+[IO.File]::ReadAllText((Join-Path $fixturePath 'build-overlay.gradle')))
    [IO.File]::WriteAllText((Join-Path $Workspace 'settings.gradle'),"rootProject.name = 'kit-workshop'`n")
}
$Workspace=[IO.Path]::GetFullPath($Workspace)
foreach($set in @('main','evaluation')) {
    $directory=Join-Path $fixturePath ('src/'+$set)
    foreach($file in (Get-ChildItem -LiteralPath $directory -Recurse -File)) {
        $relative=$file.FullName.Substring($directory.Length+1)
        if((Get-FileHash $file.FullName).Hash -ne (Get-FileHash (Join-Path $Workspace ('src/'+$set+'/'+$relative))).Hash){throw ('Fixture differs: '+$relative)}
    }
}
function Invoke-Phase([string]$Phase,[int]$ExpectedExit=0,[switch]$Force) {
    $result=$null
    $status=& powershell -NoProfile -File (Join-Path $Workspace 'agent.ps1') -Action status
    if($LASTEXITCODE -ne 0){throw 'Status failed'}
    $identity=($status -join "`n") | ConvertFrom-Json
    if($ReuseEvidence -and -not $Force) {
        $reports=Join-Path $Workspace 'reports/workshop-acceptance'
        if(Test-Path $reports) {
            $expectedStatus=if($ExpectedExit -eq 0){'PASS'}else{'FAIL'}
            $result=Get-ChildItem $reports -Filter '*.json' | ForEach-Object {Get-Content -Encoding UTF8 $_.FullName -Raw | ConvertFrom-Json} | Where-Object {$_.status -eq $expectedStatus -and $_.phase -eq $Phase -and $_.inputFingerprint -eq $identity.inputFingerprint -and $_.toolchainFingerprint -eq $identity.toolchainFingerprint} | Sort-Object runId -Descending | Select-Object -First 1
        }
    }
    if(-not $result) {
        $output=& powershell -NoProfile -File (Join-Path $PSScriptRoot 'Invoke-WorkshopServer.ps1') -Workspace $Workspace -Phase $Phase
        $nativeExit=$LASTEXITCODE;$result=($output -join "`n") | ConvertFrom-Json
        if($nativeExit -ne $ExpectedExit){throw "Unexpected runtime exit: $output"}
    }
    if($result.status -ne $(if($ExpectedExit -eq 0){'PASS'}else{'FAIL'})){throw 'Unexpected runtime status'}
    $cases.Add($result);return $result
}
try {
    $live=Invoke-Phase live;$restore=Invoke-Phase restore
    if($Mode -eq 'Regression') {
        if($Fault -eq 'Dimension') {
            $faultFile='PortalTravel.java';$target='persisted.setTag(RETURN,returnPoint);';$replacement='persisted.setTag(RETURN,new NBTTagCompound());'
            $cause='Dimension first return point'
        }elseif($Fault -eq 'Placement') {
            $faultFile='TrialWorldGen.java';$target='if(!canPlaceMarker(world,base))return;';$replacement='if(base.getY()<4 || base.getY()>220 || !world.getBlockState(base.down()).isFullBlock())return;'
            $cause='World content tree canopy marker rejection'
        }elseif($Fault -eq 'Crop') {
            $faultFile='TrialCrop.java';$target='return net.minecraftforge.common.EnumPlantType.Crop;';$replacement='return net.minecraftforge.common.EnumPlantType.Plains;'
            $cause='World content trampled immature crop/soil/drop'
        }elseif($Fault -eq 'WorldContent') {
            $faultFile='test_marker.json';$target='"count":2';$replacement='"count":0'
            $cause='World content chest loot accounting'
        }elseif($Fault -eq 'Projectile') {
            $faultFile='TestProjectile.java';$target='DamageSource.causeThrownDamage(this,getThrower()),4F';$replacement='DamageSource.causeThrownDamage(this,getThrower()),0F'
            $cause='Projectile entity hit damage/cleanup'
        }elseif($Fault -eq 'Quest') {
            $faultFile='PlayerQuest.java';$target='save(player,previous+submitted);';$replacement='save(player,complete?previous:previous+submitted);'
            $cause='Quest completion persisted'
        }elseif($Fault -eq 'NPC') {
            $faultFile='TraderNpc.java';$target='recipe.incrementToolUses();';$replacement='/* deliberate QA fault: no persisted trade use */'
            $cause='NPC trade use counter'
        }elseif($Fault -eq 'Pouch') {
            $faultFile='PouchInventory.java';$target='setTag("PouchContents",saved)';$replacement='setTag("PouchContents",new NBTTagCompound())'
            $cause='Pouch independent reopened contents'
        }else{
            $faultFile='ProcessorTile.java';$target='getStackInSlot(0).shrink(1)';$replacement='getStackInSlot(0).shrink(0)'
            $cause='Resume or conversion accounting'
        }
        $path=if($Fault -eq 'WorldContent'){Join-Path $Workspace 'src/main/resources/assets/kitworkshop/loot_tables/chests/test_marker.json'}else{Join-Path $Workspace ('src/main/java/dev/agentic/workshop/'+$faultFile)}
        $original=[IO.File]::ReadAllText($path)
        try {
            if(-not $original.Contains($target)){throw 'Fault target missing'}
            [IO.File]::WriteAllText($path,$original.Replace($target,$replacement))
            $faultResult=Invoke-Phase live 1
            if(-not [IO.File]::ReadAllText($faultResult.log).Contains('WORKSHOP_ASSERTIONS_FAIL: '+$cause)){throw 'Wrong fault cause'}
        }finally{[IO.File]::WriteAllText($path,$original)}
        $live=Invoke-Phase live -Force;$restore=Invoke-Phase restore -Force
    }
    $output=& powershell -NoProfile -File (Join-Path $Workspace 'agent.ps1') -Action check -Profile mod -TimeoutSeconds 180
    if($LASTEXITCODE -ne 0){throw "Mod validation failed: $output"}
    $mod=($output -join "`n") | ConvertFrom-Json
    if($mod.inputFingerprint -ne $live.inputFingerprint -or $mod.inputFingerprint -ne $restore.inputFingerprint){throw 'Evidence input mismatch'}
    Add-Type -AssemblyName System.IO.Compression.FileSystem
    $jar=[IO.Compression.ZipFile]::OpenRead($mod.artifact.path)
    try{if($jar.Entries.FullName -match 'workshopchecks|Test\.class|EnvironmentProbe|\.java$'){throw 'QA leaked into artifact'}}finally{$jar.Dispose()}
    $result=[ordered]@{status='PASS';mode=$Mode;fault=$Fault;workspace=$Workspace;kitArchiveSha256=$archive.archiveSha256;cases=$cases;mod=$mod;faultCoverage=if($Mode -eq 'Regression'){'PASS'}else{'NOT_RUN'};scope='135 actual ticks, prior block/pouch/NPC/quest/projectile checks, actual prepared-chunk population, crop/loot/advancement, player replacement/reset and vanilla disk restart; zero units, no real client/network or natural default-world proof'}
    $exitCode=0
}catch{$result=[ordered]@{status='FAIL';message=$_.Exception.Message;workspace=$Workspace;cases=$cases};$exitCode=1}
$evidence=Join-Path $taskRoot 'evidence/workshop';New-Item -ItemType Directory -Force -Path $evidence | Out-Null
$json=$result | ConvertTo-Json -Depth 10 -Compress
[IO.File]::WriteAllText((Join-Path $evidence ('acceptance-'+[DateTime]::UtcNow.ToString('yyyyMMddTHHmmssfff')+'.json')),$json)
$json;exit $exitCode
