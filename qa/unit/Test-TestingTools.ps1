# Runner contract with a fake Gradle (no Minecraft): exit codes, server/harness handshake, harness results, timeout.
[CmdletBinding()]
param()
$ErrorActionPreference = 'Stop'
[Console]::OutputEncoding = New-Object Text.UTF8Encoding($false)
$authoringRoot = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$projectRoot = Join-Path $authoringRoot 'kit'
$fixtureRoot = Join-Path $authoringRoot ('.work/runner-fixtures/' + [Guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Force -Path (Join-Path $fixtureRoot 'testing') | Out-Null
Copy-Item -LiteralPath (Join-Path $projectRoot 'testing/Test-DevEnvironment.ps1') -Destination (Join-Path $fixtureRoot 'testing')
$runner = Join-Path $fixtureRoot 'testing/Test-DevEnvironment.ps1'
# gradlew.bat forwards to fake.ps1, which acts according to $env:FAKE_MODE.
Set-Content -LiteralPath (Join-Path $fixtureRoot 'gradlew.bat') -Encoding ASCII -Value "@echo off`r`npowershell -NoProfile -File `"%~dp0fake.ps1`" %*`r`nexit /b %errorlevel%"
Set-Content -LiteralPath (Join-Path $fixtureRoot 'fake.ps1') -Encoding UTF8 -Value @'
$out = ($args | Where-Object { $_ -like '-PkitCheckOut=*' } | Select-Object -First 1) -replace '^-PkitCheckOut=', ''
switch ($env:FAKE_MODE) {
    'ok' { exit 0 }
    'fail' { '> Deliberate fixture failure.'; exit 1 }
    'early' { exit 0 }
    'timeout' { Start-Sleep -Seconds 30; exit 0 }
    default {
        'Done (1.0s)! For help, type "help"'
        if ($env:FAKE_MODE -eq 'crash') { 'Encountered an unexpected exception' }
        if ($env:FAKE_MODE -eq 'recipe-error') { '[12:00:00] [Server thread/ERROR] [FML]: Parsing error loading recipe demo:bad'; 'com.google.gson.JsonSyntaxException: Unknown item ''demo:missing'''; '	at net.minecraftforge.common.crafting.CraftingHelper.getItemStack(CraftingHelper.java:1)' }
        $report = @{ registered = @{ items = 1 }; lint = @(); tests = @() }
        if ($env:FAKE_MODE -eq 'lint-error') { $report.lint = @(@{ level = 'ERROR'; subject = 'block x:y'; message = 'tile entity not registered' }) }
        if ($env:FAKE_MODE -eq 'test-fail') { $report.tests = @(@{ id = 'a'; status = 'PASS' }, @{ id = 'b'; status = 'FAIL'; phase = 'p'; message = 'm' }) }
        New-Item -ItemType Directory -Force -Path (Split-Path -Parent $out) | Out-Null
        [IO.File]::WriteAllText($out, ($report | ConvertTo-Json -Depth 5))
        'KIT_HARNESS_DONE'
        exit 0
    }
}
'@
$cases = New-Object 'System.Collections.Generic.List[string]'
$shell = (Get-Process -Id $PID).Path
function Test-Case([string]$Name, [string]$Mode, [string]$Profile, [int]$ExpectedExit, [string]$MessagePattern, [string]$OutputPattern) {
    $env:FAKE_MODE = $Mode
    $ErrorActionPreference = 'Continue'   # PowerShell 5.1 turns native stderr (progress lines) into errors
    $output = & $shell -NoProfile -File $runner -Profile $Profile -TimeoutSeconds 10 2>$null
    $ErrorActionPreference = 'Stop'
    $actualExit = $LASTEXITCODE
    $result = ($output -join "`n") | ConvertFrom-Json
    if ($actualExit -ne $ExpectedExit -or $result.exitCode -ne $ExpectedExit) { throw "$Name expected exit $ExpectedExit, got $actualExit. $($result.message)" }
    if ($result.status -ne $(if ($ExpectedExit -eq 0) { 'PASS' } else { 'FAIL' })) { throw "$Name reported $($result.status)" }
    if ($MessagePattern -and $result.message -notmatch $MessagePattern) { throw "$Name message: $($result.message)" }
    if ($OutputPattern -and ($output -join "`n") -notmatch $OutputPattern) { throw "$Name output: $($output -join ' ')" }
    if (-not (Test-Path -LiteralPath $result.report)) { throw "$Name saved no report" }
    $cases.Add($Name)
}
try {
    Test-Case 'build passes' ok build 0
    Test-Case 'Gradle failure' fail build 1 'Deliberate fixture failure'
    Test-Case 'server exit before startup' early server 1 'before finishing startup'
    Test-Case 'server + harness' server server 0 'linted'
    Test-Case 'fatal exception' crash server 1 'fatal'
    Test-Case 'lint ERROR fails' lint-error server 1 'content error'
    Test-Case 'broken recipe fails' recipe-error server 1 'content error' 'recipe demo:bad.*Unknown item'
    Test-Case 'failed game test fails' test-fail game 1 '1 of 2 game tests failed'
    Test-Case 'timeout' timeout build 2 'Timeout'
    "PASS: $($cases.Count) runner cases ($($cases -join ', '))"
    exit 0
} catch {
    "FAIL: $($_.Exception.Message) (passed: $($cases -join ', '); fixtures: $fixtureRoot)"
    exit 1
} finally { Remove-Item Env:FAKE_MODE -ErrorAction SilentlyContinue }
