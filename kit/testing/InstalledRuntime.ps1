# Shared helpers for optional installed-artifact checks. No game is started on import.
[Console]::OutputEncoding=New-Object Text.UTF8Encoding($false)
$OutputEncoding=[Console]::OutputEncoding
# Prefer this host's modules when invoked from Codex/another PowerShell edition.
$env:PSModulePath=(Join-Path $PSHOME 'Modules')+';'+$env:PSModulePath
function Write-InstalledJson([string]$Path,$Value) {
    [IO.File]::WriteAllText($Path,($Value | ConvertTo-Json -Depth 16),(New-Object Text.UTF8Encoding($false)))
}
function Read-InstalledJson([string]$Path) {
    Get-Content -LiteralPath $Path -Encoding UTF8 -Raw | ConvertFrom-Json
}
function Read-InstalledScenarioCatalog([string]$Jar) {
    Add-Type -AssemblyName System.IO.Compression.FileSystem
    $zip=[IO.Compression.ZipFile]::OpenRead($Jar)
    try {
        $entry=$zip.GetEntry('META-INF/installed-scenarios.json')
        if(-not $entry){throw 'Driver catalog missing; build project scenarios with the current installed-test harness.'}
        if($entry.Length -gt 65536){throw 'Driver catalog exceeds 64 KiB.'}
        $reader=New-Object IO.StreamReader($entry.Open(),(New-Object Text.UTF8Encoding($false)))
        try{$catalog=$reader.ReadToEnd() | ConvertFrom-Json}finally{$reader.Dispose()}
        if($catalog.schemaVersion -ne 1 -or $catalog.apiVersion -ne 1 -or @($catalog.scenarios).Count -lt 1 -or @($catalog.scenarios).Count -gt 256){throw 'Unsupported/empty driver scenario catalog.'}
        $ids=@{}
        foreach($scenario in $catalog.scenarios){
            if($scenario.id -notmatch '^[a-z][a-z0-9-]{0,63}$' -or -not $scenario.className -or $ids.ContainsKey($scenario.id)){throw 'Invalid/duplicate scenario in driver catalog.'}
            $ids[$scenario.id]=$true
        }
        return $catalog
    }finally{$zip.Dispose()}
}
function Resolve-InstalledPath([string]$Base,[string]$Path) {
    if(-not $Path){throw 'An explicit file path is required.'}
    if([IO.Path]::IsPathRooted($Path)){[IO.Path]::GetFullPath($Path)}else{[IO.Path]::GetFullPath((Join-Path $Base $Path))}
}
function Get-InstalledFile([string]$Path,[string]$Expected) {
    if($Expected -notmatch '^[a-fA-F0-9]{64}$'){throw "Expected SHA256 required: $Path"}
    if(-not [IO.File]::Exists($Path)){throw "Required file missing: $Path"}
    $hash=(Get-FileHash -LiteralPath $Path -Algorithm SHA256).Hash.ToLowerInvariant()
    if($hash -ne $Expected.ToLowerInvariant()){throw "SHA256 mismatch: $Path"}
    [pscustomobject]@{path=[IO.Path]::GetFullPath($Path);sha256=$hash}
}
function ConvertTo-InstalledArgument([string]$Value) {
    if($Value -match '["\r\n\x00]'){throw 'Unsupported quote/newline in process argument.'}
    '"'+[regex]::Replace($Value,'(\\+)$','$1$1')+'"'
}
function Invoke-InstalledProcess {
    param([string]$Java,[string[]]$Arguments,[string]$Directory,[string]$Log,[int]$TimeoutSeconds)
    $info=New-Object Diagnostics.ProcessStartInfo
    $info.FileName=$Java;$info.Arguments=($Arguments | ForEach-Object {ConvertTo-InstalledArgument $_}) -join ' '
    $info.WorkingDirectory=$Directory;$info.UseShellExecute=$false;$info.CreateNoWindow=$true
    $info.RedirectStandardOutput=$true;$info.RedirectStandardError=$true;$info.RedirectStandardInput=$true
    $info.StandardOutputEncoding=New-Object Text.UTF8Encoding($false)
    $info.StandardErrorEncoding=New-Object Text.UTF8Encoding($false)
    foreach($name in @('CLASSPATH','JAVA_TOOL_OPTIONS','_JAVA_OPTIONS','JDK_JAVA_OPTIONS')){$info.EnvironmentVariables.Remove($name)}
    $process=New-Object Diagnostics.Process;$process.StartInfo=$info
    $writer=New-Object IO.StreamWriter($Log,$false,(New-Object Text.UTF8Encoding($false)))
    $writer.AutoFlush=$true;$watch=[Diagnostics.Stopwatch]::StartNew();$started=$false;$nextProgress=5
    try {
        if(-not $process.Start()){throw 'Java process did not start.'};$started=$true
        $streams=@($process.StandardOutput,$process.StandardError)
        $reads=@($streams[0].ReadLineAsync(),$streams[1].ReadLineAsync());$ended=@($false,$false)
        while(-not($process.HasExited -and $ended[0] -and $ended[1])) {
            for($index=0;$index -lt 2;$index++) {
                for($count=0;$count -lt 64 -and -not $ended[$index] -and $reads[$index].IsCompleted;$count++) {
                    $line=$reads[$index].GetAwaiter().GetResult()
                    if($null -eq $line){$ended[$index]=$true;continue}
                    $writer.WriteLine($line);$reads[$index]=$streams[$index].ReadLineAsync()
                }
            }
            if($watch.Elapsed.TotalSeconds -ge $TimeoutSeconds){throw "Installed Java process timed out after $TimeoutSeconds seconds; log: $Log"}
            if($watch.Elapsed.TotalSeconds -ge $nextProgress){[Console]::Error.WriteLine(('[{0}s] Installed runtime active; log: {1}' -f [int]$watch.Elapsed.TotalSeconds,$Log));$nextProgress+=5}
            Start-Sleep -Milliseconds 20
        }
        [Console]::Error.WriteLine('Installed Java process exited; checking evidence.')
        [pscustomobject]@{exitCode=$process.ExitCode;elapsedSeconds=[Math]::Round($watch.Elapsed.TotalSeconds,2)}
    } finally {
        if($started -and -not $process.HasExited){
            try{$process.StandardInput.WriteLine('stop');$process.StandardInput.Flush()}catch{}
            if(-not $process.WaitForExit(5000)){& "$env:SystemRoot/System32/taskkill.exe" /PID $process.Id /T /F 2>&1 | Out-Null}
        }
        $writer.Dispose();$process.Dispose()
    }
}
function Test-InstalledRuntime([string]$LockPath,$Kit) {
    $lock=Read-InstalledJson $LockPath
    if($lock.schemaVersion -ne 1 -or $lock.minecraft -ne $Kit.minecraft -or $lock.forge -ne $Kit.forge){throw 'Installed runtime lock does not match kit pins.'}
    $base=Split-Path -Parent ([IO.Path]::GetFullPath($LockPath))
    $server=Join-Path $base 'server'
    $paths=@{}
    foreach($entry in $lock.files){
        $path=Resolve-InstalledPath $server $entry.path
        if(-not $path.StartsWith($server+[IO.Path]::DirectorySeparatorChar,[StringComparison]::OrdinalIgnoreCase) -or $paths.ContainsKey($path)){throw 'Unsafe/duplicate runtime lock path.'}
        $paths[$path]=$true
        $null=Get-InstalledFile $path $entry.sha256
    }
    if(@($lock.files).Count -lt 3){throw 'Incomplete installed runtime lock.'}
    $actual=@(Get-ChildItem -LiteralPath $server -Recurse -File)
    if($actual.Count -ne $paths.Count){throw 'Installed runtime file inventory changed.'}
    foreach($file in $actual){if(-not $paths.ContainsKey($file.FullName)){throw 'Unexpected installed runtime file.'}}
    $forgeJar='forge-'+$Kit.minecraft+'-'+$Kit.forge+'.jar'
    foreach($required in @($forgeJar,('minecraft_server.'+$Kit.minecraft+'.jar'))){if(-not $paths.ContainsKey((Join-Path $server $required))){throw "Runtime lock missing $required"}}
    [pscustomobject]@{root=$server;forgeJar=$forgeJar;lock=$lock;identity=(Get-FileHash -LiteralPath $LockPath).Hash.ToLowerInvariant()}
}
