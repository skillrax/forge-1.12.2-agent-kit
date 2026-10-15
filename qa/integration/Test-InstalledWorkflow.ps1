# Focused result-integrity checks for the optional installed-server runner.
[CmdletBinding()]
param(
    [Parameter(Mandatory)][string]$Workspace,
    [Parameter(Mandatory)][string]$RuntimeLock,
    [Parameter(Mandatory)][string]$SubjectExport,
    [Parameter(Mandatory)][string]$DriverManifest,
    [Parameter(Mandatory)][string]$IronChestJar,
    [Parameter(Mandatory)][string]$IronChestSha256,
    [string]$EvidenceDirectory,
    [switch]$SuccessOnly
)
$ErrorActionPreference='Stop'
$root=Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
. (Join-Path $Workspace 'testing/InstalledRuntime.ps1')
if(-not $EvidenceDirectory){$EvidenceDirectory=Join-Path $root ('evidence/installed-tests/'+[DateTime]::UtcNow.ToString('yyyyMMddTHHmmssfffZ'))}
$EvidenceDirectory=[IO.Path]::GetFullPath($EvidenceDirectory)
if(Test-Path -LiteralPath $EvidenceDirectory){throw 'Evidence directory already exists.'}
New-Item -ItemType Directory -Path $EvidenceDirectory | Out-Null
$cases=New-Object 'System.Collections.Generic.List[object]'
$driver=Read-InstalledJson $DriverManifest
$baseline=@{
    schemaVersion=1;mode='server';runtimeLock=[IO.Path]::GetFullPath($RuntimeLock);scenario='hopper-transfer';timeoutSeconds=90
    subject=@{modId='machinestarter';exportManifest=[IO.Path]::GetFullPath($SubjectExport)}
    driver=@{modId='installedtestdriver';path=$driver.path;sha256=$driver.sha256}
    dependencies=@(@{modId='ironchest';path=[IO.Path]::GetFullPath($IronChestJar);sha256=$IronChestSha256})
    expectedAssertions=@('scenario.natural-tick-transfer','scenario.item-conservation')
    parameters=@{sourceBlock='ironchest:iron_chest';targetBlock='machinestarter:machine';item='minecraft:cobblestone';count=8;waitTicks=100}
}
function New-Spec {($baseline | ConvertTo-Json -Depth 12) | ConvertFrom-Json}
function Run-Case([string]$Name,$Spec,[string]$ExpectedStatus,[string]$MessagePattern,[switch]$NoLaunch) {
    $manifest=Join-Path $EvidenceDirectory ($Name+'.json');Write-InstalledJson $manifest $Spec
    $log=Join-Path $EvidenceDirectory ($Name+'.log')
    $shell=Join-Path $env:SystemRoot 'System32/WindowsPowerShell/v1.0/powershell.exe'
    $process=Invoke-InstalledProcess -Java $shell -Arguments @('-NoProfile','-File',(Join-Path $Workspace 'tools/Test-InstalledMod.ps1'),'-Manifest',$manifest,'-AcceptEula') -Directory $Workspace -Log $log -TimeoutSeconds 130
    $lines=@(Get-Content -LiteralPath $log -Encoding UTF8 | Where-Object {$_.StartsWith('{')})
    if($lines.Count -ne 1){throw "Expected one bounded JSON result: $Name"}
    $response=$lines[0] | ConvertFrom-Json
    $expectedExit=if($ExpectedStatus -eq 'PASS'){0}else{1}
    if($process.exitCode -ne $expectedExit -or $response.status -ne $ExpectedStatus -or $response.message -notmatch $MessagePattern){throw "Unexpected $Name result; inspect $log"}
    $report=Read-InstalledJson $response.report
    if($NoLaunch -and (Test-Path -LiteralPath (Join-Path (Split-Path -Parent $response.report) 'game'))){throw "Preflight case started staging: $Name"}
    $cases.Add(@{name=$Name;status='PASS';observedStatus=$response.status;failureKind=$response.failureKind;report=$response.report;log=$log;runId=$response.runId})
    return $report
}
try {
    $export=Read-InstalledJson $SubjectExport
    $subject=Get-InstalledFile $export.artifact.path $export.artifact.sha256
    Add-Type -AssemblyName System.IO.Compression.FileSystem
    $zip=[IO.Compression.ZipFile]::OpenRead($subject.path)
    try {
        if(@($zip.Entries | Where-Object {$_.FullName -match 'installedtest|cpw/mods/ironchest|EnvironmentProbe|\.java$'}).Count){throw 'QA/third-party/source content in production artifact.'}
    }finally{$zip.Dispose()}
    $pass=Run-Case 'ironchest-transfer' (New-Spec) 'PASS' 'required world assertions passed'
    if($pass.loadedMods.Count -ne 3 -or @($pass.assertions | Where-Object {$_.name -eq 'scenario.natural-tick-transfer' -and $_.passed}).Count -ne 1){throw 'Missing actual integration evidence.'}
    if(-not $SuccessOnly){
        $spec=New-Spec;$spec.dependencies[0].sha256=('0'*64)
        $null=Run-Case 'changed-jar' $spec 'FAIL' 'SHA256 mismatch' -NoLaunch
        $spec=New-Spec;$spec.dependencies[0].path=Join-Path $EvidenceDirectory 'absent.jar'
        $null=Run-Case 'missing-jar' $spec 'FAIL' 'Required file missing' -NoLaunch
        $spec=New-Spec;$spec.parameters.targetBlock='minecraft:stone'
        $failure=Run-Case 'driver-failure' $spec 'FAIL' 'Driver failed:'
        if($failure.failureKind -ne 'behavior'){throw 'Driver failure incorrectly classified.'}
        $spec=New-Spec;$spec.expectedAssertions+=@('never-emitted')
        $null=Run-Case 'missing-assertion' $spec 'FAIL' 'Required assertion missing'
        # An ordinary mod has no scenario catalog; reject it without launching Minecraft.
        $spec=New-Spec;$spec.driver.path=$IronChestJar;$spec.driver.sha256=$IronChestSha256;$spec.dependencies=@();$spec.timeoutSeconds=15
        $null=Run-Case 'no-driver-catalog' $spec 'FAIL' 'Driver catalog missing' -NoLaunch
    }
    $null=Get-InstalledFile $subject.path $subject.sha256
    $summary=@{status='PASS';cases=@($cases.ToArray());productionArtifact=$subject;scope='Installed server: exact production starter plus Iron Chests release, normal hopper transfer/accounting; result integrity where selected. No client, visual, restart or broad compatibility claim.'}
    Write-InstalledJson (Join-Path $EvidenceDirectory 'summary.json') $summary
    @{status='PASS';caseCount=$cases.Count;summary=(Join-Path $EvidenceDirectory 'summary.json')} | ConvertTo-Json -Compress
    exit 0
}catch{
    Write-InstalledJson (Join-Path $EvidenceDirectory 'summary.json') @{status='FAIL';message=$_.Exception.Message;cases=@($cases.ToArray())}
    @{status='FAIL';message=$_.Exception.Message;evidence=$EvidenceDirectory} | ConvertTo-Json -Compress;exit 1
}
