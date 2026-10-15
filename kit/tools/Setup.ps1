[CmdletBinding()]
param([switch]$InstallMissing,[string]$GradleJavaHome,[string]$Java8Home,[ValidateSet('None','User','Machine')][string]$JavaHomeScope='None',[switch]$PromptMachineEnvironment)
$ErrorActionPreference = 'Stop'
[Console]::OutputEncoding = New-Object Text.UTF8Encoding($false)
$OutputEncoding = [Console]::OutputEncoding
# Prefer this shell's modules over paths inherited from a different PowerShell host.
$env:PSModulePath=(Join-Path $PSHOME 'Modules')+';'+$env:PSModulePath
$root = Split-Path -Parent $PSScriptRoot
. (Join-Path $PSScriptRoot 'JavaEnvironment.ps1')
function Write-KitSetupResult {
    param($Result)
    $json=$Result | ConvertTo-Json -Depth 5 -Compress
    try{
        $resultDir=Join-Path $root 'reports/setup';New-Item -ItemType Directory -Force -Path $resultDir | Out-Null
        [IO.File]::WriteAllText((Join-Path $resultDir 'setup-latest.json'),$json,(New-Object Text.UTF8Encoding($false)))
    }catch{[Console]::Error.WriteLine('Could not save setup result; keep this output: '+$_.Exception.Message)}
    $json
}
try {
    if($PromptMachineEnvironment -and -not (Test-KitAdministrator)){throw 'Right-click Setup.cmd and choose Run as administrator, then answer Y or N to the Windows Java settings prompt.'}
    if($PromptMachineEnvironment -and $JavaHomeScope -ne 'None'){throw 'Use PromptMachineEnvironment or JavaHomeScope, not both.'}
    $machineDecision=$null
    if($PromptMachineEnvironment){$machineDecision=Request-KitMachineEnvironmentChoice}
    [Console]::Error.WriteLine('Checking installed 64-bit JDKs (modern Gradle JVM and Java8 compiler)...')
    $selection=Get-KitJavaSelection -GradleJavaHome $GradleJavaHome -Java8Home $Java8Home
    if($PromptMachineEnvironment){$selection.gradle=Get-KitJava25}
    $installed=@()
    foreach($major in @(8,25)){
        $missing=if($major -eq 8){-not $selection.java8}else{-not $selection.gradle}
        if(-not $missing){continue}
        if(-not $InstallMissing){throw 'Required 64-bit JDK missing. Rerun Setup.ps1 -InstallMissing for WinGet Temurin25/8, or provide -GradleJavaHome and -Java8Home. A Java8 JRE is insufficient.'}
        $logDir=Join-Path $root 'reports/setup';New-Item -ItemType Directory -Force -Path $logDir | Out-Null
        $log=Join-Path $logDir ('winget-jdk'+$major+'-'+[DateTime]::UtcNow.ToString('yyyyMMddTHHmmssfff')+'.log')
        Invoke-KitWinget -Major $major -Log $log
        $installed+=@{major=$major;log=$log}
        $selection=Get-KitJavaSelection -GradleJavaHome $GradleJavaHome -Java8Home $Java8Home
        if($PromptMachineEnvironment){$selection.gradle=Get-KitJava25}
        if(($major -eq 8 -and -not $selection.java8) -or ($major -eq 25 -and -not $selection.gradle)){throw ('WinGet finished but required JDK'+$major+' could not be verified. Check installer log; use an explicit JDK home for custom paths.')}
    }
    Set-KitJavaProcess $selection
    $machineEnvironment=$null
    if($PromptMachineEnvironment){$machineEnvironment=Set-KitMachineEnvironment $selection -Decision $machineDecision}
    if($JavaHomeScope -ne 'None'){
        [Environment]::SetEnvironmentVariable('JAVA_HOME',$selection.gradle.home,$JavaHomeScope)
    }
    $setupDir=Join-Path $root 'reports/setup';New-Item -ItemType Directory -Force -Path $setupDir | Out-Null
    @{gradle=$selection.gradle.home;java8=$selection.java8.home} | ConvertTo-Json -Compress | Set-Content -Encoding UTF8 (Join-Path $setupDir 'java-homes.json')
    Write-KitSetupResult ([ordered]@{ schemaVersion=1; status='PASS'; projectRoot=$root; gradleJava=$selection.gradle.java; gradleJavaHome=$selection.gradle.home; java8Home=$selection.java8.home; java8Compiler=$selection.java8.javac; installed=$installed; javaHomeScope=$JavaHomeScope; machineEnvironment=$machineEnvironment; scope='verified JDK prerequisites; run agent.ps1 check environment for the pinned build' })
    exit 0
} catch {
    Write-KitSetupResult ([ordered]@{ schemaVersion=1; status='FAIL'; projectRoot=$root; message=$_.Exception.Message; machineEnvironment=$machineEnvironment })
    exit 1
}
