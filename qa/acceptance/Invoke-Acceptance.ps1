# Runs the hidden acceptance tests of one prompt against a finished trial folder, then removes them again.
# Sets the agent's own src/gametest/java aside (so its tests, compiling or not, do not decide the score), copies
# qa/acceptance/common/*.java and qa/acceptance/<Series>/<Id>/*.java into <Trial>/src/gametest/java/acceptance,
# runs `agent.ps1 check game -Test <their ids>`, then deletes the copies and their compiled classes, puts the agent's
# tests back and restores the trial's own check history, so the trial looks as the agent left it.
# Returns (and with -Out writes as JSON):
#   @{ id; passed; total; tests = @(@{ id; status; phase; message }); verdict; note; output (the check's first 60 lines) }
# Usage:
#   powershell -NoProfile -File qa/acceptance/Invoke-Acceptance.ps1 -Trial ..\kit-trials\series3-claude-...\S1-r1 -Id S1
#   ... -Rename @{ 'glowberries:glowberry"' = 'glowberry:glowberries"' }   (string replacements in the copied tests:
#       for trials run before the prompts named their ids; see qa/local-model/results/series3-acceptance.md)
[CmdletBinding()]
param(
    [Parameter(Mandatory)][string]$Trial,
    [Parameter(Mandatory)][string]$Id,
    [string]$Series = 'series3',
    [hashtable]$Rename,
    [string]$Out
)
$ErrorActionPreference = 'Stop'
$Trial = [IO.Path]::GetFullPath($Trial)
$sources = @(Get-ChildItem -LiteralPath (Join-Path $PSScriptRoot 'common') -Filter '*.java') +
    @(Get-ChildItem -LiteralPath (Join-Path $PSScriptRoot "$Series/$Id") -Filter '*.java' -ErrorAction SilentlyContinue)
$result = [ordered]@{ id = $Id; passed = 0; total = 0; tests = @(); verdict = ''; note = ''; output = @() }
if ($sources.Count -lt 2) { $result.note = "no acceptance tests for $Series/$Id"; return [pscustomobject]$result }
if (-not [IO.File]::Exists((Join-Path $Trial 'agent.ps1'))) { throw "Not a trial folder (no agent.ps1): $Trial" }

$testDir = Join-Path $Trial 'src/gametest/java'
$aside = Join-Path $Trial 'src/gametest/java-agent-tests'
$dest = Join-Path $testDir 'acceptance'
if ([IO.Directory]::Exists($aside)) { throw "src/gametest/java-agent-tests already exists (an interrupted run?): move it back to src/gametest/java first." }
$utf8 = New-Object Text.UTF8Encoding($false)
$ids = New-Object System.Collections.Generic.List[string]
# The check rewrites these; keep the agent's own history as it was.
$keep = @('reports/agent-log.jsonl', 'reports/testing-tools/latest.json', 'reports/testing-tools/game-latest.json',
    'reports/testing-tools/streak.json', 'reports/testing-tools/test-history.json')
$saved = @{}
foreach ($rel in $keep) { $p = Join-Path $Trial $rel; if ([IO.File]::Exists($p)) { $saved[$rel] = [IO.File]::ReadAllBytes($p) } }
$hadTests = [IO.Directory]::Exists($testDir); $moved = $false
try {
    if ($hadTests) { [IO.Directory]::Move($testDir, $aside); $moved = $true }
    New-Item -ItemType Directory -Force -Path $dest | Out-Null
    foreach ($file in $sources) {
        $text = [IO.File]::ReadAllText($file.FullName)
        if ($Rename) { foreach ($k in $Rename.Keys) { $text = $text.Replace([string]$k, [string]$Rename[$k]) } }
        [IO.File]::WriteAllText((Join-Path $dest $file.Name), $text, $utf8)
        foreach ($m in [regex]::Matches($text, '@GameTest\(\s*(?:value\s*=\s*)?"([^"]+)"')) { $ids.Add($m.Groups[1].Value) }
    }
    $result.total = $ids.Count
    $ErrorActionPreference = 'Continue'
    $output = & powershell -NoProfile -ExecutionPolicy Bypass -File (Join-Path $Trial 'agent.ps1') check game -Test ($ids -join ',') 2>$null
    $ErrorActionPreference = 'Stop'
    $result.verdict = (@($output) | Select-Object -First 1)
    $result.output = @(@($output) | Select-Object -First 60)
    $report = Join-Path $Trial 'reports/testing-tools/game-latest.json'
    $ran = $null
    if ([IO.File]::Exists($report)) { $ran = [IO.File]::ReadAllText($report, [Text.Encoding]::UTF8).TrimStart([char]0xFEFF) | ConvertFrom-Json }
    $byId = @{}
    if ($ran -and $ran.harness) { foreach ($t in @($ran.harness.tests)) { if ($t -and $ids.Contains([string]$t.id)) { $byId[[string]$t.id] = $t } } }
    $tests = foreach ($tid in $ids) {
        $t = $byId[$tid]
        if ($t) { [ordered]@{ id = $tid; status = [string]$t.status; phase = [string]$t.phase; message = [string]$t.message } }
        else { [ordered]@{ id = $tid; status = 'NOT RUN'; phase = ''; message = '' } }
    }
    $result.tests = @($tests)
    $result.passed = @($result.tests | Where-Object { $_.status -eq 'PASS' }).Count
    if (-not $byId.Count) { $result.note = 'did not run: ' + $result.verdict }
}
finally {
    $ErrorActionPreference = 'Continue'
    # Only ever delete the folder this script made: the agent's own tests are in $aside unless the move failed.
    if ($moved -or -not $hadTests) { if ([IO.Directory]::Exists($testDir)) { Remove-Item -LiteralPath $testDir -Recurse -Force } }
    elseif ([IO.Directory]::Exists($dest)) { Remove-Item -LiteralPath $dest -Recurse -Force }
    if ($moved) { [IO.Directory]::Move($aside, $testDir) }
    foreach ($classes in @('build/classes/java/gametest/acceptance', 'build/classes/gametest/acceptance')) {
        $p = Join-Path $Trial $classes
        if ([IO.Directory]::Exists($p)) { Remove-Item -LiteralPath $p -Recurse -Force }
    }
    foreach ($rel in $keep) {
        $p = Join-Path $Trial $rel
        if ($saved.ContainsKey($rel)) { [IO.File]::WriteAllBytes($p, $saved[$rel]) } elseif ([IO.File]::Exists($p)) { Remove-Item -LiteralPath $p -Force }
    }
}
$object = [pscustomobject]$result
if ($Out) { [IO.File]::WriteAllText($Out, ($object | ConvertTo-Json -Depth 6), $utf8) }
return $object
