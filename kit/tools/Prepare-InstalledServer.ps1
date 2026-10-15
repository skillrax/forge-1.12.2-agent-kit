# Install a pinned normal Forge server; downloaded game files never belong in the kit ZIP.
[CmdletBinding()]
param([string]$CacheDirectory,[string]$Java8Home,[ValidateRange(30,1800)][int]$TimeoutSeconds=300)
$ErrorActionPreference='Stop'
$root=Split-Path -Parent $PSScriptRoot
. (Join-Path $root 'testing/InstalledRuntime.ps1')
. (Join-Path $PSScriptRoot 'JavaEnvironment.ps1')
try {
    $kit=Read-InstalledJson (Join-Path $root 'kit.json')
    if($kit.minecraft -ne '1.12.2' -or $kit.forge -ne '14.23.5.2859'){throw 'No verified installer recipe for these kit pins.'}
    if(-not $CacheDirectory){$CacheDirectory=Join-Path $root ('reports/installed-runtime/'+$kit.minecraft+'-'+$kit.forge)}
    $CacheDirectory=[IO.Path]::GetFullPath($CacheDirectory)
    $lockPath=Join-Path $CacheDirectory 'runtime-lock.json'
    if([IO.File]::Exists($lockPath)){
        $runtime=Test-InstalledRuntime $lockPath $kit
        @{status='PASS';reused=$true;runtimeLock=$lockPath;runtimeIdentity=$runtime.identity} | ConvertTo-Json -Compress
        exit 0
    }
    if(Test-Path -LiteralPath $CacheDirectory){throw 'Incomplete/unrecognized cache exists; preserve it and choose a new CacheDirectory.'}
    $java=(Get-KitJavaSelection -Java8Home $Java8Home).java8
    if(-not $java){throw 'A working Java 8 JDK is required.'}
    New-Item -ItemType Directory -Path $CacheDirectory | Out-Null
    $server=Join-Path $CacheDirectory 'server';New-Item -ItemType Directory -Path $server | Out-Null
    $url='https://maven.minecraftforge.net/net/minecraftforge/forge/1.12.2-14.23.5.2859/forge-1.12.2-14.23.5.2859-installer.jar'
    $installer=Join-Path $CacheDirectory 'installer.jar'
    [Net.ServicePointManager]::SecurityProtocol=[Net.SecurityProtocolType]::Tls12
    Invoke-WebRequest -UseBasicParsing -Uri $url -OutFile $installer -TimeoutSec 120
    $verified=Get-InstalledFile $installer 'dbba1cd1bf34a4b82148bac50a6150f63ffcb2d5dc7d88a0508c27fdcff15b76'
    $process=Invoke-InstalledProcess -Java (Join-Path $java.home 'bin/java.exe') -Arguments @('-Dfile.encoding=UTF-8','-jar',$installer,'--installServer',$server) -Directory $CacheDirectory -Log (Join-Path $CacheDirectory 'install.log') -TimeoutSeconds $TimeoutSeconds
    if($process.exitCode -ne 0){throw 'Forge installation failed; inspect install.log.'}
    foreach($name in @('forge-1.12.2-14.23.5.2859.jar','minecraft_server.1.12.2.jar')){if(-not [IO.File]::Exists((Join-Path $server $name))){throw "Installer did not produce $name"}}
    # The pinned installer validates upstream download checksums. Lock its successful output for reuse.
    $files=@(Get-ChildItem -LiteralPath $server -Recurse -File | Sort-Object FullName | ForEach-Object {
        @{path=$_.FullName.Substring($server.Length+1).Replace('\','/');sha256=(Get-FileHash -LiteralPath $_.FullName).Hash.ToLowerInvariant()}
    })
    $lock=@{schemaVersion=1;minecraft=$kit.minecraft;forge=$kit.forge;installer=@{url=$url;sha256=$verified.sha256};files=$files}
    Write-InstalledJson $lockPath $lock
    $runtime=Test-InstalledRuntime $lockPath $kit
    @{status='PASS';reused=$false;runtimeLock=$lockPath;runtimeIdentity=$runtime.identity} | ConvertTo-Json -Compress
    exit 0
} catch {@{status='FAIL';message=$_.Exception.Message;cache=$CacheDirectory} | ConvertTo-Json -Compress;exit 1}
