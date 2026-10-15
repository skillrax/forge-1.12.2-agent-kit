# Windows PowerShell 5.1+ and PowerShell 7+. Invoke from any directory.
[CmdletBinding()]
param(
    [ValidateSet('all', 'environment', 'build', 'mod', 'unit', 'server')]
    [string] $Profile = 'all',
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
$stopSent = $false
$process = $null
$processStarted = $false
$writer = $null
$gradleExit = $null
$progressAt=0;$observedStage='starting Gradle wrapper'
try {
    $sourcePaths=@(Get-ChildItem -LiteralPath (Join-Path $projectRoot 'src') -Recurse -File -ErrorAction SilentlyContinue | ForEach-Object {$_.FullName.Substring($projectRoot.Length)} | Sort-Object)
    $buildInputs=@('build.gradle','settings.gradle','gradle.properties','testing/testing.gradle','testing/Test-DevEnvironment.ps1','gradle/wrapper/gradle-wrapper.properties')
    $buildIdentity=($buildInputs | ForEach-Object {$p=Join-Path $projectRoot $_;if(Test-Path -LiteralPath $p){$_+':'+(Get-FileHash -LiteralPath $p -Algorithm SHA256).Hash}}) -join '|'
    $previousPath=Join-Path $reportRoot ($Profile+'-latest.json')
    $previous=if(Test-Path -LiteralPath $previousPath){Get-Content -LiteralPath $previousPath -Raw -Encoding UTF8 | ConvertFrom-Json}else{$null}
    $cleanReason=if($Clean){'requested'}elseif(-not $previous -or $previous.status -ne 'PASS'){'no successful baseline'}elseif($previous.buildIdentity -ne $buildIdentity){'build configuration changed'}elseif(@($previous.sourcePaths | Where-Object {$_ -notin $sourcePaths}).Count){'source/resource path removed'}else{$null}
    $taskArgs = switch ($Profile) {
        'all' { 'verifyAll' }
        'environment' { 'verifyEnvironment --rerun-tasks' }
        'build' { 'build verifyModJar' }
        'mod' { 'verifyAll -PrequireMod=true' }
        'unit' { 'cleanTest verifyUnitTests --rerun-tasks' }
        'server' {
            $serverDir = Join-Path $projectRoot ('run/testing-server/' + $runId)
            New-Item -ItemType Directory -Force -Path $serverDir | Out-Null
            Set-Content -LiteralPath (Join-Path $serverDir 'eula.txt') -Value 'eula=true' -Encoding ASCII
            Set-Content -LiteralPath (Join-Path $serverDir 'server.properties') -Encoding ASCII -Value @(
                'server-ip=127.0.0.1', 'server-port=0', 'level-type=FLAT', 'generate-structures=false'
            )
            'runServer "-PtestingToolsServerDir=' + $serverDir + '"'
        }
    }
    if($Profile -in @('all','build','mod') -and $cleanReason){$taskArgs='clean '+$taskArgs}
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
    $writer = New-Object IO.StreamWriter($logPath, $false, (New-Object Text.UTF8Encoding($false)))
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
                    if($line -match '^> Task |^> Configure |^Downloading |^Fetching distribution|^Processing Minecraft dependency:'){$observedStage=$line.Substring(0,[Math]::Min(150,$line.Length))}
                    if ($Profile -eq 'server' -and $line -match 'Encountered an unexpected exception|Exception in server tick loop|Caught exception from|[A-Z0-9_]+_ASSERTIONS_FAIL:') {
                        $serverFatalError = $true
                    }
                    if ($Profile -eq 'server' -and $line -match 'Done \([0-9.,]+s\)!') {
                        $serverReady = $true
                        if (-not $stopSent -and -not $process.HasExited) {
                            $process.StandardInput.WriteLine('stop')
                            $process.StandardInput.Flush()
                            $stopSent = $true
                        }
                    }
                    $reads[$index] = $streams[$index].ReadLineAsync()
                }
            }
        }
        if($watch.Elapsed.TotalSeconds -ge ($progressAt+5)){
            [Console]::Error.WriteLine(('[{0}s] {1}: {2}; still running, full output saved to log.' -f [int]$watch.Elapsed.TotalSeconds,$Profile,$observedStage))
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
    if ($Profile -eq 'server' -and $serverFatalError) {
        throw 'Server reported a fatal startup/runtime exception. Read the saved log.'
    }
    if ($Profile -eq 'server' -and (-not $serverReady -or -not $stopSent)) {
        throw 'Server exited without reaching readiness and receiving the stop command.'
    }
    $exitCode = 0
    $reason = if ($Profile -eq 'server') { 'Server reached readiness and shut down successfully.' } else { 'Requested Gradle checks passed.' }
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
if ($exitCode -eq 0 -and $Profile -in @('all', 'build', 'mod', 'unit')) {
    $unitTestCount = 0
    Get-ChildItem -LiteralPath (Join-Path $projectRoot 'build/test-results/test') -Filter 'TEST-*.xml' -ErrorAction SilentlyContinue | ForEach-Object {
        [xml]$testReport = Get-Content -Encoding UTF8 -LiteralPath $_.FullName
        $unitTestCount += [int]$testReport.testsuite.tests - [int]$testReport.testsuite.skipped
    }
    if ($unitTestCount -eq 0) { $reason += ' No unit tests were present; mod behavior has not been tested.' }
}
$result = [ordered]@{
    schemaVersion = 1
    profile = $Profile
    scope = switch ($Profile) {
        'all' { 'environment, build, existing unit tests, static SRG jar inspection' }
        'environment' { 'environment and API compilation only' }
        'build' { 'build, existing unit tests, static SRG jar inspection' }
        'mod' { 'environment, build, existing unit tests, static SRG jar inspection with required @Mod entry point' }
        'unit' { 'unit tests, at least one executed test required' }
        'server' { 'development server readiness and shutdown only' }
    }
    status = if ($exitCode -eq 0) { 'PASS' } else { 'FAIL' }
    exitCode = $exitCode
    gradleExitCode = $gradleExit
    message = $reason
    elapsedSeconds = [Math]::Round($watch.Elapsed.TotalSeconds, 2)
    serverReady = $serverReady
    serverFatalError = $serverFatalError
    unitTestCount = $unitTestCount
    jarClassCount = $classCount
    modEntryPoints = $modEntryPoints
    modVersion = $modVersion
    inspectedArtifact = $inspectedArtifact
    sourcePaths = $sourcePaths
    buildIdentity = $buildIdentity
    cleanReason = if($Profile -in @('all','build','mod')){$cleanReason}else{$null}
    log = $logPath
    report = $resultPath
}
$json = $result | ConvertTo-Json -Depth 5 -Compress
Set-Content -LiteralPath $resultPath -Value $json -Encoding UTF8
Set-Content -LiteralPath (Join-Path $reportRoot 'latest.json') -Value $json -Encoding UTF8
Write-Output $json
exit $exitCode


