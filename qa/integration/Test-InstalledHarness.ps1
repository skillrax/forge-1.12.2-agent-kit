# Focused checks of project discovery and the generic tick/assertion lifecycle.
[CmdletBinding()]
param([Parameter(Mandatory)][string]$Workspace,[Parameter(Mandatory)][string]$RuntimeLock,[Parameter(Mandatory)][string]$SubjectExport,[Parameter(Mandatory)][string]$DriverManifest,[Parameter(Mandatory)][string]$EvidenceDirectory)
$ErrorActionPreference='Stop'
. (Join-Path $Workspace 'testing/InstalledRuntime.ps1')
$EvidenceDirectory=[IO.Path]::GetFullPath($EvidenceDirectory)
if(Test-Path -LiteralPath $EvidenceDirectory){throw 'Evidence already exists.'}
New-Item -ItemType Directory -Path $EvidenceDirectory | Out-Null
$driver=Read-InstalledJson $DriverManifest
$cases=New-Object 'System.Collections.Generic.List[object]'
function Run-Scenario([string]$Id,[string]$Expected,[string[]]$Assertions,[string]$Message,[string]$Phase,[switch]$NoLaunch) {
    $spec=@{schemaVersion=1;mode='server';scenario=$Id;maxTicks=50;timeoutSeconds=60;runtimeLock=$RuntimeLock;subject=@{modId='machinestarter';exportManifest=$SubjectExport};driver=@{modId='installedtestdriver';path=$driver.path;sha256=$driver.sha256};dependencies=@();parameters=@{};expectedAssertions=$Assertions}
    $manifest=Join-Path $EvidenceDirectory ($Id+'.json');Write-InstalledJson $manifest $spec
    $log=Join-Path $EvidenceDirectory ($Id+'.log')
    $process=Invoke-InstalledProcess -Java (Join-Path $env:SystemRoot 'System32/WindowsPowerShell/v1.0/powershell.exe') -Arguments @('-NoProfile','-File',(Join-Path $Workspace 'tools/Test-InstalledMod.ps1'),'-Manifest',$manifest,'-AcceptEula') -Directory $Workspace -Log $log -TimeoutSeconds 90
    $lines=@(Get-Content -LiteralPath $log -Encoding UTF8 | Where-Object {$_.StartsWith('{')})
    if($lines.Count -ne 1){throw "Malformed result: $Id"}
    $response=$lines[0] | ConvertFrom-Json
    $expectedExit=if($Expected -eq 'PASS'){0}else{1}
    if($process.exitCode -ne $expectedExit -or $response.status -ne $Expected -or $response.message -notmatch $Message){throw "Unexpected $Id result; inspect $log"}
    $report=Read-InstalledJson $response.report
    if($Phase -and $report.phase -ne $Phase){throw "Wrong failure phase: $Id"}
    if($NoLaunch -and (Test-Path -LiteralPath (Join-Path (Split-Path -Parent $response.report) 'game'))){throw 'Unknown scenario reached game staging.'}
    if($Expected -eq 'FAIL' -and -not $NoLaunch -and $report.failureKind -ne 'behavior'){throw 'Scenario failure lost its behavior scope.'}
    $cases.Add(@{scenario=$Id;status='PASS';observed=$report.status;phase=$report.phase;serverTicks=$report.serverTicks;report=$response.report})
}
try {
    Run-Scenario 'entity-growth' 'PASS' @('scenario.grown','scenario.alive','scenario.world-tracked') 'required world assertions passed' 'complete'
    Run-Scenario 'condition-timeout' 'FAIL' @('scenario.unreachable') 'timed out after 3 ticks' 'unreachable'
    Run-Scenario 'invariant-failure' 'FAIL' @('scenario.stable') 'Invariant failed at tick 3' 'stable'
    Run-Scenario 'empty-scenario' 'FAIL' @('scenario.never') 'without any behavior assertions' 'complete'
    Run-Scenario 'unknown-scenario' 'FAIL' @('scenario.never') 'Unknown scenario.*available:' '' -NoLaunch
    $summary=@{status='PASS';driver=@{path=$driver.path;sha256=$driver.sha256;scenarios=$driver.scenarios};cases=@($cases.ToArray());scope='Project-owned discovery, natural vanilla entity age ticks, sequential delay/condition, per-tick invariant, empty-scenario rejection and preflight discovery. Vanilla entity case is a harness check, not a custom entity proof.'}
    Write-InstalledJson (Join-Path $EvidenceDirectory 'summary.json') $summary
    @{status='PASS';caseCount=$cases.Count;summary=(Join-Path $EvidenceDirectory 'summary.json')} | ConvertTo-Json -Compress;exit 0
}catch{
    Write-InstalledJson (Join-Path $EvidenceDirectory 'summary.json') @{status='FAIL';message=$_.Exception.Message;cases=@($cases.ToArray())}
    @{status='FAIL';message=$_.Exception.Message;evidence=$EvidenceDirectory} | ConvertTo-Json -Compress;exit 1
}
