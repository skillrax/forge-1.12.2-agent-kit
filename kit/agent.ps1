# Single entry point for agents. Prints a few plain-text lines by default; -Json prints the full result.
#   agent.ps1 status
#   agent.ps1 check [build|server|game|unit|environment] [-Test id[,id]] [-Clean]
#   agent.ps1 errors [-RunId id]
#   agent.ps1 api <Class> | <Class>.<member> | -Find <text>
#   agent.ps1 init <modid> ["Name"]              (working mod skeleton)
#   agent.ps1 new <kind> <name> [options]        (generators: docs/GENERATORS.md)
#   agent.ps1 version <x.y.z>                    (build.gradle + @Mod + mcmod.info)
#   agent.ps1 export                             (releases/<modid>-<version>.jar after a passing check server)
#   agent.ps1 batch <file>                       (one command per line; stops at the first failure)
[CmdletBinding(PositionalBinding = $false)]
param(
    [Parameter(Position = 0)][ValidateSet('status', 'check', 'errors', 'api', 'new', 'init', 'version', 'export', 'batch')][string]$Action = 'status',
    [Parameter(Position = 1)][string]$Target,
    [Parameter(Position = 2, ValueFromRemainingArguments = $true)][object[]]$Rest,
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
# Some agent hosts omit these Windows variables. Restore only this process's defaults.
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
    foreach ($call in @($Result.clientOnlyCalls)) { if ($call) { $lines.Add($call) } }
    if ($Result.harness) {
        foreach ($issue in @($Result.harness.lint)) { $lines.Add(('{0} {1}: {2}' -f $issue.level, $issue.subject, $issue.message)) }
        foreach ($t in @($Result.harness.tests)) {
            if ($t.status -eq 'PASS') { $lines.Add(('PASS test {0} ({1} assertions, {2} ticks)' -f $t.id, $t.assertions, $t.ticks)) }
            else {
                $lines.Add(('FAIL test {0} at {1}: {2}' -f $t.id, $t.phase, $t.message))
                foreach ($frame in @($t.stack)) { $lines.Add('    at ' + $frame) }
                if (('{0} {1}' -f $t.type, $t.message) -match 'NoSuchMethodError|NoSuchFieldError') { $lines.Add('  hint: that member is not on the server: it is client-only (@SideOnly(Side.CLIENT); agent.ps1 api marks it [client-only]). Use a server-side alternative, or call it only from client code') }
                if (@($t.area).Count) {
                    $lines.Add('  area (x,y,z from t.origin()):')
                    foreach ($a in @($t.area)) { $lines.Add('    ' + $a) }
                }
            }
        }
        if ($Result.harness.lintError) { $lines.Add('harness lint crashed: ' + $Result.harness.lintError) }
    }
    if ($Result.status -ne 'PASS' -and -not $lines.Count) { foreach ($l in (Get-BoundedDiagnostics $Result.log)) { $lines.Add($l) } }
    return $lines
}

# The same failure in consecutive checks: a test at the same step, else the first problem without its line number.
function Get-FailureKey($Result) {
    if ($Result.status -eq 'PASS' -or $Result.networkError) { return $null }
    $test = @(@($Result.harness.tests) | Where-Object { $_ -and $_.status -ne 'PASS' }) | Select-Object -First 1
    if ($test) { return ('test {0} at {1}' -f $test.id, $test.phase) }
    $first = @($Result.problems) | Select-Object -First 1
    if ($first) { return ($first -replace ':\d+:', ':') }
    return [string]$Result.message
}

# Circuit breaker: small models can loop on one failure for an hour; after 3 identical failures say so.
function Update-FailureStreak($Result) {
    $path = Join-Path $reportRoot 'streak.json'
    $key = Get-FailureKey $Result
    if (-not $key) { if ([IO.File]::Exists($path)) { [IO.File]::Delete($path) }; return 0 }
    $previous = $null
    if ([IO.File]::Exists($path)) { try { $previous = [IO.File]::ReadAllText($path) | ConvertFrom-Json } catch { $previous = $null } }
    $count = if ($previous -and $previous.key -eq $key) { [int]$previous.count + 1 } else { 1 }
    # A test that keeps failing at changing steps is stuck too (each new setup changes the key above).
    $test = @(@($Result.harness.tests) | Where-Object { $_ -and $_.status -ne 'PASS' } | ForEach-Object { $_.id }) | Select-Object -First 1
    $testCount = if (-not $test) { 0 } elseif ($previous -and $previous.test -eq $test) { [int]$previous.testCount + 1 } else { 1 }
    [IO.File]::WriteAllText($path, (@{ key = $key; count = $count; test = $test; testCount = $testCount } | ConvertTo-Json -Compress), (New-Object Text.UTF8Encoding($false)))
    $Result | Add-Member -NotePropertyName testFailures -NotePropertyValue $testCount -Force
    return $count
}

# Recent outcomes per test, kept across PASSes: a test that failed at a step, passed, then failed there again is flaky.
function Update-TestHistory($Result) {
    $tests = @(@($Result.harness.tests) | Where-Object { $_ })
    if (-not $tests.Count) { return @() }
    $path = Join-Path $reportRoot 'test-history.json'
    $history = @{}
    if ([IO.File]::Exists($path)) { try { ([IO.File]::ReadAllText($path) | ConvertFrom-Json).PSObject.Properties | ForEach-Object { $history[$_.Name] = @($_.Value) } } catch { $history = @{} } }
    $flaky = @()
    foreach ($t in $tests) {
        $past = @($history[[string]$t.id] | Where-Object { $_ })
        $now = if ($t.status -eq 'PASS') { 'PASS' } else { 'FAIL ' + $t.phase }
        if ($now -ne 'PASS') {
            $lastSame = [array]::LastIndexOf([object[]]$past, $now)
            if ($lastSame -ge 0 -and @($past[$lastSame..($past.Count - 1)] | Where-Object { $_ -eq 'PASS' }).Count) { $flaky += $t }
        }
        $history[[string]$t.id] = @(@($past) + $now | Select-Object -Last 6)
    }
    [IO.File]::WriteAllText($path, ($history | ConvertTo-Json -Compress), (New-Object Text.UTF8Encoding($false)))
    return @($flaky | ForEach-Object { '{0} at {1}' -f $_.id, $_.phase })
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
    if ($Result.repeatedFailures -ge 3) {
        $key = Get-FailureKey $Result
        if ($key -like 'test *') { $out += ('STUCK: {0} failed the same way {1} checks in a row. Stop changing code to guess. Read the area lines; move this behavior into its own @GameTest with only its own setup and rerun it with -Test; if it still fails, finish with a partial report (AGENTS.md).' -f $key, $Result.repeatedFailures) }
        else { $out += ('STUCK: the same problem {0} checks in a row: {1}. Look up the exact signature (agent.ps1 api Class.member) or write it another way; if it still fails, finish with a partial report (AGENTS.md).' -f $Result.repeatedFailures, $key) }
    }
    foreach ($f in @($Result.flakyTests)) {
        if ($f) { $out += ('FLAKY: test {0} failed there before, then passed, and fails again. If your last change did not touch this behavior, the test depends on world noise: mobs burning in the always-day test world, the hurt cooldown after any damage (hits during it do nothing; arrows bounce), random ticks, mob AI. Read the area lines (mob hp, burning, hurt cooldown), make the setup deterministic, then rerun it twice with -Test.' -f $f) }
    }
    if ($Result.repeatedFailures -lt 3 -and $Result.testFailures -ge 5) {
        $test = @(@($Result.harness.tests) | Where-Object { $_ -and $_.status -ne 'PASS' }) | Select-Object -First 1
        $out += ('STUCK: test {0} failed {1} checks in a row. Stop changing the setup. Reduce it to the one behavior that fails, compare the area lines with what you expected, look up that behavior (agent.ps1 api); if it still fails, finish with a partial report (AGENTS.md).' -f $test.id, $Result.testFailures)
    }
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
    $reader = Join-Path $root 'tools/Read-MappedSource.ps1'
    $mark = { param($m) if ($m[4] -match 'client-only') { '  [client-only]' } else { '' } }
    if ($Find) {
        $needle = $Find.ToLowerInvariant()
        $hits = New-Object 'System.Collections.Generic.List[string]'
        foreach ($c in $api.classes.Keys) { if ($c.Substring($c.LastIndexOf('.') + 1).ToLowerInvariant().Contains($needle)) { $hits.Add('class ' + $c) } }
        foreach ($owner in $api.members.Keys) {
            foreach ($m in $api.members[$owner]) {
                if ($m[2] -ne '<init>' -and $m[2].ToLowerInvariant().Contains($needle)) { $hits.Add((Get-KitShortName $owner) + '.' + $(if ($m[0] -eq 'F') { $m[2] + ': ' + $m[3] } else { $m[3] }) + (& $mark $m) + '   (' + $owner + ')') }
            }
        }
        $sorted = @($hits | Sort-Object { $_.Length })
        $sorted | Select-Object -First 40
        if ($sorted.Count -gt 40) { '... {0} more; narrow the search' -f ($sorted.Count - 40) }
        if ($sorted.Count -lt 3) {
            # Private fields, constants and literals are only in the source.
            if ($sorted.Count) { '' }
            'source lines containing "' + $Find + '":'
            & $reader -Grep $Find -MaxLines 25 -Text 2>$null
        }
        return
    }
    if (-not $Target) { throw 'Usage: agent.ps1 api <Class> | <Class>.<member> | <Class>.new (constructors) | -Find <text>' }
    $className = $Target; $member = $null
    if ($Target -cmatch '^(.+)\.([a-z_$][\w$]*|<init>)$') { $className = $Matches[1]; $member = $Matches[2] }
    elseif ($Target -cmatch '^(.+)\.([A-Z][\w$]*)$' -and ($Matches[1] -replace '^.*\.', '') -ceq $Matches[2]) { $className = $Matches[1]; $member = '<init>' }   # ItemAxe.ItemAxe
    if ($member -eq 'new') { $member = '<init>' }
    # The kit's own test harness (TestContext, Scenario, GameTest) is read from testing/harness/src.
    if ($className -cmatch '^(kit\.gametest\.)?(TestContext|Scenario|GameTest)$') {
        $harnessClass = 'kit.gametest.' + $Matches[2]; $harnessSrc = Join-Path $root 'testing/harness/src'
        if ($member) { & $reader -Class $harnessClass -Member $member -MaxLines $(if ($Full) { 150 } else { 40 }) -Text -Archive $harnessSrc 2>$null; return }
        $found = (& $reader -Class $harnessClass -Declarations -MaxLines 200 -Archive $harnessSrc 2>$null) | ConvertFrom-Json
        $harnessClass + ' (kit harness; table: docs/TESTING.md)'
        foreach ($d in @($found.declarations)) { if ($d.text -match '^\s*public\b') { '  ' + ($d.text.Trim() -replace '\s*\{.*$', '') } }
        return
    }
    $candidates = @(Resolve-KitClass $api $className)
    if (-not $candidates.Count) {
        $own = @(foreach ($dir in @('src/main/java', 'src/gametest/java')) { $p = Join-Path $root $dir; if ([IO.Directory]::Exists($p)) { [IO.Directory]::GetFiles($p, ($className -replace '^.*\.', '') + '.java', [IO.SearchOption]::AllDirectories) } })
        if ($own.Count) { throw ("'$className' is your mod's class: read " + (($own | ForEach-Object { $_.Substring($root.Length + 1).Replace('\', '/') }) -join ', ') + ' (api covers Minecraft and Forge only).') }
        $near = @($api.bySimple.Keys | Where-Object { $_ -like ('*' + $className + '*') } | Select-Object -First 8)
        throw ("No class '$className' in 1.12.2." + $(if ($near.Count) { ' Similar: ' + ($near -join ', ') } else { ' Try: agent.ps1 api -Find <word>' }))
    }
    if ($candidates.Count -gt 1 -and -not $member) { '(also: ' + (($candidates | Select-Object -Skip 1 -First 4) -join ', ') + ')' }
    $chain = @(Get-KitClassChain $api $candidates[0])
    $c = $api.classes[$chain[0]]
    # Source file of a (possibly nested) class: the outermost class's .java file.
    $segments = $chain[0].Split('.')
    $outer = @(); foreach ($s in $segments) { $outer += $s; if ($s -cmatch '^[A-Z]') { break } }
    $outerClass = $outer -join '.'
    $simple = $segments[-1]
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
        $limit = if ($Full) { 400 } else { 60 }
        $lines | Select-Object -First $limit
        if (@($lines).Count -gt $limit) { '  ... {0} more (-Full lists all; or api {1}.<member>, api -Find <text>)' -f (@($lines).Count - $limit), $className }
        if ($chain.Count -gt 1) { '  (inherited members: agent.ps1 api ' + (Get-KitShortName $chain[1]) + ')' }
        # Declaration and first fields, including private constants the signature list cannot show.
        ''
        & $reader -Class $outerClass -Head -TypeName $simple -MaxLines $(if ($Full) { 80 } else { 20 }) -Text 2>$null
        return
    }
    if ($member -eq '<init>') {
        $ctors = @(if ($api.members.ContainsKey($chain[0])) { $api.members[$chain[0]] | Where-Object { $_[2] -eq '<init>' } })
        if (-not $ctors.Count) { throw "$className has no public/protected constructor." }
        foreach ($m in $ctors) { 'new ' + $m[3] + $(if (($m[4] -replace 'client-only', '').Trim()) { '  {' + ($m[4] -replace 'client-only', '').Trim() + '}' } else { '' }) }
        ''
        & $reader -Class $outerClass -Member $simple -MaxLines $(if ($Full) { 150 } else { 40 }) -Text 2>$null
        return
    }
    $found = @(foreach ($owner in $chain) { if ($api.members.ContainsKey($owner)) { foreach ($m in $api.members[$owner]) { if ($m[2] -eq $member) { ,@($owner, $m) } } } })
    if (-not $found.Count) {
        if ($script:KitRenames.ContainsKey($member)) { throw "No member '$member' in 1.12.2 (stable_39); use $($script:KitRenames[$member])" }
        # Private members are not in the index but are in the source.
        $private = & $reader -Class $outerClass -Member $member -MaxLines $(if ($Full) { 150 } else { 40 }) -Text 2>$null
        if ($LASTEXITCODE -eq 0 -and $private) { "(not public/protected; source below)"; $private; return }
        $similar = Find-KitSimilarMembers $api $chain[0] $member $null
        throw ("No member '$member' on $className or its superclasses." + $(if ($similar.Count) { ' Did you mean: ' + ($similar -join ' | ') } else { '' }))
    }
    foreach ($pair in $found) { '{0}.{1}{2}{3}' -f (Get-KitShortName $pair[0]), $(if ($pair[1][0] -eq 'F') { $member + ': ' + $pair[1][3] } else { $pair[1][3] }), $(if (($pair[1][4] -replace 'client-only', '').Trim()) { '  {' + ($pair[1][4] -replace 'client-only', '').Trim() + '}' } else { '' }), (& $mark $pair[1]) }
    # Source of the first declaring class, bounded.
    $owner = $found[0][0].Split('.')
    $ownerOuter = @(); foreach ($s in $owner) { $ownerOuter += $s; if ($s -cmatch '^[A-Z]') { break } }
    $source = & $reader -Class ($ownerOuter -join '.') -Member $member -MaxLines $(if ($Full) { 150 } else { 40 }) -Text 2>$null
    if ($LASTEXITCODE -eq 0 -and $source) { ''; $source }
}

# Options after the subcommand are forwarded: -Name value pairs, bare -Switches, positionals.
# PowerShell turns unquoted a,b,c into an array; join it back so "3,8,6,3" and 3,8,6,3 behave the same.
# One kit command that writes or builds at a time: generators edit shared files (ModItems, lang, GeneratedContentTest).
function Enter-KitLock([int]$WaitSeconds) {
    New-Item -ItemType Directory -Force -Path $reportRoot | Out-Null
    $until = [DateTime]::UtcNow.AddSeconds($WaitSeconds)
    while ($true) {
        try { return [IO.File]::Open((Join-Path $reportRoot 'agent.lock'), 'OpenOrCreate', 'ReadWrite', 'None') }
        catch { if ([DateTime]::UtcNow -gt $until) { throw 'Another agent.ps1 check/init/new is running in this project. Run kit commands one at a time (several generators: batch).' } }
        Start-Sleep -Milliseconds 200
    }
}

function ConvertTo-Splat([object[]]$Tokens) {
    $named = @{}; $positional = New-Object 'System.Collections.Generic.List[string]'
    $flat = @(foreach ($token in $Tokens) { if ($token -is [array]) { ($token | ForEach-Object { "$_" }) -join ',' } else { "$token" } })
    for ($i = 0; $i -lt $flat.Count; $i++) {
        if ($flat[$i] -match '^-([A-Za-z]+)$') {
            $key = $Matches[1]
            if ($i + 1 -lt $flat.Count -and $flat[$i + 1] -notmatch '^-[A-Za-z]') { $named[$key] = $flat[$i + 1]; $i++ } else { $named[$key] = $true }
        } else { $positional.Add($flat[$i]) }
    }
    if ($Json) { $named['Json'] = $true }
    return @{ named = $named; positional = $positional }
}

function Set-ModVersion([string]$Version, [bool]$Force) {
    if ($Version -notmatch '^\d+\.\d+\.\d+(-[A-Za-z0-9.-]+)?$') { throw 'Usage: agent.ps1 version <major.minor.patch>' }
    $info = Get-ModInfo
    if (-not $info) { throw 'No mod found.' }
    $old = $info.version -replace '-.*$', ''; $new = $Version -replace '-.*$', ''
    if (-not $Force -and $old -match '^\d+\.\d+\.\d+$' -and [version]$new -lt [version]$old) { throw "$Version is lower than the current $($info.version); versions only go up (a typo?). Pass -Force to go back on purpose." }
    $utf8 = New-Object Text.UTF8Encoding($false)
    $changes = @()
    $buildPath = Join-Path $root 'build.gradle'
    $build = [IO.File]::ReadAllText($buildPath)
    if ($build -notmatch "(?m)^version = '[^']*'") { throw "build.gradle has no line version = '...'" }
    [IO.File]::WriteAllText($buildPath, ([regex]::Replace($build, "(?m)^version = '[^']*'", "version = '$Version'")), $utf8); $changes += 'build.gradle'
    $mainPath = Join-Path $root $info.entry
    $main = [IO.File]::ReadAllText($mainPath)
    if ($main -match 'VERSION\s*=\s*"[^"]*"') { $main = [regex]::Replace($main, 'VERSION\s*=\s*"[^"]*"', "VERSION = `"$Version`"", 1) }
    elseif ($main -match 'version\s*=\s*"[^"]*"') { $main = [regex]::Replace($main, 'version\s*=\s*"[^"]*"', "version = `"$Version`"", 1) }
    else { throw "No version in $($info.entry)" }
    [IO.File]::WriteAllText($mainPath, $main, $utf8); $changes += $info.entry
    $mcmod = Join-Path $root 'src/main/resources/mcmod.info'
    if ([IO.File]::Exists($mcmod)) {
        [IO.File]::WriteAllText($mcmod, ([regex]::Replace([IO.File]::ReadAllText($mcmod), '"version"\s*:\s*"[^"]*"', "`"version`": `"$Version`"")), $utf8); $changes += 'src/main/resources/mcmod.info'
    }
    "version $($info.version) -> $Version in " + ($changes -join ', ')
}

# PowerShell 5.1 Set-Content/Out-File write a UTF-8 BOM, which javac rejects and which breaks the
# first key of a lang file. Strip it from source/resource text files before building.
function Remove-KitBom {
    $fixed = @()
    $src = Join-Path $root 'src'
    if (-not [IO.Directory]::Exists($src)) { return $fixed }
    foreach ($file in [IO.Directory]::GetFiles($src, '*', [IO.SearchOption]::AllDirectories)) {
        if ($file -notmatch '\.(java|json|lang|info|mcmeta|txt|cfg)$') { continue }
        $bytes = [IO.File]::ReadAllBytes($file)
        if ($bytes.Length -ge 3 -and $bytes[0] -eq 0xEF -and $bytes[1] -eq 0xBB -and $bytes[2] -eq 0xBF) {
            $rest = New-Object byte[] ($bytes.Length - 3)
            [Array]::Copy($bytes, 3, $rest, 0, $rest.Length)
            [IO.File]::WriteAllBytes($file, $rest)
            $fixed += (Get-Relative $file)
        }
    }
    return $fixed
}

# One agent.ps1 command line -> arguments ("quoted values" and 'quoted values' keep spaces).
function Split-KitCommandLine([string]$Line) {
    @([regex]::Matches($Line, '"[^"]*"|''[^'']*''|\S+') | ForEach-Object { $v = $_.Value; if ($v.Length -ge 2 -and ($v[0] -eq '"' -or $v[0] -eq "'") -and $v[-1] -eq $v[0]) { $v.Substring(1, $v.Length - 2) } else { $v } })
}

# ---- main --------------------------------------------------------------------------------

$exitCode = 0
$lock = $null
$logStart = [DateTime]::UtcNow
$result = $null
try {
    switch ($Action) {
        'check' {
            $selected = if ($Profile) { $Profile } elseif ($Target) { $Target } else { 'build' }
            if ($selected -in @('mod', 'all')) { $selected = 'build' }
            if ($selected -notin @('environment', 'build', 'unit', 'server', 'game')) { throw "Unknown profile '$selected'. Use build, server, game, unit or environment." }
            $lock = Enter-KitLock 0
            $bomFixed = @(Remove-KitBom)
            # The first build on a PC downloads and decompiles Minecraft/Forge (10-30 min on a laptop); allow for it.
            $gradleHome = if ($env:GRADLE_USER_HOME) { $env:GRADLE_USER_HOME } else { Join-Path $env:USERPROFILE '.gradle' }
            if (-not [IO.File]::Exists((Join-Path $gradleHome 'caches/minecraftforge/forgegradle/mavenizer/caches/forge/net/minecraftforge/forge/1.12.2-14.23.5.2859/stable/39-1.12/remapped-javadoc.jar'))) {
                if (-not $PSBoundParameters.ContainsKey('TimeoutSeconds')) { $TimeoutSeconds = 3600 }
                [Console]::Error.WriteLine('First build on this PC: downloading and decompiling Minecraft and Forge. This takes 10-30 minutes once; later checks take seconds.')
            }
            # A first build downloads ~1 GB from Gradle, Mojang and Forge servers; one network failure is retried.
            for ($attempt = 1; ; $attempt++) {
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
            if (-not $result.networkError -or $attempt -ge 2) { break }
            [Console]::Error.WriteLine('Network download failed; retrying once. ' + $result.message)
            }
            $result | Add-Member -NotePropertyName projectRoot -NotePropertyValue $root
            # Client-only calls compile but throw NoSuchMethodError on a server: report them once the build compiles.
            if ($selected -in @('build', 'server', 'game') -and $result.gradleExitCode -eq 0 -or $result.harness) {
                $clientCalls = @(Get-KitClientOnlyCalls $root)
                if ($clientCalls.Count) {
                    $result | Add-Member -NotePropertyName clientOnlyCalls -NotePropertyValue $clientCalls
                    if ($result.status -eq 'PASS' -and @($clientCalls | Where-Object { $_ -like 'ERROR *' }).Count) {
                        $result.status = 'FAIL'; $result.exitCode = 1; $exitCode = 1; $result.message = 'Game tests call client-only members.'
                    }
                }
            }
            $result | Add-Member -NotePropertyName runId -NotePropertyValue ([IO.Path]::GetFileNameWithoutExtension($result.report))
            $result | Add-Member -NotePropertyName problems -NotePropertyValue @(Get-Problems $result)
            $result | Add-Member -NotePropertyName repeatedFailures -NotePropertyValue (Update-FailureStreak $result)
            $result | Add-Member -NotePropertyName flakyTests -NotePropertyValue @(Update-TestHistory $result)
            $resultJson = $result | ConvertTo-Json -Depth 8 -Compress
            Set-Content -LiteralPath $result.report -Value $resultJson -Encoding UTF8
            Set-Content -LiteralPath (Join-Path $reportRoot 'latest.json') -Value $resultJson -Encoding UTF8
            Set-Content -LiteralPath (Join-Path $reportRoot ($selected + '-latest.json')) -Value $resultJson -Encoding UTF8
            if ($Json) { $resultJson } else {
                Format-Check $result
                $taskFile = Join-Path $root 'TASK.md'
                if ($selected -eq 'game' -and [IO.File]::Exists($taskFile) -and [IO.File]::ReadAllText($taskFile).Contains("request, in their words)")) { 'note: TASK.md is still the placeholder: write the request and decisions there (they outlast your context).' }
                if ($bomFixed.Count) { 'note: removed a UTF-8 byte-order mark from ' + ($bomFixed -join ', ') + '. Write files with your edit tool or [IO.File]::WriteAllText, not Set-Content/Out-File.' }
            }
        }
        'status' {
            $info = Get-ModInfo
            $last = Read-Result
            $java = if (-not $javaSelection) { 'unknown' } elseif ($javaSelection.gradle -and $javaSelection.java8) { 'ok (JDK ' + $javaSelection.gradle.major + ' for Gradle, JDK 8 for mods)' } else { 'MISSING - run Setup.cmd (see SETUP.md)' }
            $stale = $false
            if ($last) { $stale = (Get-NewestSourceTime) -gt [IO.File]::GetLastWriteTimeUtc($last.report) }
            $task = if ([IO.File]::Exists((Join-Path $root 'TASK.md'))) { [IO.File]::ReadAllText((Join-Path $root 'TASK.md')) } else { '' }
            $taskPlaceholder = $task.Contains("request, in their words)")
            # Test scaffolds from `new test` still contain a placeholder check until someone writes the real one.
            $scaffolds = @()
            $gametest = Join-Path $root 'src/gametest'
            if ([IO.Directory]::Exists($gametest)) {
                foreach ($file in [IO.Directory]::GetFiles($gametest, '*.java', [IO.SearchOption]::AllDirectories)) { if ([IO.File]::ReadAllText($file).Contains('KIT-SCAFFOLD')) { $scaffolds += [IO.Path]::GetFileName($file) } }
            }
            $next = if ($javaSelection -and -not ($javaSelection.gradle -and $javaSelection.java8)) { 'Run Setup.cmd (SETUP.md).' }
                elseif (-not $info) { 'No mod yet: agent.ps1 init <modid> "<Name>" (creates a working skeleton).' }
                elseif (-not $last -or $stale) { 'Run agent.ps1 check (then check server before finishing).' }
                elseif ($last.status -ne 'PASS') { 'Fix the last failure (agent.ps1 errors), then rerun agent.ps1 check ' + $last.profile + '.' }
                elseif ($scaffolds.Count) { 'Fill in the placeholder check in ' + ($scaffolds -join ', ') + ', then agent.ps1 check game.' }
                elseif ($taskPlaceholder) { 'Write the request, decisions and acceptance checks into TASK.md (AGENTS.md step 2).' }
                else { 'Continue TASK.md.' }
            if ($Json) {
                [ordered]@{ schemaVersion = 1; action = 'status'; projectRoot = $root; mod = $info; java = $javaSelection; lastRun = if ($last) { $last | Select-Object runId, profile, status, message, log } else { $null }; lastRunStale = $stale; task = $task; nextAction = $next } | ConvertTo-Json -Depth 6 -Compress
            } else {
                'project: ' + $root
                'mod: ' + $(if ($info) { '{0} {1} ({2})' -f $info.modId, $info.version, $info.entry } else { 'none' })
                'java: ' + $java
                'last check: ' + $(if ($last) { '{0} {1} {2}{3}' -f $last.status, $last.profile, ([IO.File]::GetLastWriteTime($last.report).ToString('yyyy-MM-dd HH:mm')), $(if ($stale) { ' (source changed since)' } else { '' }) } else { 'none' })
                if ($scaffolds.Count) { 'tests: placeholder check not filled in: ' + ($scaffolds -join ', ') }
                if ($taskPlaceholder) { 'task: TASK.md is still the placeholder' }
                'next: ' + $next
                # Gradle/Java fail with "Unable to access jarfile" once nested paths pass Windows' 260-character limit.
                if ($root.Length -gt 150) { "warning: project path is $($root.Length) characters; builds can fail past Windows' 260-character limit. Move the folder somewhere short (e.g. C:\mods\mykit)." }
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
            $split = ConvertTo-Splat $Rest
            $named = $split.named; $named['ModId'] = $Target
            if ($split.positional.Count) { $named['Name'] = $split.positional[0] }
            $lock = Enter-KitLock 60
            & (Join-Path $root 'tools/Initialize-Mod.ps1') @named
            $exitCode = $LASTEXITCODE
        }
        'new' {
            $split = ConvertTo-Splat $Rest
            $named = $split.named; $named['Kind'] = $Target
            if ($split.positional.Count) { $named['Name'] = $split.positional[0] }
            $lock = Enter-KitLock 60
            & (Join-Path $root 'tools/New-Content.ps1') @named
            $exitCode = $LASTEXITCODE
        }
        'version' { Set-ModVersion $Target (@($Rest) -contains '-Force') }
        'batch' {
            # .NET resolves relative paths against the process directory, not the PowerShell location.
            $batchFile = if ($Target -and -not [IO.Path]::IsPathRooted($Target)) { Join-Path (Get-Location).Path $Target } else { $Target }
            if (-not $batchFile -or -not [IO.File]::Exists($batchFile)) { throw 'Usage: agent.ps1 batch <file>  (one agent.ps1 command per line, e.g. new item ruby; # starts a comment)' }
            $lineNumber = 0; $ran = 0
            foreach ($raw in [IO.File]::ReadAllLines($batchFile)) {
                $lineNumber++
                $line = $raw.Trim()
                if (-not $line -or $line.StartsWith('#')) { continue }
                $line = $line -replace '^(\./)?agent\.ps1\s+', ''
                $tokens = @(Split-KitCommandLine $line)
                if ($tokens[0] -eq 'batch') { throw "line ${lineNumber}: batch files cannot run batch" }
                '> ' + $line
                $ErrorActionPreference = 'Continue'
                $output = & (Get-Process -Id $PID).Path -NoProfile -ExecutionPolicy Bypass -File $PSCommandPath @tokens 2>$null
                $code = $LASTEXITCODE
                $ErrorActionPreference = 'Stop'
                $output
                $ran++
                if ($code -ne 0) { "stopped at line $lineNumber of $Target after $($ran - 1) successful command(s); fix that line, then run the rest"; $exitCode = 1; break }
            }
            if ($exitCode -eq 0) { "batch done: $ran command(s)" }
        }
        'export' {
            & (Join-Path $root 'tools/Export-Release.ps1') -Json:$Json
            $exitCode = $LASTEXITCODE
        }
    }
} catch {
    $exitCode = 1
    if ($Json) { [ordered]@{ schemaVersion = 1; projectRoot = $root; action = $Action; status = 'FAIL'; exitCode = 1; message = $_.Exception.Message } | ConvertTo-Json -Compress }
    else { 'FAIL: ' + $_.Exception.Message; if ($env:KIT_DEBUG) { $_.ScriptStackTrace } }
} finally {
    if ($null -ne $lock) { $lock.Dispose() }
    # Usage log: one JSON line per call, so a long unattended run can be reviewed afterwards
    # (qa tooling or a person reads reports/agent-log.jsonl; check details are in the run's report).
    try {
        $named = @($PSBoundParameters.Keys | Where-Object { $_ -notin @('Action', 'Target', 'Rest') } | ForEach-Object { $v = $PSBoundParameters[$_]; if ($v -is [switch] -or $v -is [bool]) { "-$_" } else { "-$_ $v" } })
        $entry = [ordered]@{
            time = $logStart.ToString('o'); seconds = [Math]::Round(([DateTime]::UtcNow - $logStart).TotalSeconds, 1)
            action = $Action; target = $Target; args = (@(@($Rest | Where-Object { $null -ne $_ } | ForEach-Object { if ($_ -is [array]) { $_ -join ',' } else { "$_" } }) + $named) -join ' ')
            exit = [int]$exitCode
        }
        if ($result -and $result.runId) { $entry.runId = $result.runId; $entry.status = $result.status; $entry.repeatedFailures = $result.repeatedFailures }
        New-Item -ItemType Directory -Force -Path (Join-Path $root 'reports') | Out-Null
        [IO.File]::AppendAllText((Join-Path $root 'reports/agent-log.jsonl'), ($entry | ConvertTo-Json -Compress) + "`n", (New-Object Text.UTF8Encoding($false)))
    } catch { }
}
exit $exitCode
