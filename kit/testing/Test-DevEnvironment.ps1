# Windows PowerShell 5.1+ and PowerShell 7+. Invoke from any directory.
[CmdletBinding()]
param(
    [ValidateSet('environment', 'build', 'unit', 'server', 'game', 'client')]
    [string] $Profile = 'build',
    [ValidatePattern('^[a-z0-9*,-]*$')][string]$Test = '*',
    [switch]$Clean,
    [ValidateRange(10, 7200)]
    [int] $TimeoutSeconds = 900
)
$ErrorActionPreference = 'Stop'
[Console]::OutputEncoding = New-Object Text.UTF8Encoding($false)
$OutputEncoding = [Console]::OutputEncoding
if (-not $env:PATHEXT) { $env:PATHEXT = '.COM;.EXE;.BAT;.CMD' }
if (-not [IO.File]::Exists($env:ComSpec)) {
    $env:ComSpec = Join-Path ([Environment]::GetFolderPath('System')) 'cmd.exe'
}
$projectRoot = Split-Path -Parent $PSScriptRoot
$reportRoot = Join-Path $projectRoot 'reports/testing-tools'
$runId = [DateTime]::UtcNow.ToString('yyyyMMddTHHmmssfffZ') + '-' + [Guid]::NewGuid().ToString('N').Substring(0, 8)
New-Item -ItemType Directory -Force -Path $reportRoot | Out-Null
$logPath = Join-Path $reportRoot ($runId + '.log')
$resultPath = Join-Path $reportRoot ($runId + '.json')
$watch = [Diagnostics.Stopwatch]::StartNew()
$exitCode = 1
$reason = 'Check failed.'
$serverReady = $false
$serverFatalError = $false
$networkError = $false
$stopSent = $false
$process = $null
$processStarted = $false
$writer = $null
$gradleExit = $null
$harnessDone = $false
$doneAt = $null
$harnessPath = $null
$usesServer = $Profile -in @('server', 'game')
$usesClient = $Profile -eq 'client'
$clientCrash = $false
$progressAt=0;$observedStage='starting Gradle wrapper';$lastDetail='';$lastOutputAt=0
try {
    $sourcePaths=@(Get-ChildItem -LiteralPath (Join-Path $projectRoot 'src') -Recurse -File -ErrorAction SilentlyContinue | ForEach-Object {$_.FullName.Substring($projectRoot.Length)} | Sort-Object)
    $buildInputs=@('build.gradle','settings.gradle','gradle.properties','testing/testing.gradle','testing/Test-DevEnvironment.ps1','gradle/wrapper/gradle-wrapper.properties')
    $buildIdentity=($buildInputs | ForEach-Object {$p=Join-Path $projectRoot $_;if(Test-Path -LiteralPath $p){$_+':'+(Get-FileHash -LiteralPath $p -Algorithm SHA256).Hash}}) -join '|'
    $previousPath=Join-Path $reportRoot ($Profile+'-latest.json')
    $previous=if(Test-Path -LiteralPath $previousPath){Get-Content -LiteralPath $previousPath -Raw -Encoding UTF8 | ConvertFrom-Json}else{$null}
    $cleanReason=if($Clean){'requested'}elseif(-not $previous -or $previous.status -ne 'PASS'){'no successful baseline'}elseif($previous.buildIdentity -ne $buildIdentity){'build configuration changed'}elseif(@($previous.sourcePaths | Where-Object {$_ -notin $sourcePaths}).Count){'source/resource path removed'}else{$null}
    $hasMod = @(Get-ChildItem -LiteralPath (Join-Path $projectRoot 'src/main/java') -Filter '*.java' -Recurse -File -ErrorAction SilentlyContinue).Count -gt 0
    $requireMod = if ($hasMod) { ' -PrequireMod=true' } else { '' }
    $taskArgs = switch ($Profile) {
        'environment' { 'verifyEnvironment --rerun-tasks' }
        'build' { 'verifyAll' + $requireMod }
        'unit' { 'cleanTest verifyUnitTests --rerun-tasks' }
        'client' {
            # A real client (visible window) with the client check mod: models, textures, screenshots, then it quits.
            $clientDir = Join-Path $projectRoot ('run/testing-client/' + $runId)
            New-Item -ItemType Directory -Force -Path $clientDir | Out-Null
            Set-Content -LiteralPath (Join-Path $clientDir 'options.txt') -Encoding ASCII -Value @('tutorialStep:none', 'narrator:0', 'pauseOnLostFocus:false', 'renderDistance:4', 'soundCategory_master:0.0')
            $harnessPath = Join-Path $clientDir 'kit-client.json'
            'verifyModJar' + $requireMod + ' runClient "-PtestingToolsClientDir=' + $clientDir + '" "-PkitClientOut=' + $harnessPath + '"'
        }
        default {
            # server/game: build + jar inspection, then a fresh flat-world dev server with the check harness.
            $serverDir = Join-Path $projectRoot ('run/testing-server/' + $runId)
            New-Item -ItemType Directory -Force -Path $serverDir | Out-Null
            Set-Content -LiteralPath (Join-Path $serverDir 'eula.txt') -Value 'eula=true' -Encoding ASCII
            Set-Content -LiteralPath (Join-Path $serverDir 'server.properties') -Encoding ASCII -Value @(
                'server-ip=127.0.0.1', 'server-port=0', 'level-type=FLAT', 'generate-structures=false', 'spawn-monsters=false', 'spawn-animals=true'
            )
            $harnessPath = Join-Path $serverDir 'kit-check.json'
            $gameTests = if ($Profile -eq 'game') { ' "-PkitGameTests=' + $Test + '"' } else { '' }
            'verifyModJar' + $requireMod + ' runServer "-PtestingToolsServerDir=' + $serverDir + '" "-PkitCheckOut=' + $harnessPath + '"' + $gameTests
        }
    }
    if($Profile -in @('build','server','game','client') -and $cleanReason){$taskArgs='clean '+$taskArgs}
    # One Gradle run with live progress; dot-sourced so its flags land in this scope. A second run reuses it.
    $runGradle = {
        $start = New-Object Diagnostics.ProcessStartInfo
        $start.FileName = $env:ComSpec
        $start.Arguments = '/d /s /c ""' + (Join-Path $projectRoot 'gradlew.bat') + '" ' + $taskArgs + ' --console=plain --no-daemon"'
        $start.WorkingDirectory = $projectRoot
        $start.UseShellExecute = $false
        $start.CreateNoWindow = $true
        $start.RedirectStandardOutput = $true
        $start.RedirectStandardError = $true
        $start.RedirectStandardInput = $true
        $process = New-Object Diagnostics.Process
        $process.StartInfo = $start
        $writer = New-Object IO.StreamWriter($logPath, $appendLog, (New-Object Text.UTF8Encoding($false)))
        $writer.AutoFlush = $true
        $writer.WriteLine('PROFILE: ' + $Profile + '; TASKS: ' + $taskArgs)
        if (-not $process.Start()) { throw 'Could not start the Gradle wrapper.' }
        $processStarted = $true
        [Console]::Error.WriteLine('Starting '+$Profile+' check. Full log: '+$logPath)
        $reads = @($process.StandardOutput.ReadLineAsync(), $process.StandardError.ReadLineAsync())
        $streams = @($process.StandardOutput, $process.StandardError)
        $ended = @($false, $false)
        while (-not ($process.HasExited -and $ended[0] -and $ended[1])) {
            for ($index = 0; $index -lt 2; $index++) {
                if (-not $ended[$index] -and $reads[$index].IsCompleted) {
                    $line = $reads[$index].GetAwaiter().GetResult()
                    if ($null -eq $line) { $ended[$index] = $true }
                    else {
                        $writer.WriteLine($line)
                        $lastOutputAt=$watch.Elapsed.TotalSeconds
                        # Latest meaningful line (Mavenizer steps, downloads), shown with progress so long steps are visible.
                        $trimmed=$line.Trim()
                        # Progress shows the stage and a recent normal line; exceptions (often FML's harmless dev-jar scan noise) are reported at the end.
                        if($trimmed -and $trimmed -notmatch '^(at |\.\.\.|Cache miss|\.+\d+%|Caused by|[\w.$]+(Exception|Error))' -and $trimmed -notmatch 'Exception|Unable to read a class file|problem reading the entry|failed to read properly' -and $trimmed -ne $observedStage){$lastDetail=$trimmed.Substring(0,[Math]::Min(110,$trimmed.Length))}
                        if($line -match '^> Task |^> Configure |^Downloading |^Fetching distribution|^Processing Minecraft dependency:'){$observedStage=$line.Substring(0,[Math]::Min(150,$line.Length));$lastDetail=''}
                        if ($usesServer -and $line -match 'Encountered an unexpected exception|Exception in server tick loop|Caught exception from|[A-Z0-9_]+_ASSERTIONS_FAIL:') {
                            $serverFatalError = $true
                        }
                        if ($usesServer -and $line -match 'Done \([0-9.,]+s\)!') { $serverReady = $true; $doneAt = $watch.Elapsed.TotalSeconds }
                        # The harness lints, runs requested game tests, writes its report, then stops the server itself.
                        if ($usesServer -and $line -match 'KIT_HARNESS_DONE') { $harnessDone = $true; $stopSent = $true }
                        if ($usesClient -and $line -match 'KIT_CLIENT_DONE') { $harnessDone = $true }
                        if ($usesClient -and $line -match '---- Minecraft Crash Report ----|Reported exception thrown!') { $clientCrash = $true }
                        $reads[$index] = $streams[$index].ReadLineAsync()
                    }
                }
            }
            if ($Profile -eq 'server' -and $doneAt -and -not $stopSent -and $watch.Elapsed.TotalSeconds -ge ($doneAt + 60) -and -not $process.HasExited) {
                # Harness missing or stuck: stop normally so the run still ends cleanly.
                $process.StandardInput.WriteLine('stop'); $process.StandardInput.Flush(); $stopSent = $true
            }
            if($watch.Elapsed.TotalSeconds -ge ($progressAt+15)){
                $quiet=[int]($watch.Elapsed.TotalSeconds-$lastOutputAt)
                $detail=$(if($lastDetail){' | '+$lastDetail}else{''})+$(if($quiet -ge 60){' (working; no new output for '+$quiet+'s)'}else{''})
                [Console]::Error.WriteLine(('[{0}s] {1}: {2}{3}' -f [int]$watch.Elapsed.TotalSeconds,$Profile,$observedStage,$detail))
                $progressAt=$watch.Elapsed.TotalSeconds
            }
            if ($watch.Elapsed.TotalSeconds -ge $TimeoutSeconds) {
                $exitCode = 2
                throw "Timeout after $TimeoutSeconds seconds. The wrapper process tree will be terminated."
            }
            Start-Sleep -Milliseconds 20
        }
        $process.WaitForExit()
        $gradleExit = $process.ExitCode
        if ($gradleExit -ne 0) { throw "Gradle exited with code $gradleExit. Read the saved log." }
        if ($usesServer -and $serverFatalError) {
            throw 'Server reported a fatal startup/runtime exception.'
        }
        if ($usesServer -and -not $serverReady) {
            throw 'Server exited before finishing startup.'
        }
        if ($usesServer -and -not $harnessDone) {
            throw 'The check harness did not report; see the log.'
        }
        if ($usesClient -and ($clientCrash -or -not $harnessDone)) {
            throw $(if ($clientCrash) { 'The client crashed; the crash report is in the log.' } else { 'The client check did not report (window closed early, no display, or a startup error); see the log.' })
        }
    }
    $appendLog = $false
    . $runGradle
    # Tests with afterRestart(): start the server again on the saved world, then fold those results in.
    if ($Profile -eq 'game' -and $harnessPath -and [IO.File]::Exists($harnessPath)) {
        $first = [IO.File]::ReadAllText($harnessPath, [Text.Encoding]::UTF8) | ConvertFrom-Json
        $restartIds = @($first.restart | Where-Object { $_ } | ForEach-Object { $_.id })
        if ($restartIds.Count) {
            $process.Dispose(); $process = $null; $processStarted = $false; $writer.Dispose(); $writer = $null
            [Console]::Error.WriteLine('Restarting the server on the same world for ' + $restartIds.Count + ' afterRestart test(s).')
            $restartPath = Join-Path $serverDir 'kit-check-restart.json'
            $taskArgs = 'runServer "-PtestingToolsServerDir=' + $serverDir + '" "-PkitCheckOut=' + $restartPath + '" "-PkitRestartFrom=' + $harnessPath + '"'
            $serverReady = $false; $harnessDone = $false; $stopSent = $false; $serverFatalError = $false; $doneAt = $null
            $appendLog = $true
            . $runGradle
            $second = [IO.File]::ReadAllText($restartPath, [Text.Encoding]::UTF8) | ConvertFrom-Json
            $merged = @()
            foreach ($entry in @($first.tests)) {
                $after = @($second.tests | Where-Object { $_.id -eq $entry.id }) | Select-Object -First 1
                if ($entry.id -in $restartIds -and -not $after) { $after = [pscustomobject]@{ id = $entry.id; status = 'FAIL'; phase = 'after restart'; assertions = 0; ticks = 0; message = 'no result after the restart (see the log)' } }
                if ($after -and $after.status -eq 'PASS') {
                    # Both halves passed: one result counting the checks of both.
                    $after = [pscustomobject]@{ id = $entry.id; status = 'PASS'; phase = $after.phase; assertions = [int]$entry.assertions + [int]$after.assertions; ticks = [int]$entry.ticks + [int]$after.ticks; message = 'ok'; restart = $true }
                }
                $merged += $(if ($after) { $after } else { $entry })
            }
            $first.tests = $merged
            [IO.File]::WriteAllText($harnessPath, ($first | ConvertTo-Json -Depth 12), (New-Object Text.UTF8Encoding($false)))
        }
    }
    $exitCode = 0
    $reason = switch ($Profile) {
        'server' { 'Server started, content linted, clean shutdown.' }
        'game' { 'Game tests ran.' }
        'client' { 'Client started, models, textures and GUI container sync checked, screenshots taken.' }
        default { 'Gradle checks passed.' }
    }
}
catch { $reason = $_.Exception.Message }
finally {
    if ($null -ne $process -and $processStarted) {
        try {
            if (-not $process.HasExited) {
                & "$env:SystemRoot\System32\taskkill.exe" /PID $process.Id /T /F 2>&1 | Out-Null
                $process.WaitForExit(5000) | Out-Null
            }
        } catch { $reason += ' Process cleanup failed: ' + $_.Exception.Message }
        $process.Dispose()
    }
    elseif ($null -ne $process) { $process.Dispose() }
    if ($null -ne $writer) { $writer.Dispose() }
    $watch.Stop()
}
$unitTestCount = $null
$modEntryPoints = $null
$classCount = $null
$modVersion = $null
$inspectedArtifact=$null
if (Test-Path -LiteralPath $logPath) {
    $logText = Get-Content -Encoding UTF8 -LiteralPath $logPath -Raw
    if ($gradleExit -ne 0 -and $null -ne $gradleExit) {
        $detail = [regex]::Match($logText, '(?m)^> (?!Task |Configure |Run with|Get more)(.+)$')
        if ($detail.Success) { $reason = $detail.Groups[1].Value.Trim() }
        # First builds download Gradle, Minecraft and Forge; a slow or blocked connection fails inside a tool
        # process, and Gradle then reports only "java.exe finished with non-zero exit value 1".
        $network = [regex]::Match($logText, '(?m)(?:Http|Socket|Connect)TimeoutException[^\r\n]*|UnknownHostException[^\r\n]*|java\.net\.ConnectException[^\r\n]*|SSLHandshakeException[^\r\n]*|Connection reset[^\r\n]*|Read timed out')
        if ($network.Success) {
            $networkError = $true
            $reason = 'Network download failed (' + ($network.Value.Trim() -replace '^[\w.]+\.(?=\w+Exception)', '') + ') during: ' + $observedStage + '. Finished downloads are kept; rerun the same check. If it keeps failing, test the connection to maven.minecraftforge.net and services.gradle.org (VPN, proxy, firewall).'
        }
    }
    $jarInfo = [regex]::Match($logText, 'SRG jar inspected \((\d+) classes, (\d+) mod entry points\)')
    if ($jarInfo.Success) {
        $classCount = [int]$jarInfo.Groups[1].Value
        $modEntryPoints = [int]$jarInfo.Groups[2].Value
    }
    $versionInfo=[regex]::Match($logText,'(?m)^MOD_VERSION: (\S+)\s*$')
    if($versionInfo.Success){$modVersion=$versionInfo.Groups[1].Value}
    $artifactInfo=[regex]::Match($logText,'(?m)^MOD_ARTIFACT: (.+)\r?$')
    if($artifactInfo.Success){$inspectedArtifact=$artifactInfo.Groups[1].Value.Trim()}
} else { $logPath = $null }
if ($exitCode -eq 0 -and $Profile -in @('build', 'unit')) {
    $unitTestCount = 0
    Get-ChildItem -LiteralPath (Join-Path $projectRoot 'build/test-results/test') -Filter 'TEST-*.xml' -ErrorAction SilentlyContinue | ForEach-Object {
        [xml]$testReport = Get-Content -Encoding UTF8 -LiteralPath $_.FullName
        $unitTestCount += [int]$testReport.testsuite.tests - [int]$testReport.testsuite.skipped
    }
}
$harness = $null
if ($harnessPath -and [IO.File]::Exists($harnessPath)) {
    $harness = [IO.File]::ReadAllText($harnessPath, [Text.Encoding]::UTF8) | ConvertFrom-Json
    # Forge logs broken recipe/loot JSON and keeps going; the game then silently lacks them. Make them lint errors.
    if ($logPath -and [IO.File]::Exists($logPath)) {
        $logLines = [IO.File]::ReadAllLines($logPath)
        $loadErrors = @()
        for ($i = 0; $i -lt $logLines.Length; $i++) {
            if ($logLines[$i] -match 'Parsing error loading recipe (\S+)|Couldn''t load loot table (\S+)') {
                $subject = if ($Matches[1]) { 'recipe ' + $Matches[1] } else { 'loot table ' + $Matches[2] }
                $reason = ''
                for ($j = $i + 1; $j -lt [Math]::Min($logLines.Length, $i + 6); $j++) {
                    if ($logLines[$j] -match '^\s*(?:Caused by: )?[\w.$]+(?:Exception|Error): (.+)$') { $reason = $Matches[1].Trim(); break }
                }
                $loadErrors += [pscustomobject]@{ level = 'ERROR'; subject = $subject; message = $(if ($reason) { "failed to load: $reason" } else { 'failed to load (see log)' }) }
            }
        }
        if ($loadErrors.Count) { $harness.lint = @($harness.lint) + $loadErrors }
    }
    if ($exitCode -eq 0) {
        $failedTests = @($harness.tests | Where-Object { $_.status -ne 'PASS' })
        $lintErrors = @($harness.lint | Where-Object { $_.level -eq 'ERROR' })
        if ($harness.testError) { $exitCode = 1; $reason = $harness.testError }
        elseif ($failedTests.Count) { $exitCode = 1; $reason = ('{0} of {1} {2} failed.' -f $failedTests.Count, @($harness.tests).Count, $(if ($usesClient) { 'GUIs (client/server container sync)' } else { 'game tests' })) }
        elseif ($lintErrors.Count) { $exitCode = 1; $reason = ('{0} content error(s) found {1}.' -f $lintErrors.Count, $(if ($usesClient) { 'in the client' } else { 'by the harness' })) }
        elseif ($Profile -eq 'game') { $reason = ('{0} game tests passed.' -f @($harness.tests).Count) }
        elseif ($Profile -eq 'client') { $reason = ('{0} blocks and {1} items have models and textures; {2} GUIs open with the client in sync with the server; look at the screenshots.' -f $harness.checked.blocks, $harness.checked.items, [int]$harness.checked.guis) }
    }
}
$result = [ordered]@{
    schemaVersion = 1
    profile = $Profile
    scope = switch ($Profile) {
        'environment' { 'toolchain and API compilation' }
        'build' { 'compile, unit tests if present, jar inspection' }
        'unit' { 'unit tests (at least one required)' }
        'server' { 'build + dedicated server startup + registry/asset lint' }
        'game' { 'build + dedicated server + game tests' }
        'client' { 'build + real client: models, textures, translations, GUI container sync, screenshots' }
    }
    status = if ($exitCode -eq 0) { 'PASS' } else { 'FAIL' }
    exitCode = $exitCode
    gradleExitCode = $gradleExit
    networkError = $networkError
    message = $reason
    elapsedSeconds = [Math]::Round($watch.Elapsed.TotalSeconds, 2)
    serverReady = $serverReady
    serverFatalError = $serverFatalError
    harness = $harness
    unitTestCount = $unitTestCount
    jarClassCount = $classCount
    modEntryPoints = $modEntryPoints
    modVersion = $modVersion
    inspectedArtifact = $inspectedArtifact
    sourcePaths = $sourcePaths
    buildIdentity = $buildIdentity
    cleanReason = if($Profile -in @('build','server','game','client')){$cleanReason}else{$null}
    log = $logPath
    report = $resultPath
}
$json = $result | ConvertTo-Json -Depth 8 -Compress
Set-Content -LiteralPath $resultPath -Value $json -Encoding UTF8
Set-Content -LiteralPath (Join-Path $reportRoot 'latest.json') -Value $json -Encoding UTF8
Write-Output $json
exit $exitCode


