[CmdletBinding()]
param()
$ErrorActionPreference = 'Stop'
[Console]::OutputEncoding = New-Object Text.UTF8Encoding($false)
$OutputEncoding = [Console]::OutputEncoding
$authoringRoot = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$root = Join-Path $authoringRoot 'kit'
$fixture = Join-Path $authoringRoot ('.work/agent-fixtures/' + [Guid]::NewGuid().ToString('N'))
$shell = (Get-Process -Id $PID).Path
$passed = New-Object 'System.Collections.Generic.List[string]'
$lock = $null
function Invoke-Agent {
    param([string[]]$Arguments, [int]$ExpectedExit = 0)
    $output = & $shell -NoProfile -File (Join-Path $fixture 'agent.ps1') @Arguments
    if ($LASTEXITCODE -ne $ExpectedExit) { throw "Unexpected exit: $LASTEXITCODE; $output" }
    ($output -join "`n") | ConvertFrom-Json
}
try {
    New-Item -ItemType Directory -Force -Path $fixture | Out-Null
    foreach ($directory in @('src', 'testing', 'gradle', 'tools')) {
        New-Item -ItemType Directory -Force -Path (Join-Path $fixture $directory) | Out-Null
    }
    Copy-Item -LiteralPath (Join-Path $root 'tools/Record-Evidence.ps1') -Destination (Join-Path $fixture 'tools')
    Copy-Item -LiteralPath (Join-Path $root 'agent.ps1') -Destination $fixture
    Copy-Item -LiteralPath (Join-Path $root 'TASK.md') -Destination $fixture
    Copy-Item -LiteralPath (Join-Path $root 'testing/Diagnostics.ps1') -Destination (Join-Path $fixture 'testing')
    Copy-Item -LiteralPath (Join-Path $root 'testing/Test-DevEnvironment.ps1') -Destination (Join-Path $fixture 'testing')
    Set-Content -LiteralPath (Join-Path $fixture 'gradlew.bat') -Value "@echo off`r`nexit /b 0" -Encoding ASCII
    $initial = Invoke-Agent -Arguments @('-Action', 'status')
    if ($null -ne $initial.lastRun -or $initial.lastRunMatchesCurrentInputs) { throw 'Fresh project reported validation evidence.' }
    if($initial.nextAction -notmatch 'environment'){throw 'Missing environment not recommended'}
    $passed.Add('fresh status and environment recommendation')
    $unicodeTask = 'UTF-8 task ' + [char]0x2603 + ' ' + [char]0x00E9
    [IO.File]::WriteAllText((Join-Path $fixture 'TASK.md'), $unicodeTask, (New-Object Text.UTF8Encoding($false)))
    $utf8Status = Invoke-Agent -Arguments @('-Action','status')
    if ($utf8Status.task -ne $unicodeTask) { throw 'UTF-8 task failed JSON roundtrip.' }
    $passed.Add('UTF-8 task/output roundtrip')

    $check = Invoke-Agent -Arguments @('-Action', 'check')
    if ($check.status -ne 'PASS' -or -not $check.runId -or -not $check.inputFingerprint) { throw 'Check contract missing.' }
    $status = Invoke-Agent -Arguments @('-Action', 'status')
    if (-not $status.lastRunMatchesCurrentInputs) { throw 'Unchanged inputs reported stale.' }
    $passed.Add('check and matching fingerprint')
    if ($status.projectRoot -ne $fixture -or $check.projectRoot -ne $fixture) { throw 'Canonical project root incorrect.' }
    $savedJavaHome = $env:JAVA_HOME
    try {
        $env:JAVA_HOME = Join-Path $fixture 'missing-jdk'
        $staleToolchain = Invoke-Agent -Arguments @('-Action', 'status')
        if ($staleToolchain.lastRunMatchesCurrentInputs) { throw 'Toolchain change did not invalidate evidence.' }
    } finally { $env:JAVA_HOME = $savedJavaHome }
    $passed.Add('root identity and toolchain freshness')
    $latestPath = Join-Path $fixture 'reports/testing-tools/latest.json'
    $savedReport = [IO.File]::ReadAllText($latestPath)
    try {
        $foreign = $savedReport | ConvertFrom-Json
        $foreign.projectRoot = 'C:/foreign-copy'
        $foreign | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath $latestPath -Encoding UTF8
        $foreignStatus = Invoke-Agent -Arguments @('-Action', 'status')
        if ($foreignStatus.lastRunMatchesCurrentInputs) { throw 'Foreign checkout evidence was accepted.' }
    } finally { [IO.File]::WriteAllText($latestPath, $savedReport) }
    $passed.Add('copied evidence rejected')
    $evidenceFile = Join-Path $fixture 'reports/assertion-fixture.log'
    [IO.File]::WriteAllText($evidenceFile, 'FIXTURE_ASSERTIONS_PASS')
    $record = & $shell -NoProfile -File (Join-Path $fixture 'tools/Record-Evidence.ps1') -Capability behavior -Outcome PASS -Method log-marker -Observation 'Synthetic recorder fixture only' -EvidenceFiles $evidenceFile -Marker 'FIXTURE_ASSERTIONS_PASS'
    if ($LASTEXITCODE -ne 0) { throw "Recorder failed: $record" }
    $recorded = Invoke-Agent -Arguments @('-Action','status')
    if ($recorded.capabilities.behavior.state -ne 'RECORDED_PASS') { throw 'Recorded assertion missing from summary.' }
    [IO.File]::WriteAllText($evidenceFile, 'tampered')
    $tampered = Invoke-Agent -Arguments @('-Action','status')
    if ($tampered.capabilities.behavior.state -ne 'STALE') { throw 'Tampered observation remained current.' }
    $reject = & $shell -NoProfile -File (Join-Path $fixture 'tools/Record-Evidence.ps1') -Capability behavior -Outcome PASS -Method log-marker -Observation 'Negative fixture' -EvidenceFiles $evidenceFile -Marker 'FIXTURE_ASSERTIONS_PASS'
    if ($LASTEXITCODE -ne 1 -or $reject -notmatch 'marker absent') { throw 'Missing assertion marker accepted.' }
    $passed.Add('recorded observations, tampering and missing marker')


    Set-Content -LiteralPath (Join-Path $fixture 'src/change.txt') -Value 'changed'
    $status = Invoke-Agent -Arguments @('-Action', 'status')
    if ($status.lastRunMatchesCurrentInputs) { throw 'Changed source did not invalidate the result.' }
    $passed.Add('stale result detection')
    $errors = Invoke-Agent -Arguments @('-Action', 'errors', '-RunId', $check.runId)
    if ($errors.runId -ne $check.runId -or $errors.matchesCurrentInputs) { throw 'Historical lookup incorrect.' }
    if($errors.inputFingerprint -ne $check.inputFingerprint -or $errors.toolchainFingerprint -ne $check.toolchainFingerprint -or $errors.scope -ne $check.scope -or $errors.report -ne $check.report){throw 'Diagnostic provenance differs from selected run'}
    $passed.Add('historical lookup')
    $unknown = Invoke-Agent -Arguments @('-Action', 'errors', '-RunId', 'missing') -ExpectedExit 1
    if ($unknown.status -ne 'FAIL') { throw 'Unknown run did not fail.' }
    $passed.Add('unknown run rejected')
    $failureLines = @('@echo off') + @(1..12 | ForEach-Object { 'echo ^> fixture error ' + $_ + ' ' + ('x' * 400) }) + @('exit /b 1')
    Set-Content -LiteralPath (Join-Path $fixture 'gradlew.bat') -Value $failureLines -Encoding ASCII
    $failed = Invoke-Agent -Arguments @('-Action', 'check') -ExpectedExit 1
    $diagnostics = Invoke-Agent -Arguments @('-Action', 'errors')
    if ($failed.status -ne 'FAIL' -or $diagnostics.validationStatus -ne 'FAIL' -or $diagnostics.diagnostics.Count -ne 10) {
        throw 'Failure or diagnostic count was incorrect.'
    }
    foreach ($line in $diagnostics.diagnostics) { if ($line.Length -gt 300) { throw 'Diagnostic line exceeded the limit.' } }
    if($failed.diagnostics.Count -ne 10 -or ($failed.diagnostics -join '|') -ne ($diagnostics.diagnostics -join '|')){throw 'Failed check did not include matching bounded diagnostics'}
    $failedStatus=Invoke-Agent -Arguments @('-Action','status')
    if($failedStatus.nextAction -notmatch 'Repair the latest'){throw 'Current failure recommendation missing'}
    $passed.Add('failure diagnostics bounded')
    Set-Content -LiteralPath (Join-Path $fixture 'testing/Test-DevEnvironment.ps1') -Value 'exit 1'
    $runnerFailed = Invoke-Agent -Arguments @('-Action', 'check') -ExpectedExit 1
    $afterFailure = Invoke-Agent -Arguments @('-Action', 'status')
    if ($runnerFailed.message -notmatch 'no valid JSON report' -or $afterFailure.lastRun.status -ne 'FAIL' -or $afterFailure.lastRun.runId -ne $runnerFailed.runId -or $afterFailure.capabilities.all.state -ne 'FAIL') {
        throw 'Runner failure was not recorded as the latest failed invocation.'
    }
    $passed.Add('runner failure supersedes previous results')
    $lock = [IO.File]::Open((Join-Path $fixture 'reports/testing-tools/agent.lock'), 'OpenOrCreate', 'ReadWrite', 'None')
    $busy = Invoke-Agent -Arguments @('-Action', 'check') -ExpectedExit 1
    if ($busy.message -notmatch 'Another agent check') { throw 'Concurrent check was not rejected.' }
    $passed.Add('concurrency rejection')
    [ordered]@{ schemaVersion = 1; status = 'PASS'; cases = $passed; fixtures = $fixture } | ConvertTo-Json -Compress
    exit 0
} catch {
    [ordered]@{ schemaVersion = 1; status = 'FAIL'; message = $_.Exception.Message; cases = $passed; fixtures = $fixture } | ConvertTo-Json -Compress
    exit 1
} finally { if ($null -ne $lock) { $lock.Dispose() } }
