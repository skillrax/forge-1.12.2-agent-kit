# Single entry point for agents. Prints a few plain-text lines by default; -Json prints the full result.
#   agent.ps1 status
#   agent.ps1 check [build|server|game|unit|environment] [-Test id[,id]] [-Clean]
#   agent.ps1 errors [-RunId id]
#   agent.ps1 api <Class> | <Class>.<member> | -Find <text>
#   agent.ps1 new <kind> <name> [options]        (see docs/GENERATORS.md)
[CmdletBinding(PositionalBinding = $false)]
param(
    [Parameter(Position = 0)][ValidateSet('status', 'check', 'errors', 'api', 'new', 'init')][string]$Action = 'status',
    [Parameter(Position = 1)][string]$Target,
    [Parameter(Position = 2, ValueFromRemainingArguments = $true)][string[]]$Rest,
    [ValidateSet('environment', 'build', 'unit', 'server', 'game', 'mod', 'all')][string]$Profile,
    [ValidatePattern('^[a-z0-9*,-]*$')][string]$Test = '*',
    [string]$Find,
    [ValidateRange(10, 7200)][int]$TimeoutSeconds = 900,
    [ValidatePattern('^[A-Za-z0-9-]+$')][string]$RunId,
    [switch]$Json,
    [switch]$Full,
    [switch]$Clean
)
$ErrorActionPreference = 'Stop'
[Console]::OutputEncoding = New-Object Text.UTF8Encoding($false)
$OutputEncoding = [Console]::OutputEncoding
# Prefer this shell's modules over paths inherited from a different PowerShell host.
$env:PSModulePath = (Join-Path $PSHOME 'Modules') + ';' + $env:PSModulePath
# MCP hosts may omit these Windows variables. Restore only this process's defaults.
if (-not $env:PATHEXT) { $env:PATHEXT = '.COM;.EXE;.BAT;.CMD' }
if (-not [IO.File]::Exists($env:ComSpec)) { $env:ComSpec = Join-Path ([Environment]::GetFolderPath('System')) 'cmd.exe' }
$root = [IO.Path]::GetFullPath($PSScriptRoot)
$reportRoot = Join-Path $root 'reports/testing-tools'
$javaSelection = $null
if ([IO.File]::Exists((Join-Path $root 'tools/JavaEnvironment.ps1'))) {
    . (Join-Path $root 'tools/JavaEnvironment.ps1')
    $javaSelection = Get-KitJavaSelection
    Set-KitJavaProcess $javaSelection
}
. (Join-Path $root 'testing/Diagnostics.ps1')

function Get-Relative([string]$Path) {
    if ($Path -and $Path.StartsWith($root, [StringComparison]::OrdinalIgnoreCase)) { return $Path.Substring($root.Length).TrimStart('\', '/').Replace('\', '/') }
    return $Path
}

# modid/version/entry class from source, so status needs no build.
function Get-ModInfo {
    $javaRoot = Join-Path $root 'src/main/java'
    if (-not [IO.Directory]::Exists($javaRoot)) { return $null }
    foreach ($file in [IO.Directory]::GetFiles($javaRoot, '*.java', [IO.SearchOption]::AllDirectories)) {
        $text = [IO.File]::ReadAllText($file)
        if ($text -notmatch '@Mod\s*\(') { continue }
        $modId = if ($text -match 'modid\s*=\s*"([^"]+)"') { $Matches[1] } elseif ($text -match 'MODID\s*=\s*"([^"]+)"') { $Matches[1] } else { '?' }
        $package = if ($text -match '(?m)^package\s+([\w.]+);') { $Matches[1] } else { '' }
        $build = [IO.File]::ReadAllText((Join-Path $root 'build.gradle'))
        $version = if ($build -match "(?m)^version = '([^']+)'") { $Matches[1] } else { '?' }
        return [ordered]@{ modId = $modId; version = $version; package = $package; entry = (Get-Relative $file) }
    }
    return $null
}

function Get-NewestSourceTime {
    $newest = [DateTime]::MinValue
    foreach ($dir in @('src', 'testing')) {
        $path = Join-Path $root $dir
        if (-not [IO.Directory]::Exists($path)) { continue }
        foreach ($file in [IO.Directory]::GetFiles($path, '*', [IO.SearchOption]::AllDirectories)) {
            $time = [IO.File]::GetLastWriteTimeUtc($file); if ($time -gt $newest) { $newest = $time }
        }
    }
    $build = [IO.File]::GetLastWriteTimeUtc((Join-Path $root 'build.gradle'))
    if ($build -gt $newest) { $newest = $build }
    return $newest
}

function Read-Result {
    $path = if ($RunId) { Join-Path $reportRoot ($RunId + '.json') } else { Join-Path $reportRoot 'latest.json' }
    if (Test-Path -LiteralPath $path) { return ([IO.File]::ReadAllText($path) | ConvertFrom-Json) }
    if ($RunId) { throw "Unknown run ID: $RunId" }
    return $null
}

# Problem lines for a finished run: compiler errors with hints, then crashes/log warnings, then lint, then tests.
function Get-Problems($Result) {
    $lines = New-Object 'System.Collections.Generic.List[string]'
    $log = if ($Result.log -and [IO.File]::Exists($Result.log)) { [IO.File]::ReadAllLines($Result.log) } else { @() }
    $compile = @(Get-KitCompileErrors $root $log)
    foreach ($e in ($compile | Select-Object -First 8)) {
        $detail = @($e.details | Where-Object { $_ -notmatch '^location:' }) -join '; '
        $lines.Add(('{0}:{1}: {2}{3}' -f $e.file, $e.line, $e.message, $(if ($detail) { ' (' + $detail + ')' } else { '' })))
        if ($e.code) { $lines.Add('    ' + $(if ($e.code.Length -gt 160) { $e.code.Substring(0, 160) } else { $e.code })) }
        if ($e.hint) { $lines.Add('  hint: ' + $e.hint) }
    }
    if ($compile.Count -gt 8) { $lines.Add(('... {0} more compiler errors; fix these first' -f ($compile.Count - 8))) }
    if (-not $compile.Count) { foreach ($l in (Get-KitLogProblems $log)) { $lines.Add($l) } }
    if ($Result.harness) {
        foreach ($issue in @($Result.harness.lint)) { $lines.Add(('{0} {1}: {2}' -f $issue.level, $issue.subject, $issue.message)) }
        foreach ($t in @($Result.harness.tests)) {
            if ($t.status -eq 'PASS') { $lines.Add(('PASS test {0} ({1} assertions, {2} ticks)' -f $t.id, $t.assertions, $t.ticks)) }
            else {
                $lines.Add(('FAIL test {0} at {1}: {2}' -f $t.id, $t.phase, $t.message))
                foreach ($frame in @($t.stack)) { $lines.Add('    at ' + $frame) }
            }
        }
        if ($Result.harness.lintError) { $lines.Add('harness lint crashed: ' + $Result.harness.lintError) }
    }
    if ($Result.status -ne 'PASS' -and -not $lines.Count) { foreach ($l in (Get-BoundedDiagnostics $Result.log)) { $lines.Add($l) } }
    return $lines
}

function Format-Check($Result) {
    $info = Get-ModInfo
    $head = '{0} {1} ({2}s)' -f $Result.status, $Result.profile, [int]$Result.elapsedSeconds
    $summary = $Result.message
    if ($Result.status -eq 'PASS') {
        $parts = @()
        if ($info) { $parts += ($info.modId + ' ' + $info.version) }
        if ($Result.harness -and $Result.harness.registered) {
            $r = $Result.harness.registered
            $counts = foreach ($name in @('items', 'blocks', 'entities', 'recipes', 'sounds', 'enchantments', 'potions', 'biomes')) { if ($r.$name) { '{0} {1}' -f $r.$name, $name } }
            if ($counts) { $parts += ('registered ' + ($counts -join ', ')) }
        }
        if ($null -ne $Result.unitTestCount -and $Result.unitTestCount -gt 0) { $parts += ('{0} unit tests' -f $Result.unitTestCount) }
        if ($Result.inspectedArtifact -and $Result.profile -eq 'build') { $parts += (Get-Relative $Result.inspectedArtifact) }
        if ($parts) { $summary = $parts -join '; ' }
    }
    $out = @($head + ': ' + $summary)
    $out += @(Get-Problems $Result)
    if ($Result.status -ne 'PASS') { $out += ('log: ' + (Get-Relative $Result.log) + '  (run ' + $Result.runId + ')') }
    return ($out -join "`n")
}

# ---- api ---------------------------------------------------------------------------------

function Invoke-Api {
    $api = Get-KitApiIndex $root
    if (-not $api) {
        & (Join-Path $root 'gradlew.bat') kitApiIndex --console=plain -q *> $null
        $api = Get-KitApiIndex $root
        if (-not $api) { throw 'API index missing; run: agent.ps1 check environment' }
    }
    $mark = { param($m) if ($m[4] -match 'client-only') { '  [client-only]' } else { '' } }
    if ($Find) {
        $needle = $Find.ToLowerInvariant()
        $hits = New-Object 'System.Collections.Generic.List[string]'
        foreach ($c in $api.classes.Keys) { if ($c.Substring($c.LastIndexOf('.') + 1).ToLowerInvariant().Contains($needle)) { $hits.Add('class ' + $c) } }
        foreach ($owner in $api.members.Keys) {
            foreach ($m in $api.members[$owner]) {
                if ($m[2] -ne '<init>' -and $m[2].ToLowerInvariant().Contains($needle)) { $hits.Add((Get-KitShortName $owner) + '.' + $m[3] + (& $mark $m) + '   (' + $owner + ')') }
            }
        }
        $sorted = @($hits | Sort-Object { $_.Length })
        $sorted | Select-Object -First 40
        if ($sorted.Count -gt 40) { '... {0} more; narrow the search' -f ($sorted.Count - 40) }
        if (-not $sorted.Count) { "nothing in 1.12.2 Minecraft/Forge contains '$Find'" }
        return
    }
    if (-not $Target) { throw 'Usage: agent.ps1 api <Class> | <Class>.<member> | -Find <text>' }
    $className = $Target; $member = $null
    if ($Target -cmatch '^(.+)\.([a-z_$][\w$]*)$') { $className = $Matches[1]; $member = $Matches[2] }
    $candidates = @(Resolve-KitClass $api $className)
    if (-not $candidates.Count) {
        $near = @($api.bySimple.Keys | Where-Object { $_ -like ('*' + $className + '*') } | Select-Object -First 8)
        throw ("No class '$className' in 1.12.2." + $(if ($near.Count) { ' Similar: ' + ($near -join ', ') } else { ' Try: agent.ps1 api -Find <word>' }))
    }
    if ($candidates.Count -gt 1 -and -not $member) { '(also: ' + (($candidates | Select-Object -Skip 1 -First 4) -join ', ') + ')' }
    $chain = @(Get-KitClassChain $api $candidates[0])
    $c = $api.classes[$chain[0]]
    $header = '{0} {1}' -f $c[4], $c[1]
    if ($chain.Count -gt 1) { $header += ' extends ' + (($chain | Select-Object -Skip 1 | ForEach-Object { Get-KitShortName $_ }) -join ' > ') }
    if ($c[3]) { $header += ' implements ' + (($c[3].Split(',') | ForEach-Object { Get-KitShortName $_ }) -join ', ') }
    if (-not $member) {
        $header
        $rows = @(if ($api.members.ContainsKey($chain[0])) { $api.members[$chain[0]] })
        $lines = foreach ($m in $rows) {
            $mods = ($m[4] -replace 'client-only', '').Trim()
            '  ' + $(if ($m[0] -eq 'F') { 'field ' + $m[2] + ': ' + $m[3] } else { $m[3] }) + $(if ($mods) { '  {' + $mods + '}' } else { '' }) + (& $mark $m)
        }
        $lines | Select-Object -First 80
        if (@($lines).Count -gt 80) { '  ... {0} more; use agent.ps1 api -Find <text> or api {1}.<member>' -f (@($lines).Count - 80), $className }
        if ($chain.Count -gt 1) { '  (inherited members: agent.ps1 api ' + (Get-KitShortName $chain[1]) + ')' }
        return
    }
    $found = @(foreach ($owner in $chain) { if ($api.members.ContainsKey($owner)) { foreach ($m in $api.members[$owner]) { if ($m[2] -eq $member) { ,@($owner, $m) } } } })
    if (-not $found.Count) {
        $similar = Find-KitSimilarMembers $api $chain[0] $member $null
        throw ("No member '$member' on $className or its superclasses." + $(if ($similar.Count) { ' Did you mean: ' + ($similar -join ' | ') } else { '' }))
    }
    foreach ($pair in $found) { '{0}.{1}{2}{3}' -f (Get-KitShortName $pair[0]), $(if ($pair[1][0] -eq 'F') { $member + ': ' + $pair[1][3] } else { $pair[1][3] }), $(if (($pair[1][4] -replace 'client-only', '').Trim()) { '  {' + ($pair[1][4] -replace 'client-only', '').Trim() + '}' } else { '' }), (& $mark $pair[1]) }
    # Source of the first declaring class, bounded.
    # Nested classes live in their outermost class's source file.
    $segments = $found[0][0].Split('.')
    $outer = @(); foreach ($s in $segments) { $outer += $s; if ($s -cmatch '^[A-Z]') { break } }
    $source = & (Join-Path $root 'tools/Read-MappedSource.ps1') -Class ($outer -join '.') -Member $member -MaxLines $(if ($Full) { 80 } else { 25 }) -Text 2>$null
    if ($LASTEXITCODE -eq 0 -and $source) { ''; $source }
}

# ---- main --------------------------------------------------------------------------------

$exitCode = 0
$lock = $null
try {
    switch ($Action) {
        'check' {
            $selected = if ($Profile) { $Profile } elseif ($Target) { $Target } else { 'build' }
            if ($selected -in @('mod', 'all')) { $selected = 'build' }
            if ($selected -notin @('environment', 'build', 'unit', 'server', 'game')) { throw "Unknown profile '$selected'. Use build, server, game, unit or environment." }
            New-Item -ItemType Directory -Force -Path $reportRoot | Out-Null
            try { $lock = [IO.File]::Open((Join-Path $reportRoot 'agent.lock'), 'OpenOrCreate', 'ReadWrite', 'None') }
            catch { throw 'Another check is running in this project. Wait for it to finish.' }
            $start = New-Object Diagnostics.ProcessStartInfo
            $start.FileName = (Get-Process -Id $PID).Path
            $start.Arguments = '-NoProfile -File "' + (Join-Path $root 'testing/Test-DevEnvironment.ps1') + '" -Profile ' + $selected + ' -Test "' + $Test + '" -TimeoutSeconds ' + $TimeoutSeconds + $(if ($Clean) { ' -Clean' } else { '' })
            $start.WorkingDirectory = $root
            $start.UseShellExecute = $false
            $start.CreateNoWindow = $true
            $start.RedirectStandardOutput = $true
            $start.RedirectStandardError = $true
            $runner = New-Object Diagnostics.Process
            $runner.StartInfo = $start
            try {
                if (-not $runner.Start()) { throw 'Could not start the check runner.' }
                $stdoutRead = $runner.StandardOutput.ReadToEndAsync()
                # Progress lines go to stderr so the final stdout stays a single result.
                $stderrRead = $runner.StandardError.ReadLineAsync(); $stderrEnded = $false
                $watch = [Diagnostics.Stopwatch]::StartNew()
                while (-not ($runner.HasExited -and $stderrEnded)) {
                    while (-not $stderrEnded -and $stderrRead.IsCompleted) {
                        $line = $stderrRead.GetAwaiter().GetResult()
                        if ($null -eq $line) { $stderrEnded = $true } else { [Console]::Error.WriteLine($line); $stderrRead = $runner.StandardError.ReadLineAsync() }
                    }
                    if ($watch.Elapsed.TotalSeconds -gt ($TimeoutSeconds + 30)) { break }
                    Start-Sleep -Milliseconds 50
                }
                if (-not $runner.HasExited) {
                    & (Join-Path ([Environment]::GetFolderPath('System')) 'taskkill.exe') /PID $runner.Id /T /F *> $null
                    throw 'Check exceeded its timeout and was stopped.'
                }
                $output = $stdoutRead.GetAwaiter().GetResult()
                $exitCode = $runner.ExitCode
            } finally { $runner.Dispose() }
            $result = $output | ConvertFrom-Json
            if ($null -eq $result -or -not $result.report) { throw "Check runner returned no result (exit code $exitCode)." }
            $result | Add-Member -NotePropertyName projectRoot -NotePropertyValue $root
            $result | Add-Member -NotePropertyName runId -NotePropertyValue ([IO.Path]::GetFileNameWithoutExtension($result.report))
            $result | Add-Member -NotePropertyName problems -NotePropertyValue @(Get-Problems $result)
            $resultJson = $result | ConvertTo-Json -Depth 8 -Compress
            Set-Content -LiteralPath $result.report -Value $resultJson -Encoding UTF8
            Set-Content -LiteralPath (Join-Path $reportRoot 'latest.json') -Value $resultJson -Encoding UTF8
            Set-Content -LiteralPath (Join-Path $reportRoot ($selected + '-latest.json')) -Value $resultJson -Encoding UTF8
            if ($Json) { $resultJson } else { Format-Check $result }
        }
        'status' {
            $info = Get-ModInfo
            $last = Read-Result
            $java = if (-not $javaSelection) { 'unknown' } elseif ($javaSelection.gradle -and $javaSelection.java8) { 'ok (JDK ' + $javaSelection.gradle.major + ' for Gradle, JDK 8 for mods)' } else { 'MISSING - run Setup.cmd (see SETUP.md)' }
            $stale = $false
            if ($last) { $stale = (Get-NewestSourceTime) -gt [IO.File]::GetLastWriteTimeUtc($last.report) }
            $task = if ([IO.File]::Exists((Join-Path $root 'TASK.md'))) { [IO.File]::ReadAllText((Join-Path $root 'TASK.md')) } else { '' }
            $next = if (-not ($javaSelection.gradle -and $javaSelection.java8)) { 'Run Setup.cmd (SETUP.md).' }
                elseif (-not $info) { 'No mod yet: agent.ps1 init <modid> "<Name>" (creates a working skeleton).' }
                elseif (-not $last -or $stale) { 'Run agent.ps1 check (then check server before finishing).' }
                elseif ($last.status -ne 'PASS') { 'Fix the last failure (agent.ps1 errors), then rerun agent.ps1 check ' + $last.profile + '.' }
                else { 'Continue TASK.md.' }
            if ($Json) {
                [ordered]@{ schemaVersion = 1; action = 'status'; projectRoot = $root; mod = $info; java = $javaSelection; lastRun = $(if ($last) { $last | Select-Object runId, profile, status, message, log }); lastRunStale = $stale; task = $task; nextAction = $next } | ConvertTo-Json -Depth 6 -Compress
            } else {
                'project: ' + $root
                'mod: ' + $(if ($info) { '{0} {1} ({2})' -f $info.modId, $info.version, $info.entry } else { 'none' })
                'java: ' + $java
                'last check: ' + $(if ($last) { '{0} {1} {2}{3}' -f $last.status, $last.profile, ([IO.File]::GetLastWriteTime($last.report).ToString('yyyy-MM-dd HH:mm')), $(if ($stale) { ' (source changed since)' } else { '' }) } else { 'none' })
                'next: ' + $next
            }
        }
        'errors' {
            $last = Read-Result
            if (-not $last) { throw 'No check has run yet.' }
            if ($Json) { $last | ConvertTo-Json -Depth 8 -Compress }
            else {
                Format-Check $last
                if ($Full -and $last.log) { ''; '--- last 60 log lines ---'; Get-Content -LiteralPath $last.log -Tail 60 }
            }
        }
        'api' { Invoke-Api }
        'init' {
            $initArgs = @('-ModId', $Target) + @($Rest)
            & (Join-Path $root 'tools/Initialize-Mod.ps1') @initArgs
            $exitCode = $LASTEXITCODE
        }
        'new' {
            $newArgs = @('-Kind', $Target) + @($Rest)
            & (Join-Path $root 'tools/New-Content.ps1') @newArgs
            $exitCode = $LASTEXITCODE
        }
    }
} catch {
    $exitCode = 1
    if ($Json) { [ordered]@{ schemaVersion = 1; projectRoot = $root; action = $Action; status = 'FAIL'; exitCode = 1; message = $_.Exception.Message } | ConvertTo-Json -Compress }
    else { 'FAIL: ' + $_.Exception.Message; if ($env:KIT_DEBUG) { $_.ScriptStackTrace } }
} finally {
    if ($null -ne $lock) { $lock.Dispose() }
}
exit $exitCode
