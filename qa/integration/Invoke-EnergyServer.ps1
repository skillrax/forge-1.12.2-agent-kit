[CmdletBinding()]
param([Parameter(Mandatory)][string]$Workspace,[int]$TimeoutSeconds=120,[ValidateRange(1,128)][int]$DrainBatchSize=32)
$ErrorActionPreference='Stop'
[Console]::OutputEncoding=New-Object Text.UTF8Encoding($false)
$OutputEncoding=[Console]::OutputEncoding
$Workspace=[IO.Path]::GetFullPath($Workspace)
$runId=[DateTime]::UtcNow.ToString('yyyyMMddTHHmmssfffZ')+'-'+[Guid]::NewGuid().ToString('N').Substring(0,8)
$reportDir=Join-Path $Workspace 'reports/energy-acceptance'
New-Item -ItemType Directory -Force -Path $reportDir | Out-Null
$log=Join-Path $reportDir ($runId+'.log')
$report=Join-Path $reportDir ($runId+'.json')
$watch=[Diagnostics.Stopwatch]::StartNew()
$process=$null;$writer=$null;$ready=$false;$marker=$false;$fatal=$false;$stopSent=$false;$started=$false
$diagnostics=New-Object 'System.Collections.Generic.List[string]'
$lineCounts=@(0,0);$launchAt=$null;$runTaskAt=$null;$readyAt=$null;$markerAt=$null;$stopAt=$null;$exitAt=$null
$expected='ENERGY_LIVE_PASS'
try {
    $statusOutput=& powershell -NoProfile -File (Join-Path $Workspace 'agent.ps1') -Action status
    if($LASTEXITCODE -ne 0){throw 'Cannot read workspace identity'}
    $identity=($statusOutput -join "`n") | ConvertFrom-Json
    if($identity.projectRoot -ne $Workspace){throw 'Wrong workspace identity'}
    $start=New-Object Diagnostics.ProcessStartInfo
    $start.FileName=$env:ComSpec
    $start.Arguments='/d /s /c ""'+(Join-Path $Workspace 'gradlew.bat')+'" runServer --console=plain --no-daemon"'
    $start.WorkingDirectory=$Workspace;$start.UseShellExecute=$false;$start.CreateNoWindow=$true
    $start.RedirectStandardOutput=$true;$start.RedirectStandardError=$true;$start.RedirectStandardInput=$true
    $process=New-Object Diagnostics.Process;$process.StartInfo=$start
    $writer=New-Object IO.StreamWriter($log,$false,(New-Object Text.UTF8Encoding($false)))
    $writer.AutoFlush=$true;$writer.WriteLine('FOCUSED ENERGY PROOF: wait for '+$expected+' then stop; source '+$identity.inputFingerprint)
    if(-not $process.Start()){throw 'Server process did not start'}
    $started=$true
    $launchAt=$watch.Elapsed.TotalSeconds
    $streams=@($process.StandardOutput,$process.StandardError)
    $reads=@($streams[0].ReadLineAsync(),$streams[1].ReadLineAsync());$ended=@($false,$false)
    while(-not($process.HasExited -and $ended[0] -and $ended[1])) {
        for($index=0;$index -lt 2;$index++) {
            for($drained=0;$drained -lt $DrainBatchSize -and -not $ended[$index] -and $reads[$index].IsCompleted;$drained++) {
                $line=$reads[$index].GetAwaiter().GetResult()
                if($null -eq $line){$ended[$index]=$true;continue}
                $writer.WriteLine($line)
                $lineCounts[$index]++
                if($line -match '^> Task :runServer' -and $null -eq $runTaskAt){$runTaskAt=$watch.Elapsed.TotalSeconds}
                if($line -match 'Done \([0-9.,]+s\)!'){$ready=$true;if($null -eq $readyAt){$readyAt=$watch.Elapsed.TotalSeconds}}
                if($line.Contains($expected)){$marker=$true;if($null -eq $markerAt){$markerAt=$watch.Elapsed.TotalSeconds}}
                if($line -match 'error:|symbol:|location:' -and $diagnostics.Count -lt 10){$diagnostics.Add($line)}
                if($line -match 'ENERGY_ASSERTIONS_FAIL|Encountered an unexpected exception|Exception in server tick loop|FAILURE: Build failed'){
                    $fatal=$true;if($diagnostics.Count -lt 10){$diagnostics.Add($line)}
                }
                if(($fatal -or ($marker -and $ready)) -and -not $stopSent -and -not $process.HasExited){
                    $process.StandardInput.WriteLine('stop');$process.StandardInput.Flush();$stopSent=$true
                    $stopAt=$watch.Elapsed.TotalSeconds
                }
                $reads[$index]=$streams[$index].ReadLineAsync()
            }
        }
        if($process.HasExited -and $null -eq $exitAt){$exitAt=$watch.Elapsed.TotalSeconds}
        if($watch.Elapsed.TotalSeconds -gt $TimeoutSeconds){throw 'Server acceptance timeout'}
        Start-Sleep -Milliseconds 20
    }
    if($fatal -or -not $ready -or -not $marker -or -not $stopSent -or $process.ExitCode -ne 0){throw 'Server acceptance did not complete cleanly; inspect diagnostics/log'}
    $afterOutput=& powershell -NoProfile -File (Join-Path $Workspace 'agent.ps1') -Action status
    if($LASTEXITCODE -ne 0){throw 'Cannot recheck workspace inputs'}
    $after=($afterOutput -join "`n") | ConvertFrom-Json
    if($after.inputFingerprint -ne $identity.inputFingerprint -or $after.toolchainFingerprint -ne $identity.toolchainFingerprint){throw 'Inputs changed during acceptance'}
    $outcome='PASS';$exitCode=0;$message='Actual server reached readiness, acceptance marker and clean shutdown'
}catch{$outcome='FAIL';$exitCode=1;$message=$_.Exception.Message}
finally {
    if($process -and $started -and -not $process.HasExited){
        try{$process.StandardInput.WriteLine('stop');$process.StandardInput.Flush()}catch{}
        if(-not $process.WaitForExit(5000)){& taskkill.exe /PID $process.Id /T /F | Out-Null}
    }
    if($writer){$writer.Dispose()}
}
$timing=[ordered]@{launch=$launchAt;runTaskObserved=$runTaskAt;readyObserved=$readyAt;markerObserved=$markerAt;stopSent=$stopAt;processExitObserved=$exitAt;drainComplete=$watch.Elapsed.TotalSeconds}
$result=[ordered]@{schemaVersion=1;status=$outcome;runId=$runId;workspace=$Workspace;elapsedSeconds=[Math]::Round($watch.Elapsed.TotalSeconds,2);runnerSha256=(Get-FileHash -LiteralPath $PSCommandPath).Hash;serverReady=$ready;acceptanceMarker=$marker;fatal=$fatal;message=$message;diagnostics=$diagnostics;log=$log;report=$report;inputFingerprint=$identity.inputFingerprint;toolchainFingerprint=$identity.toolchainFingerprint;scope='Focused energy proof: actual server-ticked line and local boundary/NBT checks; no client or separate-JVM restart claim.'}
$json=$result | ConvertTo-Json -Depth 6 -Compress
[IO.File]::WriteAllText($report,$json);$json
if($process){$process.Dispose()}
exit $exitCode
