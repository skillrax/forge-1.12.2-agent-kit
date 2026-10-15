# Optional behavior test of exact production artifacts in a normally installed Forge server.
[CmdletBinding()]
param([Parameter(Mandatory)][string]$Manifest,[string]$RuntimeLock,[string]$Java8Home,[switch]$AcceptEula)
$ErrorActionPreference='Stop'
$root=Split-Path -Parent $PSScriptRoot
. (Join-Path $root 'testing/InstalledRuntime.ps1')
. (Join-Path $PSScriptRoot 'JavaEnvironment.ps1')
$runId=[DateTime]::UtcNow.ToString('yyyyMMddTHHmmssfffZ')+'-'+[Guid]::NewGuid().ToString('N').Substring(0,8)
$reportDirectory=Join-Path $root ('reports/installed-tests/'+$runId)
New-Item -ItemType Directory -Path $reportDirectory -Force | Out-Null
$reportPath=Join-Path $reportDirectory 'report.json'
$result=[ordered]@{schemaVersion=1;runId=$runId;status='FAIL';failureKind='infrastructure';phase='preflight';scope='installedServer';report=$reportPath;installedClient='NOT_RUN';artifacts=@();assertions=@()}
$exitCode=1
try {
    if(-not $AcceptEula){throw 'Use -AcceptEula only after accepting the Minecraft EULA at https://www.minecraft.net/eula for this local test server.'}
    $Manifest=[IO.Path]::GetFullPath($Manifest);$base=Split-Path -Parent $Manifest
    $spec=Read-InstalledJson $Manifest
    $manifestHash=(Get-FileHash -LiteralPath $Manifest).Hash.ToLowerInvariant()
    if($spec.schemaVersion -ne 1 -or $spec.mode -ne 'server'){throw 'Expected schemaVersion 1, mode server.'}
    if($spec.scenario -notmatch '^[a-z][a-z0-9-]{0,63}$'){throw 'A named scenario is required.'}
    $result.scenario=$spec.scenario
    $expected=@($spec.expectedAssertions)
    if($expected.Count -lt 1 -or @($expected | Select-Object -Unique).Count -ne $expected.Count){throw 'Declare nonempty unique expectedAssertions.'}
    foreach($name in $expected){if($name -notmatch '^[a-z][a-z0-9.-]{0,95}$'){throw 'Invalid expected assertion name.'}}
    $timeout=[int]$spec.timeoutSeconds
    if($timeout -lt 15 -or $timeout -gt 600){throw 'timeoutSeconds must be between 15 and 600.'}
    $maxTicks=1200
    if($null -ne $spec.maxTicks){$maxTicks=[int]$spec.maxTicks}
    if($maxTicks -lt 1 -or $maxTicks -gt 12000){throw 'maxTicks must be between 1 and 12000.'}
    $kit=Read-InstalledJson (Join-Path $root 'kit.json')
    $artifacts=New-Object 'System.Collections.Generic.List[object]'
    $subject=$spec.subject
    if($subject.exportManifest){
        $exportPath=Resolve-InstalledPath $base $subject.exportManifest
        $export=Read-InstalledJson $exportPath
        $subjectPath=Resolve-InstalledPath (Split-Path -Parent $exportPath) $export.artifact.path
        $subjectHash=$export.artifact.sha256
        $result.subjectExport=@{path=$exportPath;sha256=(Get-FileHash -LiteralPath $exportPath).Hash.ToLowerInvariant()}
    }else{$subjectPath=Resolve-InstalledPath $base $subject.path;$subjectHash=$subject.sha256}
    $artifacts.Add(@{role='subject';modId=$subject.modId;path=$subjectPath;sha256=$subjectHash})
    foreach($dependency in @($spec.dependencies)){
        if($null -ne $dependency){$artifacts.Add(@{role='dependency';modId=$dependency.modId;path=(Resolve-InstalledPath $base $dependency.path);sha256=$dependency.sha256})}
    }
    if($spec.driver.modId -ne 'installedtestdriver'){throw 'Driver must declare modId installedtestdriver.'}
    $artifacts.Add(@{role='driver';modId=$spec.driver.modId;path=(Resolve-InstalledPath $base $spec.driver.path);sha256=$spec.driver.sha256})
    $ids=@{};$names=@{}
    foreach($artifact in $artifacts){
        if($artifact.modId -notmatch '^[a-z][a-z0-9_]{0,63}$' -or $artifact.modId -in @('minecraft','mcp','FML','forge') -or $ids.ContainsKey($artifact.modId)){throw 'Invalid, reserved or duplicate artifact modId.'}
        $ids[$artifact.modId]=$true
        if([IO.Path]::GetExtension($artifact.path) -ne '.jar'){throw 'Only explicit JAR artifacts are supported.'}
        $name=[IO.Path]::GetFileName($artifact.path)
        if($names.ContainsKey($name)){throw 'Artifact filenames must be unique.'};$names[$name]=$true
        $verified=Get-InstalledFile $artifact.path $artifact.sha256
        $artifact.path=$verified.path;$artifact.sha256=$verified.sha256
    }
    $catalog=Read-InstalledScenarioCatalog $artifacts[$artifacts.Count-1].path
    if(@($catalog.scenarios | Where-Object {$_.id -ceq $spec.scenario}).Count -ne 1){throw ('Unknown scenario: '+$spec.scenario+'; available: '+(($catalog.scenarios | Select-Object -First 20 -ExpandProperty id) -join ', '))}
    if(-not $RuntimeLock){$RuntimeLock=Resolve-InstalledPath $base $spec.runtimeLock}else{$RuntimeLock=[IO.Path]::GetFullPath($RuntimeLock)}
    $runtime=Test-InstalledRuntime $RuntimeLock $kit
    $java=(Get-KitJavaSelection -Java8Home $Java8Home).java8
    if(-not $java){throw 'Java 8 JDK required for installed Forge.'}
    $game=Join-Path $reportDirectory 'game';New-Item -ItemType Directory -Path $game | Out-Null
    foreach($entry in $runtime.lock.files){
        $destination=Join-Path $game $entry.path
        New-Item -ItemType Directory -Force -Path (Split-Path -Parent $destination) | Out-Null
        Copy-Item -LiteralPath (Join-Path $runtime.root $entry.path) -Destination $destination
        $null=Get-InstalledFile $destination $entry.sha256
    }
    $mods=Join-Path $game 'mods';New-Item -ItemType Directory -Path $mods | Out-Null
    foreach($artifact in $artifacts){
        $artifact.stagedPath=Join-Path $mods ([IO.Path]::GetFileName($artifact.path))
        Copy-Item -LiteralPath $artifact.path -Destination $artifact.stagedPath
        $null=Get-InstalledFile $artifact.stagedPath $artifact.sha256
    }
    # Selecting a free loopback port is best effort; a subsequent bind failure fails this run.
    $listener=New-Object Net.Sockets.TcpListener([Net.IPAddress]::Loopback,0)
    try{$listener.Start();$port=$listener.LocalEndpoint.Port}finally{$listener.Stop()}
    $properties="server-ip=127.0.0.1`nserver-port=$port`nonline-mode=true`nlevel-name=world`nlevel-type=FLAT`ngenerator-settings=3;minecraft:bedrock,2*minecraft:dirt,minecraft:grass;1;`nspawn-protection=0`nspawn-animals=true`nspawn-monsters=true`ngenerate-structures=false`nmax-tick-time=30000`nview-distance=3`n"
    [IO.File]::WriteAllText((Join-Path $game 'server.properties'),$properties)
    [IO.File]::WriteAllText((Join-Path $game 'eula.txt'),"eula=true`n")
    $driverResult=Join-Path $reportDirectory 'driver-result.json'
    $requestPath=Join-Path $reportDirectory 'request.json'
    $request=@{schemaVersion=1;runId=$runId;scenario=$spec.scenario;maxTicks=$maxTicks;parameters=$spec.parameters;artifacts=@($artifacts.ToArray());resultPath=$driverResult}
    Write-InstalledJson $requestPath $request
    $result.manifest=@{path=$Manifest;sha256=$manifestHash};$result.artifacts=@($artifacts.ToArray())
    $result.runtime=@{lock=$RuntimeLock;sha256=$runtime.identity;minecraft=$kit.minecraft;forge=$kit.forge;java=$java}
    $result.configuration=@{requestSha256=(Get-FileHash -LiteralPath $requestPath).Hash;serverPropertiesSha256=(Get-FileHash -LiteralPath (Join-Path $game 'server.properties')).Hash;freshWorld=$true;port=$port;eulaAccepted=$true}
    $result.log=Join-Path $reportDirectory 'server.log'
    $result.process=Invoke-InstalledProcess -Java (Join-Path $java.home 'bin/java.exe') -Arguments @('-Dfile.encoding=UTF-8','-Xms256M','-Xmx1024M',('-Dkit.test.request='+$requestPath),'-jar',(Join-Path $game $runtime.forgeJar),'nogui') -Directory $game -Log $result.log -TimeoutSeconds $timeout
    [Console]::Error.WriteLine('Verifying staged files after execution.')
    if((Get-FileHash -LiteralPath $Manifest).Hash -ne $manifestHash -or (Get-FileHash -LiteralPath $RuntimeLock).Hash -ne $runtime.identity){throw 'Manifest/runtime lock changed during execution.'}
    if($result.subjectExport -and (Get-FileHash -LiteralPath $result.subjectExport.path).Hash -ne $result.subjectExport.sha256){throw 'Subject export manifest changed during execution.'}
    if((Get-FileHash -LiteralPath $requestPath).Hash -ne $result.configuration.requestSha256){throw 'Driver request changed during execution.'}
    foreach($artifact in $artifacts){$null=Get-InstalledFile $artifact.path $artifact.sha256;$null=Get-InstalledFile $artifact.stagedPath $artifact.sha256}
    foreach($entry in $runtime.lock.files){$null=Get-InstalledFile (Join-Path $game $entry.path) $entry.sha256}
    if($result.process.exitCode -ne 0){throw 'Installed server exited unsuccessfully.'}
    if(-not [IO.File]::Exists($driverResult)){throw 'No structured driver result; startup alone is not behavior proof.'}
    [Console]::Error.WriteLine('Reading structured driver result.')
    $driver=Read-InstalledJson $driverResult
    if($driver.schemaVersion -ne 1 -or $driver.runId -cne $runId -or $driver.scenario -cne $spec.scenario){throw 'Driver result identity mismatch.'}
    $result.driverResult=$driverResult;$result.assertions=@($driver.assertions);$result.loadedMods=@($driver.loadedMods);$result.phase=$driver.phase;$result.serverTicks=$driver.serverTicks;$result.scenarioClass=$driver.scenarioClass;$result.scenarioSource=$driver.scenarioSource;$result.failure=$driver.failure
    if($driver.status -ne 'PASS'){$result.failureKind='behavior';throw ('Driver failed: '+$driver.message)}
    $selected=@($catalog.scenarios | Where-Object {$_.id -ceq $spec.scenario})[0]
    if($driver.scenarioClass -cne $selected.className -or [IO.Path]::GetFullPath($driver.scenarioSource) -ne $artifacts[$artifacts.Count-1].stagedPath){throw 'Project scenario class/catalog origin mismatch.'}
    $seen=@{}
    foreach($assertion in @($driver.assertions)){
        if($assertion.passed -isnot [bool] -or -not $assertion.passed -or $seen.ContainsKey($assertion.name)){throw 'Failed/duplicate/malformed driver assertion.'}
        $seen[$assertion.name]=$true
    }
    foreach($name in $expected){if(-not $seen.ContainsKey($name)){throw "Required assertion missing: $name"}}
    foreach($artifact in $artifacts){
        $loaded=@($driver.loadedMods | Where-Object {$_.modId -ceq $artifact.modId})
        if($loaded.Count -ne 1 -or [IO.Path]::GetFullPath($loaded[0].source) -ne $artifact.stagedPath -or [IO.Path]::GetFullPath($loaded[0].classSource) -ne $artifact.stagedPath){throw ('Loaded artifact origin mismatch: '+$artifact.modId)}
    }
    $fatal=Select-String -LiteralPath $result.log -Pattern 'Exception in server tick loop|Encountered an unexpected exception|A fatal exception occurred|Failed to save chunk' | Select-Object -First 1
    if($fatal){throw ('Fatal server log: '+$fatal.Line)}
    $result.status='PASS';$result.failureKind=$null;$result.message='Exact production artifacts loaded; required world assertions passed and server exited cleanly.';$exitCode=0
} catch {
    $result.message=$_.Exception.Message
    # Substring strips Get-Content's extended PowerShell properties before JSON serialization.
    if($result.log -and [IO.File]::Exists($result.log)){$result.diagnostics=@(Get-Content -LiteralPath $result.log -Encoding UTF8 -Tail 12 | ForEach-Object {$_.Substring(0,[Math]::Min(300,$_.Length))})}
}
[Console]::Error.WriteLine('Writing installed test report.')
Write-InstalledJson $reportPath $result
@{schemaVersion=1;status=$result.status;runId=$runId;scenario=$result.scenario;phase=$result.phase;serverTicks=$result.serverTicks;scope=$result.scope;failureKind=$result.failureKind;message=$result.message;assertionCount=@($result.assertions).Count;report=$reportPath;log=$result.log;installedClient='NOT_RUN'} | ConvertTo-Json -Compress
exit $exitCode
