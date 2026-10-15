# Stable frontend for local agents. MCP will call this same interface.
[CmdletBinding()]
param(
    [ValidateSet('status', 'check', 'errors')][string]$Action = 'status',
    [ValidateSet('all', 'environment', 'build', 'mod', 'unit', 'server')][string]$Profile = 'all',
    [ValidateRange(10, 7200)][int]$TimeoutSeconds = 900,
    [ValidatePattern('^[A-Za-z0-9-]+$')][string]$RunId,
    [switch]$Full,
    [switch]$Clean
)
$ErrorActionPreference = 'Stop'
[Console]::OutputEncoding = New-Object Text.UTF8Encoding($false)
$OutputEncoding = [Console]::OutputEncoding
# Prefer this shell's modules over paths inherited from a different PowerShell host.
$env:PSModulePath=(Join-Path $PSHOME 'Modules')+';'+$env:PSModulePath
# MCP hosts may omit these Windows variables. Restore only this process's defaults.
if (-not $env:PATHEXT) { $env:PATHEXT = '.COM;.EXE;.BAT;.CMD' }
if (-not [IO.File]::Exists($env:ComSpec)) {
    $env:ComSpec = Join-Path ([Environment]::GetFolderPath('System')) 'cmd.exe'
}
$root = [IO.Path]::GetFullPath($PSScriptRoot)
$requestedJavaHome=$env:JAVA_HOME
$javaSelection=$null
if([IO.File]::Exists((Join-Path $root 'tools/JavaEnvironment.ps1'))){
    . (Join-Path $root 'tools/JavaEnvironment.ps1')
    $javaSelection=Get-KitJavaSelection
    Set-KitJavaProcess $javaSelection
}
. (Join-Path $root 'testing/Diagnostics.ps1')
function Get-ToolchainIdentity {
    $identity = [ordered]@{ requestedJavaHome=$requestedJavaHome; javaHome=$env:JAVA_HOME; java8Home=if($javaSelection.java8){$javaSelection.java8.home}else{$null}; gradleUserHome=$env:GRADLE_USER_HOME; path=$env:PATH }
    foreach ($name in @('java.exe','node.exe')) {
        $command = Get-Command $name -ErrorAction SilentlyContinue | Select-Object -First 1
        $identity[$name] = if ($command) { $command.Source } else { $null }
    }
    # Hash installed JDK release identities too, including the separately discovered Java 8 compiler.
    $jdkRoots = @((Join-Path $env:ProgramFiles 'Eclipse Adoptium'), (Join-Path $env:USERPROFILE '.gradle/jdks'), (Join-Path $env:USERPROFILE '.jdks'))
    foreach ($jdkRoot in $jdkRoots) {
        if ([IO.Directory]::Exists($jdkRoot)) {
            foreach ($jdk in (Get-ChildItem -LiteralPath $jdkRoot -Directory | Sort-Object Name)) {
                $jdkRelease = Join-Path $jdk.FullName 'release'
                if ([IO.File]::Exists($jdkRelease)) { $identity[$jdk.FullName] = [IO.File]::ReadAllText($jdkRelease) }
            }
        }
    }
    $release = if ($env:JAVA_HOME) { Join-Path $env:JAVA_HOME 'release' } else { $null }
    $identity['javaRelease'] = if ($release -and [IO.File]::Exists($release)) { [IO.File]::ReadAllText($release) } else { $null }
    $hash = [Security.Cryptography.SHA256]::Create()
    try { [BitConverter]::ToString($hash.ComputeHash([Text.Encoding]::UTF8.GetBytes(($identity | ConvertTo-Json -Compress)))).Replace('-', '').ToLowerInvariant() } finally { $hash.Dispose() }
}
function Test-FreshResult {
    param($Evidence)
    if ($Evidence -and $Evidence.artifact) {
        if (-not [IO.File]::Exists($Evidence.artifact.path) -or (Get-FileHash -LiteralPath $Evidence.artifact.path -Algorithm SHA256).Hash -ne $Evidence.artifact.sha256) { return $false }
    }
    foreach ($file in $Evidence.files) {
        if (-not [IO.File]::Exists($file.path) -or (Get-FileHash -LiteralPath $file.path -Algorithm SHA256).Hash -ne $file.sha256) { return $false }
    }
    return ($null -ne $Evidence -and $Evidence.projectRoot -eq $root -and $Evidence.inputFingerprint -eq $fingerprint -and $Evidence.toolchainFingerprint -eq $toolchainFingerprint -and -not $Evidence.inputsChangedDuringRun -and -not $Evidence.toolchainChangedDuringRun)
}
function Get-Capabilities {
    $summary = [ordered]@{}
    foreach ($profileName in @('environment','all','build','mod','unit','server')) {
        $path = Join-Path $reportRoot ($profileName + '-latest.json')
        $evidence = if ([IO.File]::Exists($path)) { [IO.File]::ReadAllText($path) | ConvertFrom-Json } else { $null }
        $summary[$profileName] = if ($evidence) { [ordered]@{ state=if (Test-FreshResult $evidence) { $evidence.status } else { 'STALE' }; runId=$evidence.runId } } else { @{state='NOT_RUN'} }
    }
    foreach ($capability in @('behavior','client','installedArtifact')) {
        $path = Join-Path $root ('reports/capability-evidence/' + $capability + '.json')
        $evidence = if ([IO.File]::Exists($path)) { [IO.File]::ReadAllText($path) | ConvertFrom-Json } else { $null }
        $summary[$capability] = if ($evidence) { @{ state=if (Test-FreshResult $evidence) { 'RECORDED_' + $evidence.status } else { 'STALE' }; method=$evidence.method; observation=$evidence.observation; evidence=$path } } else { @{ state='NOT_RUN' } }
    }
    return $summary
}
$reportRoot = Join-Path $root 'reports/testing-tools'
$lock = $null
$exitCode = 0
$json = $null
$runnerLogPath = $null
function Get-InputFingerprint {
    $paths = @('build.gradle', 'settings.gradle', 'gradle.properties', 'agent.ps1', 'gradlew', 'gradlew.bat',
        '.codex/config.toml', 'mcp/server.mjs', 'mcp/tools.mjs', 'mcp/bridge.mjs', 'mcp/package.json', 'mcp/package-lock.json', 'kit.json')
    $files = New-Object 'System.Collections.Generic.List[string]'
    foreach ($relativePath in $paths) {
        $candidate = Join-Path $root $relativePath
        if ([IO.File]::Exists($candidate)) { $files.Add($candidate) }
    }
    foreach ($directory in @('src', 'testing', 'gradle', 'tools')) {
        if (-not [IO.Directory]::Exists((Join-Path $root $directory))) { continue }
        foreach ($candidate in [IO.Directory]::GetFiles((Join-Path $root $directory), '*', [IO.SearchOption]::AllDirectories)) {
            $files.Add($candidate)
        }
    }
    $files.Sort([StringComparer]::Ordinal)
    $hash = [Security.Cryptography.SHA256]::Create()
    try {
        $entries = foreach ($file in $files) {
            $relative = $file.Substring($root.Length).Replace('\', '/')
            $digest = [BitConverter]::ToString($hash.ComputeHash([IO.File]::ReadAllBytes($file))).Replace('-', '')
            $relative + ':' + $digest
        }
        [BitConverter]::ToString($hash.ComputeHash([Text.Encoding]::UTF8.GetBytes(($entries -join "`n")))).Replace('-', '').ToLowerInvariant()
    } finally { $hash.Dispose() }
}
function Read-Result {
    $path = if ($RunId) { Join-Path $reportRoot ($RunId + '.json') } else { Join-Path $reportRoot 'latest.json' }
    if (Test-Path -LiteralPath $path) { [IO.File]::ReadAllText($path) | ConvertFrom-Json }
    elseif ($RunId) { throw "Unknown run ID: $RunId" }
    else { return $null }
}
function Get-NextAction {
    param($Previous,$Capabilities)
    if($javaSelection -and (-not $javaSelection.gradle -or -not $javaSelection.java8)){return 'Run Setup.cmd to install/verify the missing JDK prerequisites.'}
    if($Previous -and $Previous.status -eq 'FAIL' -and (Test-FreshResult $Previous)){return ('Repair the latest '+$Previous.profile+' failure using its diagnostics/log, then rerun that profile. Run ID: '+$Previous.runId)}
    if($Capabilities.mod.state -eq 'PASS'){return 'Export the validated mod with tools/Export-Release.ps1; complete the requested gameplay/client acceptance.'}
    $javaFiles=@(Get-ChildItem -LiteralPath (Join-Path $root 'src/main') -Filter '*.java' -Recurse -File -ErrorAction SilentlyContinue)
    if($javaFiles.Count){return 'Run check -Profile mod for the current source (includes environment verification), then check the requested behavior. Build/server readiness alone is not gameplay proof.'}
    if($Capabilities.environment.state -ne 'PASS' -and $Capabilities.all.state -ne 'PASS'){return 'Run check -Profile environment to verify the pinned toolchain; previous evidence may be absent or stale.'}
    return 'Set the requested behavior/acceptance in TASK.md and implement the first mod; the foundation is ready.'
}
try {
    $fingerprint = Get-InputFingerprint
    $toolchainFingerprint = Get-ToolchainIdentity
    switch ($Action) {
        'check' {
            New-Item -ItemType Directory -Force -Path $reportRoot | Out-Null
            try { $lock = [IO.File]::Open((Join-Path $reportRoot 'agent.lock'), 'OpenOrCreate', 'ReadWrite', 'None') }
            catch { throw 'Another agent check is running in this checkout. Wait for it to finish.' }
            $shell = (Get-Process -Id $PID).Path
            $runnerLogPath = Join-Path $reportRoot ('runner-' + [Guid]::NewGuid().ToString('N') + '.log')
            $runnerStart = New-Object Diagnostics.ProcessStartInfo
            $runnerStart.FileName = $shell
            $runnerStart.Arguments = '-NoProfile -File "' + (Join-Path $root 'testing/Test-DevEnvironment.ps1') + '" -Profile ' + $Profile + ' -TimeoutSeconds ' + $TimeoutSeconds
            if($Clean){$runnerStart.Arguments+=' -Clean'}
            $runnerStart.WorkingDirectory = $root
            $runnerStart.UseShellExecute = $false
            $runnerStart.CreateNoWindow = $true
            $runnerStart.RedirectStandardOutput = $true
            $runnerStart.RedirectStandardError = $true
            $runnerProcess = New-Object Diagnostics.Process
            $runnerProcess.StartInfo = $runnerStart
            try {
                if (-not $runnerProcess.Start()) { throw 'Could not start the testing runner.' }
                $stdoutRead = $runnerProcess.StandardOutput.ReadToEndAsync()
                $stderrLines=New-Object Text.StringBuilder
                $stderrRead=$runnerProcess.StandardError.ReadLineAsync();$stderrEnded=$false
                $runnerWatch=[Diagnostics.Stopwatch]::StartNew()
                while(-not ($runnerProcess.HasExited -and $stderrEnded)){
                    for($drain=0;$drain -lt 32 -and -not $stderrEnded -and $stderrRead.IsCompleted;$drain++){
                        $line=$stderrRead.GetAwaiter().GetResult()
                        if($null -eq $line){$stderrEnded=$true;continue}
                        $stderrLines.AppendLine($line) | Out-Null
                        [Console]::Error.WriteLine($line)
                        $stderrRead=$runnerProcess.StandardError.ReadLineAsync()
                    }
                    if($runnerWatch.Elapsed.TotalSeconds -gt ($TimeoutSeconds+30)){break}
                    Start-Sleep -Milliseconds 50
                }
                if (-not $runnerProcess.HasExited -or -not $stderrEnded) {
                    $killerStart = New-Object Diagnostics.ProcessStartInfo
                    $killerStart.FileName = Join-Path ([Environment]::GetFolderPath('System')) 'taskkill.exe'
                    $killerStart.Arguments = '/PID ' + $runnerProcess.Id + ' /T /F'
                    $killerStart.UseShellExecute = $false
                    $killerStart.CreateNoWindow = $true
                    $killerStart.RedirectStandardOutput = $true
                    $killerStart.RedirectStandardError = $true
                    $killer = [Diagnostics.Process]::Start($killerStart)
                    $killer.WaitForExit(5000) | Out-Null
                    $killer.Dispose()
                    throw 'Testing runner exceeded its timeout and was terminated.'
                }
                $output = $stdoutRead.GetAwaiter().GetResult()
                $runnerErrors = $stderrLines.ToString()
                $exitCode = $runnerProcess.ExitCode
                [IO.File]::WriteAllText($runnerLogPath, "STDOUT:`n$output`nSTDERR:`n$runnerErrors", (New-Object Text.UTF8Encoding($false)))
            } finally { $runnerProcess.Dispose() }
            $result = $output | ConvertFrom-Json
            if ($null -eq $result -or -not $result.report) {
                throw "Testing runner returned no valid JSON report (exit code $exitCode)."
            }
            if ($result.exitCode -ne $exitCode -or ($exitCode -eq 0 -and $result.status -ne 'PASS')) { throw 'Runner exit code and validation result disagree.' }
            $result | Add-Member -NotePropertyName projectRoot -NotePropertyValue $root
            $result | Add-Member -NotePropertyName toolchainFingerprint -NotePropertyValue $toolchainFingerprint
            $result | Add-Member -NotePropertyName toolchainChangedDuringRun -NotePropertyValue ((Get-ToolchainIdentity) -ne $toolchainFingerprint)
            $artifact = $null
            if ($null -ne $result.jarClassCount) {
                $jarPath=$result.inspectedArtifact
                if(-not $jarPath -or -not [IO.File]::Exists($jarPath)){throw 'Validation did not identify an existing inspected SRG JAR.'}
                $artifact = @{ path=$jarPath; sha256=(Get-FileHash -LiteralPath $jarPath -Algorithm SHA256).Hash }
            }
            $result | Add-Member -NotePropertyName artifact -NotePropertyValue $artifact
            $result | Add-Member -NotePropertyName runId -NotePropertyValue ([IO.Path]::GetFileNameWithoutExtension($result.report))
            $result | Add-Member -NotePropertyName inputFingerprint -NotePropertyValue $fingerprint
            $changed = (Get-InputFingerprint) -ne $fingerprint
            $result | Add-Member -NotePropertyName inputsChangedDuringRun -NotePropertyValue $changed
            if ($changed -or $result.toolchainChangedDuringRun) {
                $exitCode = 1
                $result.exitCode = 1
                $result.status = 'FAIL'
                $result.message = 'Project inputs or toolchain changed during validation. Run the check again.'
            }
            $result | Add-Member -NotePropertyName diagnostics -NotePropertyValue $(if($exitCode -ne 0){@(Get-BoundedDiagnostics -LogPath $result.log)}else{@()})
            $json = $result | ConvertTo-Json -Depth 8 -Compress
            Set-Content -LiteralPath $result.report -Value $json -Encoding UTF8
            Set-Content -LiteralPath (Join-Path $reportRoot 'latest.json') -Value $json -Encoding UTF8
            Set-Content -LiteralPath (Join-Path $reportRoot ($Profile + '-latest.json')) -Value $json -Encoding UTF8
            if(-not $Full){$json=$result | Select-Object * -ExcludeProperty sourcePaths,buildIdentity | ConvertTo-Json -Depth 8 -Compress}
        }
        'status' {
            $previous = Read-Result
            $taskText = [IO.File]::ReadAllText((Join-Path $root 'TASK.md'))
            if ($taskText.Length -gt 2000) { $taskText = $taskText.Substring(0, 2000) }
            $capabilities=Get-Capabilities
            $result = [ordered]@{
                schemaVersion = 1; action = 'status'; project = [IO.Path]::GetFileName($root); projectRoot = $root
                inputFingerprint = $fingerprint; toolchainFingerprint = $toolchainFingerprint
                javaSelection = $javaSelection
                capabilities = $capabilities
                lastRun = if ($Full -or $null -eq $previous) { $previous } else { $previous | Select-Object runId, profile, status, scope, message, unitTestCount, log, artifact }
                lastRunMatchesCurrentInputs = (Test-FreshResult $previous)
                task = $taskText
                nextAction = Get-NextAction $previous $capabilities
            }
        }
        'errors' {
            $previous = Read-Result
            if ($null -eq $previous) { throw 'No validation report exists. Run check first.' }
            $diagnostics = @(Get-BoundedDiagnostics -LogPath $previous.log)
            $result = [ordered]@{
                schemaVersion = 1; action = 'errors'; projectRoot = $root; runId = $previous.runId
                validationStatus = $previous.status; message = $previous.message
                scope = $previous.scope; profile = $previous.profile
                inputFingerprint = $previous.inputFingerprint; toolchainFingerprint = $previous.toolchainFingerprint
                report = $previous.report
                matchesCurrentInputs = (Test-FreshResult $previous)
                diagnostics = $diagnostics; fullLog = $previous.log
            }
        }
    }
    if (-not $json) { $json = $result | ConvertTo-Json -Depth 8 -Compress }
} catch {
    $exitCode = 1
    $failureMessage = $_.Exception.Message
    if ($Action -eq 'check' -and $null -ne $lock) {
        $failedId = [DateTime]::UtcNow.ToString('yyyyMMddTHHmmssfffZ') + '-' + [Guid]::NewGuid().ToString('N').Substring(0, 8)
        $failedPath = Join-Path $reportRoot ($failedId + '.json')
        $json = [ordered]@{
            schemaVersion = 1; projectRoot = $root; toolchainFingerprint = $toolchainFingerprint; action = 'check'; profile = $Profile; scope = 'validation runner'
            status = 'FAIL'; exitCode = 1; message = $failureMessage
            runId = $failedId; inputFingerprint = $fingerprint; inputsChangedDuringRun = $false
            report = $failedPath; log = $runnerLogPath; unitTestCount = $null
            diagnostics = @(Get-BoundedDiagnostics -LogPath $runnerLogPath)
        } | ConvertTo-Json -Compress
        $utf8 = New-Object Text.UTF8Encoding($false)
        [IO.File]::WriteAllText($failedPath, $json, $utf8)
        [IO.File]::WriteAllText((Join-Path $reportRoot 'latest.json'), $json, $utf8)
        [IO.File]::WriteAllText((Join-Path $reportRoot ($Profile + '-latest.json')), $json, $utf8)
    } else {
        $json = [ordered]@{ schemaVersion = 1; projectRoot = $root; action = $Action; status = 'FAIL'; exitCode = 1; message = $failureMessage } | ConvertTo-Json -Compress
    }
} finally {
    if ($null -ne $lock) { $lock.Dispose() }
}
Write-Output $json
exit $exitCode
