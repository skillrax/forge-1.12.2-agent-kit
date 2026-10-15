# Discover Node independently of stale PATH after a Windows installer completes.
function Get-KitNodeCandidates {
    $command=Get-Command node.exe -ErrorAction SilentlyContinue | Select-Object -First 1
    if($command){$command.Source}
    Join-Path $env:ProgramFiles 'nodejs/node.exe'
    if($env:LOCALAPPDATA){Join-Path $env:LOCALAPPDATA 'Programs/nodejs/node.exe'}
}
function Get-KitNodeSelection {
    foreach($candidate in (Get-KitNodeCandidates | Select-Object -Unique)){
        if(-not [IO.File]::Exists($candidate)){continue}
        $version=& $candidate --version
        if($LASTEXITCODE -ne 0 -or $version -notmatch '^v(\d+)\.' -or [int]$Matches[1] -lt 22){continue}
        $npm=Join-Path (Split-Path -Parent $candidate) 'node_modules/npm/bin/npm-cli.js'
        if(-not [IO.File]::Exists($npm)){continue}
        $npmVersion=& $candidate $npm --version
        if($LASTEXITCODE -ne 0 -or $npmVersion -notmatch '^\d+\.\d+\.\d+'){continue}
        return [pscustomobject]@{node=[IO.Path]::GetFullPath($candidate);version=$version;npm=$npm;npmVersion=$npmVersion}
    }
    return $null
}
function Request-KitNodeChoice {
    [Console]::Error.WriteLine('Recommended: Node.js enables the kit MCP tools so your agent client can read status, validate mods and retrieve diagnostics directly.')
    [Console]::Error.WriteLine('Y reuses compatible Node/npm or installs Node.js LTS, then installs the locked MCP dependencies. N keeps the shell-only workflow.')
    while($true){
        [Console]::Error.Write('Set up Node.js and MCP support? [Y/N]: ')
        $answer=[Console]::ReadLine()
        if($null -eq $answer){throw 'No Y/N response for Node.js. Setup stopped before downloads; rerun in a visible window.'}
        if($answer.Trim() -match '^(?i:y|n)$'){return $answer.Trim().ToUpperInvariant()}
        [Console]::Error.WriteLine('Enter Y or N.')
    }
}
function Invoke-KitNodeProcess {
    param([string]$Node,[string[]]$Arguments,[string]$Log,[string]$Stage,[int]$TimeoutSeconds=900)
    $start=New-Object Diagnostics.ProcessStartInfo
    $start.FileName=$Node
    $quoted=foreach($argument in $Arguments){'"'+(($argument -replace '(\\*)"','$1$1\"') -replace '(\\+)$','$1$1')+'"'}
    $start.Arguments=$quoted -join ' '
    $start.UseShellExecute=$false;$start.CreateNoWindow=$true
    $start.RedirectStandardOutput=$true;$start.RedirectStandardError=$true
    $p=[Diagnostics.Process]::Start($start)
    $writer=New-Object IO.StreamWriter($Log,$false,(New-Object Text.UTF8Encoding($false)))
    $watch=[Diagnostics.Stopwatch]::StartNew();$progress=0
    try{
        $streams=@($p.StandardOutput,$p.StandardError);$reads=@($streams[0].ReadLineAsync(),$streams[1].ReadLineAsync());$ended=@($false,$false)
        while(-not ($p.HasExited -and $ended[0] -and $ended[1])){
            for($i=0;$i -lt 2;$i++){
                for($n=0;$n -lt 32 -and -not $ended[$i] -and $reads[$i].IsCompleted;$n++){
                    $line=$reads[$i].GetAwaiter().GetResult()
                    if($null -eq $line){$ended[$i]=$true;continue}
                    $writer.WriteLine($line);$writer.Flush();$reads[$i]=$streams[$i].ReadLineAsync()
                }
            }
            if($watch.Elapsed.TotalSeconds -ge $TimeoutSeconds){throw ($Stage+' timed out; no automatic retry. Inspect '+$Log+' before rerunning setup.')}
            if($watch.Elapsed.TotalSeconds -ge ($progress+5)){
                [Console]::Error.WriteLine(('[{0}s] {1} still running. Log: {2}' -f [int]$watch.Elapsed.TotalSeconds,$Stage,$Log))
                $progress=$watch.Elapsed.TotalSeconds
            }
            Start-Sleep -Milliseconds 50
        }
        if($p.ExitCode -ne 0){throw ($Stage+' failed with exit '+$p.ExitCode+'. Inspect '+$Log+'. Rerun Setup.ps1 -WithMcp -RepairMcp after resolving the cause.')}
    }finally{
        if(-not $p.HasExited){
            $killer=New-Object Diagnostics.ProcessStartInfo
            $killer.FileName=Join-Path ([Environment]::GetFolderPath('System')) 'taskkill.exe'
            $killer.Arguments='/PID '+$p.Id+' /T /F';$killer.UseShellExecute=$false;$killer.CreateNoWindow=$true
            $killer.RedirectStandardOutput=$true;$killer.RedirectStandardError=$true
            $killProcess=[Diagnostics.Process]::Start($killer)
            $killProcess.WaitForExit(5000) | Out-Null;$killProcess.Dispose()
        }
        $writer.Dispose();$p.Dispose()
    }
}
