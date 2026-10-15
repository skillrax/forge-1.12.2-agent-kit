# Compile-time discovery integrity: project changes must not require editing the core driver.
[CmdletBinding()]
param([Parameter(Mandatory)][string]$Workspace,[Parameter(Mandatory)][string]$EvidenceDirectory)
$ErrorActionPreference='Stop'
. (Join-Path $Workspace 'testing/InstalledRuntime.ps1')
if(Test-Path -LiteralPath $EvidenceDirectory){throw 'Evidence already exists.'}
New-Item -ItemType Directory -Path $EvidenceDirectory | Out-Null
$core=@(Get-ChildItem -LiteralPath (Join-Path $Workspace 'testing/installed-driver/src') -File -Recurse | ForEach-Object {@{path=$_.FullName;sha256=(Get-FileHash -LiteralPath $_.FullName).Hash}})
$project=Join-Path $Workspace 'src/installedTest/java/example/installedtests'
$entity=Join-Path $project 'EntityGrowth.java';$duplicate=Join-Path $project 'DuplicateScenario.java'
$cases=New-Object 'System.Collections.Generic.List[object]'
function Build([string]$Name,[int]$ExpectedExit) {
    $log=Join-Path $EvidenceDirectory ($Name+'.log')
    $process=Invoke-InstalledProcess -Java (Join-Path $env:SystemRoot 'System32/WindowsPowerShell/v1.0/powershell.exe') -Arguments @('-NoProfile','-File',(Join-Path $Workspace 'tools/Build-InstalledTestDriver.ps1')) -Directory $Workspace -Log $log -TimeoutSeconds 120
    $lines=@(Get-Content -LiteralPath $log -Encoding UTF8 | Where-Object {$_.StartsWith('{')})
    if($lines.Count -ne 1 -or $process.exitCode -ne $ExpectedExit){throw "Unexpected discovery build: $Name"}
    $result=$lines[0] | ConvertFrom-Json
    if(($ExpectedExit -eq 0) -ne ($result.status -eq 'PASS')){throw 'Build status/exit mismatch.'}
    $cases.Add(@{name=$Name;status='PASS';log=$log;build=$result})
    return $result
}
try {
    if(-not [IO.File]::Exists($entity) -or (Test-Path -LiteralPath $duplicate)){throw 'Expected clean fixture source paths.'}
    Copy-Item -LiteralPath $entity -Destination (Join-Path $EvidenceDirectory 'EntityGrowth.java')
    $source='package example.installedtests; import dev.agentic.installedtest.*; @InstalledTest("entity-growth") public final class DuplicateScenario implements InstalledScenario { public void plan(TestContext t) {} }'
    [IO.File]::WriteAllText($duplicate,$source,(New-Object Text.UTF8Encoding($false)))
    $failed=Build 'duplicate-id' 1
    if(($failed.diagnostics -join ' ') -notmatch 'Duplicate installed scenario ID: entity-growth'){throw 'Duplicate ID diagnostic missing.'}
    Copy-Item -LiteralPath $duplicate -Destination (Join-Path $EvidenceDirectory 'DuplicateScenario.java')
    Remove-Item -LiteralPath $duplicate
    Remove-Item -LiteralPath $entity
    $removed=Build 'removed-project-scenario' 0
    $catalog=Read-InstalledScenarioCatalog $removed.path
    if(@($catalog.scenarios | Where-Object {$_.id -eq 'entity-growth'}).Count){throw 'Removed scenario remains discoverable.'}
    $zip=[IO.Compression.ZipFile]::OpenRead($removed.path)
    try{if($zip.GetEntry('example/installedtests/EntityGrowth.class')){throw 'Removed scenario class remains packaged.'}}finally{$zip.Dispose()}
    Copy-Item -LiteralPath (Join-Path $EvidenceDirectory 'EntityGrowth.java') -Destination $entity
    $restored=Build 'restored-project-scenario' 0
    if($restored.scenarios -notcontains 'entity-growth'){throw 'Restored scenario not indexed.'}
    foreach($entry in $core){$null=Get-InstalledFile $entry.path $entry.sha256}
    Write-InstalledJson (Join-Path $EvidenceDirectory 'summary.json') @{status='PASS';cases=@($cases.ToArray());sharedCoreUnchanged=$true;finalDriver=$restored}
    @{status='PASS';caseCount=$cases.Count;summary=(Join-Path $EvidenceDirectory 'summary.json')} | ConvertTo-Json -Compress;exit 0
}catch{
    if([IO.File]::Exists((Join-Path $EvidenceDirectory 'EntityGrowth.java'))){Copy-Item -LiteralPath (Join-Path $EvidenceDirectory 'EntityGrowth.java') -Destination $entity -Force}
    if([IO.File]::Exists($duplicate)){Remove-Item -LiteralPath $duplicate}
    Write-InstalledJson (Join-Path $EvidenceDirectory 'summary.json') @{status='FAIL';message=$_.Exception.Message;cases=@($cases.ToArray())}
    @{status='FAIL';message=$_.Exception.Message;evidence=$EvidenceDirectory} | ConvertTo-Json -Compress;exit 1
}
