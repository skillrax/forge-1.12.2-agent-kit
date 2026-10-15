# Test the runner's automation contract using fake Gradle processes, not Minecraft.
[CmdletBinding()]
param()
$ErrorActionPreference = 'Stop'
[Console]::OutputEncoding = New-Object Text.UTF8Encoding($false)
$OutputEncoding = [Console]::OutputEncoding
$authoringRoot = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$projectRoot = Join-Path $authoringRoot 'kit'
$fixtureRoot = Join-Path $authoringRoot ('.work/runner-fixtures/' + [Guid]::NewGuid().ToString('N'))
$fixtureTesting = Join-Path $fixtureRoot 'testing'
New-Item -ItemType Directory -Force -Path $fixtureTesting | Out-Null
Copy-Item -LiteralPath (Join-Path $projectRoot 'testing/Test-DevEnvironment.ps1') -Destination $fixtureTesting
$runner = Join-Path $fixtureTesting 'Test-DevEnvironment.ps1'
$wrapper = Join-Path $fixtureRoot 'gradlew.bat'
$cases = New-Object 'System.Collections.Generic.List[object]'
$shell = (Get-Process -Id $PID).Path
function Test-Case {
    param([string]$Name, [string]$Body, [string]$Profile, [int]$ExpectedExit)
    Set-Content -LiteralPath $wrapper -Value ("@echo off`r`n" + $Body) -Encoding ASCII
    $arguments = @('-NoProfile', '-File', $runner, '-Profile', $Profile, '-TimeoutSeconds', '10')
    $output = & $shell @arguments
    $actualExit = $LASTEXITCODE
    $result = ($output -join "`n") | ConvertFrom-Json
    if ($actualExit -ne $ExpectedExit -or $result.exitCode -ne $ExpectedExit) {
        throw "$Name expected exit $ExpectedExit, got $actualExit. $($result.message)"
    }
    if ($ExpectedExit -eq 0 -and $result.status -ne 'PASS') { throw "$Name did not report PASS." }
    if ($ExpectedExit -ne 0 -and $result.status -ne 'FAIL') { throw "$Name did not report FAIL." }
    if ($Name -eq 'server readiness and stop' -and -not $result.serverReady) { throw 'Readiness marker not detected.' }
    if ($result.log -and -not (Test-Path -LiteralPath $result.log)) { throw 'Log was not saved.' }
    if (-not (Test-Path -LiteralPath $result.report)) { throw 'JSON report was not saved.' }
    $cases.Add([ordered]@{ name = $Name; status = 'PASS'; expectedExit = $ExpectedExit })
}
try {
    Test-Case -Name 'successful build' -Body 'exit /b 0' -Profile all -ExpectedExit 0
    Test-Case -Name 'Gradle failure' -Body "echo ^> Deliberate fixture failure.`r`nexit /b 1" -Profile build -ExpectedExit 1
    Test-Case -Name 'early server exit rejected' -Body 'exit /b 0' -Profile server -ExpectedExit 1
    $server = @'
echo Done ^(1.0s^)!
set /p stopCommand=
if "%stopCommand%"=="stop" exit /b 0
exit /b 1
'@
    Test-Case -Name 'server readiness and stop' -Body $server -Profile server -ExpectedExit 0
    $crashed = $server.Replace('set /p stopCommand=', "echo Encountered an unexpected exception`r`nset /p stopCommand=")
    Test-Case -Name 'fatal exception after readiness rejected' -Body $crashed -Profile server -ExpectedExit 1
    Test-Case -Name 'timeout' -Body "ping -n 31 127.0.0.1 >nul`r`nexit /b 0" -Profile build -ExpectedExit 2
    [ordered]@{ schemaVersion = 1; status = 'PASS'; cases = $cases; fixtures = $fixtureRoot } | ConvertTo-Json -Depth 4 -Compress
    exit 0
} catch {
    [ordered]@{ schemaVersion = 1; status = 'FAIL'; message = $_.Exception.Message; cases = $cases; fixtures = $fixtureRoot } | ConvertTo-Json -Depth 4 -Compress
    exit 1
}


