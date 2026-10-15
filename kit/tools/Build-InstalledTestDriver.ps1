# Build only the separate installed test driver; do not export or rebuild the subject mod.
[CmdletBinding()]
param([ValidateRange(30,1800)][int]$TimeoutSeconds=300)
$ErrorActionPreference='Stop'
$root=Split-Path -Parent $PSScriptRoot
. (Join-Path $root 'testing/InstalledRuntime.ps1')
. (Join-Path $PSScriptRoot 'JavaEnvironment.ps1')
$directory=Join-Path $root ('reports/installed-driver/'+[DateTime]::UtcNow.ToString('yyyyMMddTHHmmssfffZ')+'-'+[Guid]::NewGuid().ToString('N').Substring(0,8))
New-Item -ItemType Directory -Path $directory -Force | Out-Null
try {
    $selection=Get-KitJavaSelection
    if(-not $selection.gradle -or -not $selection.java8){throw 'Modern Gradle JDK and Java 8 JDK required.'}
    Set-KitJavaProcess $selection
    $log=Join-Path $directory 'build.log'
    $process=Invoke-InstalledProcess -Java (Join-Path $selection.gradle.home 'bin/java.exe') -Arguments @('-classpath',(Join-Path $root 'gradle/wrapper/gradle-wrapper.jar'),'org.gradle.wrapper.GradleWrapperMain','renameInstalledTestDriverJar','--console=plain','--no-daemon') -Directory $root -Log $log -TimeoutSeconds $TimeoutSeconds
    if($process.exitCode -ne 0){throw "Driver build failed; inspect $log"}
    $built=Join-Path $root 'build/libs/installed-test-driver-1.0.0-srg.jar'
    if(-not [IO.File]::Exists($built)){throw 'Expected separate remapped driver JAR missing.'}
    $destination=Join-Path $directory ([IO.Path]::GetFileName($built))
    Copy-Item -LiteralPath $built -Destination $destination
    $catalog=Read-InstalledScenarioCatalog $destination
    $result=@{schemaVersion=1;status='PASS';modId='installedtestdriver';path=$destination;sha256=(Get-FileHash -LiteralPath $destination).Hash.ToLowerInvariant();scenarioCount=@($catalog.scenarios).Count;scenarios=@($catalog.scenarios | Select-Object -First 20 -ExpandProperty id);log=$log;scope='Separate installed-test driver build; no subject/gameplay claim.'}
    Write-InstalledJson (Join-Path $directory 'manifest.json') $result
    $result | ConvertTo-Json -Compress
    exit 0
}catch{
    $diagnostics=@()
    if($log -and [IO.File]::Exists($log)){
        $diagnostics=@(Select-String -LiteralPath $log -Encoding UTF8 -Pattern 'error:|symbol:|location:|unable to resolve|Duplicate installed|No installed scenarios|Invalid installed|What went wrong' | Select-Object -First 8 | ForEach-Object {$_.Line.Substring(0,[Math]::Min(300,$_.Line.Length))})
    }
    @{schemaVersion=1;status='FAIL';message=$_.Exception.Message;directory=$directory;log=$log;diagnostics=$diagnostics} | ConvertTo-Json -Compress;exit 1
}
