# Summary of an unattended trial run, read from the trial folder afterwards (nobody has to watch it):
# kit usage log (reports/agent-log.jsonl, kit 0.5.0-beta.19+), every check report, kit files changed
# against the release manifest, the mod's files and TASK.md, then the model side from OpenCode's session store (Read-OpenCode.py).
# Usage: powershell -NoProfile -File qa/local-model/Read-Trial.ps1 -Path <trial folder>
[CmdletBinding()]
param([Parameter(Mandatory)][string]$Path)
$ErrorActionPreference = 'Stop'
$repo = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$trial = [IO.Path]::GetFullPath($Path)
if (-not [IO.File]::Exists((Join-Path $trial 'agent.ps1'))) { throw "$trial is not a kit folder (no agent.ps1)" }
function Read-Json([string]$File) { [IO.File]::ReadAllText($File) | ConvertFrom-Json }
function Get-RunTime([string]$RunId) { [DateTime]::ParseExact($RunId.Substring(0, 18), 'yyyyMMddTHHmmssfff', [Globalization.CultureInfo]::InvariantCulture) }

$version = (Read-Json (Join-Path $trial 'kit.json')).version
"trial: $trial (kit $version)"

# Usage log: every agent.ps1 call.
$logFile = Join-Path $trial 'reports/agent-log.jsonl'
$calls = @(if ([IO.File]::Exists($logFile)) { [IO.File]::ReadAllLines($logFile) | Where-Object { $_ } | ForEach-Object { $_ | ConvertFrom-Json } })
if ($calls.Count) {
    $first = [DateTime]::Parse($calls[0].time).ToLocalTime(); $last = [DateTime]::Parse($calls[-1].time).ToLocalTime()
    $busy = ($calls | Measure-Object seconds -Sum).Sum
    "calls: $($calls.Count) from $($first.ToString('HH:mm')) to $($last.ToString('HH:mm')) ($([int]($last - $first).TotalMinutes) min; $([int]($busy / 60)) min inside agent.ps1)"
    '  by command: ' + ((@($calls | Group-Object action | Sort-Object Count -Descending | ForEach-Object { "$($_.Name) $($_.Count)" })) -join ', ')
    $api = @($calls | Where-Object action -eq 'api' | ForEach-Object { (@($_.target, $_.args) | Where-Object { $_ }) -join ' ' })
    if ($api.Count) { "  api lookups ($($api.Count)): " + ((@($api | Group-Object | Sort-Object Count -Descending | Select-Object -First 15 | ForEach-Object { if ($_.Count -gt 1) { "$($_.Name) x$($_.Count)" } else { $_.Name } })) -join '; ') }
    foreach ($c in @($calls | Where-Object { $_.action -in @('init', 'new', 'export', 'version') })) { "  $($c.action) $($c.target) $($c.args)".TrimEnd() + $(if ($c.exit) { '  -> FAIL' } else { '' }) }
} else { 'calls: no usage log (kit before 0.5.0-beta.19); from check reports only' }

# Check reports: one per run, with problems and failed tests.
$reports = @(Get-ChildItem -Path (Join-Path $trial 'reports/testing-tools') -Filter '*.json' -ErrorAction SilentlyContinue | Where-Object { $_.Name -notlike '*latest*' -and $_.Name -match '^\d{8}T' } | Sort-Object Name | ForEach-Object { Read-Json $_.FullName })
if ($reports.Count) {
    $letters = @{ environment = 'e'; build = 'b'; unit = 'u'; server = 's'; game = 'g'; client = 'c' }
    $sequence = ($reports | ForEach-Object { $l = $letters[[string]$_.profile]; if (-not $l) { $l = '?' }; if ($_.status -eq 'PASS') { $l.ToUpperInvariant() } else { $l } }) -join ''
    $t0 = Get-RunTime $reports[0].runId; $t1 = Get-RunTime $reports[-1].runId
    "checks: $($reports.Count) between $($t0.ToLocalTime().ToString('HH:mm')) and $($t1.ToLocalTime().ToString('HH:mm')) ($([int]($t1 - $t0).TotalMinutes) min)"
    "  sequence (upper = PASS; e environment, b build, s server, g game, c client): $sequence"
    $stuck = @($reports | Where-Object { $_.repeatedFailures -ge 3 -or $_.testFailures -ge 5 })
    if ($stuck.Count) { "  STUCK printed $($stuck.Count)x, first after check $([array]::IndexOf($reports, $stuck[0]) + 1)" } else { '  STUCK: never' }
    $flaky = @($reports | Where-Object { @($_.flakyTests | Where-Object { $_ }).Count })
    if ($flaky.Count) { "  FLAKY printed $($flaky.Count)x: " + ((@($flaky | ForEach-Object { @($_.flakyTests)[0] }) | Select-Object -Unique) -join '; ') }
    $failedTests = @($reports | ForEach-Object { foreach ($t in @($_.harness.tests)) { if ($t -and $t.status -ne 'PASS') { $t } } })
    $withArea = @($failedTests | Where-Object { @($_.area).Count }).Count
    if ($failedTests.Count) {
        "  failed game tests: $($failedTests.Count) ($withArea with area lines); most repeated:"
        # Grouped by test, step and message: one step can fail for several different reasons.
        $failedTests | ForEach-Object { $m = [string]$_.message -replace '\s+', ' '; $_ | Add-Member -NotePropertyName shortMessage -NotePropertyValue $m.Substring(0, [Math]::Min(120, $m.Length)) -Force; $_ } |
            Group-Object { "$($_.id) at $($_.phase): $($_.shortMessage)" } | Sort-Object Count -Descending | Select-Object -First 5 | ForEach-Object { "    x$($_.Count) $($_.Name)" }
    }
    $compile = @($reports | Where-Object { $_.status -ne 'PASS' -and $_.profile -eq 'build' } | ForEach-Object { @($_.problems)[0] } | Where-Object { $_ })
    if ($compile.Count) { "  build failures: $($compile.Count); first problems: " + ((@($compile | Select-Object -First 4 | ForEach-Object { ($_ -replace '^.*?/([^/]+\.java)', '$1').Substring(0, [Math]::Min(90, ($_ -replace '^.*?/([^/]+\.java)', '$1').Length)) })) -join ' | ') }
    $final = @{}; foreach ($r in $reports) { $final[[string]$r.profile] = $r.status }
    "  last check: $($reports[-1].profile) $($reports[-1].status) (server and game checks include the build)"
} else { 'checks: none run' }

# Kit files changed or removed against the release manifest (pinned build files, tools, docs).
$manifest = Join-Path $repo "releases/$version/manifest.json"
if ([IO.File]::Exists($manifest)) {
    $changed = @(foreach ($f in (Read-Json $manifest).files) {
        if ($f.path -like 'src/*' -or $f.path -in @('TASK.md')) { continue }
        $file = Join-Path $trial $f.path
        if (-not [IO.File]::Exists($file)) { "$($f.path) (removed)" } elseif ((Get-FileHash -LiteralPath $file -Algorithm SHA256).Hash -ne $f.sha256) { $f.path }
    })
    # Show what changed, from the release ZIP; init, version and checkout legitimately set group, version, jar and project name.
    $info = Read-Json $manifest
    $zipFile = Join-Path $repo ("releases/$version/" + [IO.Path]::GetFileName($info.archive))
    $expected = '^\s*(group|version|rootProject\.name)\s*=|archivesName'
    $details = @(); $real = @()
    Add-Type -AssemblyName System.IO.Compression.FileSystem
    $zip = if ([IO.File]::Exists($zipFile)) { [IO.Compression.ZipFile]::OpenRead($zipFile) } else { $null }
    try {
        foreach ($c in $changed) {
            if ($c -like '* (removed)' -or -not $zip) { $real += $c; continue }
            $entry = $zip.GetEntry(($info.archiveRoot.TrimEnd('/') + '/' + $c).TrimStart('/'))
            if (-not $entry) { $entry = $zip.GetEntry($c) }
            if (-not $entry) { $real += $c; continue }
            $reader = New-Object IO.StreamReader($entry.Open()); try { $old = $reader.ReadToEnd() -split '\r?\n' } finally { $reader.Dispose() }
            $new = [IO.File]::ReadAllLines((Join-Path $trial $c))
            $diff = @(Compare-Object $old $new | Where-Object { $_.InputObject.Trim() -and $_.InputObject -notmatch $expected } | ForEach-Object { $(if ($_.SideIndicator -eq '=>') { '+ ' } else { '- ' }) + $_.InputObject.Trim() })
            if ($diff.Count) { $real += $c; $details += "  $c`: " + (($diff | Select-Object -First 3) -join ' | ') } else { $details += "  $c`: init/version edits only" }
        }
    } finally { if ($zip) { $zip.Dispose() } }
    $pinned = @($real | Where-Object { $_ -match '^(build\.gradle|settings\.gradle|gradle\.properties|gradle/|testing/)' })
    if ($real.Count) { "kit files changed by the agent: $($real -join ', ')" + $(if ($pinned.Count) { '  <- includes pinned build/test files' } else { '' }) } else { 'kit files changed by the agent: none' }
    $details
} else { "kit files changed: unknown (no releases/$version/manifest.json in this repo)" }

# The mod itself.
$main = @(Get-ChildItem -Recurse -File -Path (Join-Path $trial 'src/main/java') -Filter '*.java' -ErrorAction SilentlyContinue)
$tests = @(Get-ChildItem -Recurse -File -Path (Join-Path $trial 'src/gametest/java') -Filter '*.java' -ErrorAction SilentlyContinue)
$lines = { param($files) ($files | ForEach-Object { [IO.File]::ReadAllLines($_.FullName).Count } | Measure-Object -Sum).Sum }
"mod: $($main.Count) main classes ($(& $lines $main) lines), $($tests.Count) test classes ($(& $lines $tests) lines)" + $(if (@($tests | Where-Object { [IO.File]::ReadAllText($_.FullName).Contains('KIT-SCAFFOLD') }).Count) { '; an unfinished test scaffold remains' } else { '' })
$jars = @(Get-ChildItem -Path (Join-Path $trial 'releases') -Filter '*.jar' -ErrorAction SilentlyContinue)
'exported: ' + $(if ($jars.Count) { ($jars.Name -join ', ') } else { 'no jar' })
$task = Join-Path $trial 'TASK.md'
if ([IO.File]::Exists($task)) { 'TASK.md:'; [IO.File]::ReadAllLines($task) | Where-Object { $_.Trim() } | Select-Object -First 12 | ForEach-Object { '  ' + $(if ($_.Length -gt 160) { $_.Substring(0, 160) + '...' } else { $_ }) } }

# The model side (OpenCode's session store): generating time, output mix, compactions, tool errors.
$python = Get-Command python -ErrorAction SilentlyContinue
if ($python) { & $python.Source -I (Join-Path $PSScriptRoot 'Read-OpenCode.py') $trial }
else { 'opencode: python not found; run qa/local-model/Read-OpenCode.py <trial> for the model side' }
