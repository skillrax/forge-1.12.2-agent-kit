[CmdletBinding()]
param([switch]$WithMcp, [switch]$SkipDependencies,[switch]$InstallMissing,[string]$GradleJavaHome,[string]$Java8Home,[ValidateSet('None','User','Machine')][string]$JavaHomeScope='None',[switch]$PromptMachineEnvironment,[switch]$PromptNode,[switch]$RepairMcp,[ValidateRange(1,7200)][int]$DependencyTimeoutSeconds=900)
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
    if($RepairMcp -and (-not $WithMcp -or $SkipDependencies)){throw 'RepairMcp requires WithMcp and cannot use SkipDependencies.'}
    if($PromptMachineEnvironment -and -not (Test-KitAdministrator)){throw 'Right-click Setup.cmd and choose Run as administrator, then answer Y or N to the Windows Java settings prompt.'}
    if($PromptMachineEnvironment -and $JavaHomeScope -ne 'None'){throw 'Use PromptMachineEnvironment or JavaHomeScope, not both.'}
    $machineDecision=$null
    if($PromptMachineEnvironment){$machineDecision=Request-KitMachineEnvironmentChoice}
    $nodeDecision=$null
    if($WithMcp -or $PromptNode){. (Join-Path $PSScriptRoot 'NodeEnvironment.ps1')}
    if($PromptNode){
        if($WithMcp){throw 'Use PromptNode or WithMcp, not both.'}
        $nodeDecision=Request-KitNodeChoice
        $WithMcp=($nodeDecision -eq 'Y')
    }
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
    $nodeVersion = $null
    $nodeSelection=$null
    $mcpState='NOT_REQUESTED'
    if ($WithMcp) {
        $mcpState='CHECKING'
        $nodeSelection=Get-KitNodeSelection
        if(-not $nodeSelection){
            if(-not $InstallMissing){throw 'MCP needs Node.js22+ and npm. Run Setup.ps1 -WithMcp -InstallMissing, or install Node.js LTS manually.'}
            $logDir=Join-Path $root 'reports/setup';New-Item -ItemType Directory -Force -Path $logDir | Out-Null
            $log=Join-Path $logDir ('winget-node-'+[DateTime]::UtcNow.ToString('yyyyMMddTHHmmssfff')+'.log')
            Invoke-KitWinget -PackageId OpenJS.NodeJS.LTS -Log $log
            $installed+=@{package='OpenJS.NodeJS.LTS';log=$log}
            $nodeSelection=Get-KitNodeSelection
            if(-not $nodeSelection){throw ('Node.js installer finished but Node.js22+ and npm could not be verified. Inspect '+$log+'; restart setup after resolving installer/PATH requirements.')}
        }
        $nodeVersion=$nodeSelection.version
        $env:PATH=(Split-Path -Parent $nodeSelection.node)+';'+$env:PATH
    }
    # Reject old absolute-path configs instead of preserving a silent wrong-project launch.
    $config = [IO.File]::ReadAllText((Join-Path $root '.codex/config.toml'))
    if ($config -notmatch "'./tools/Start-Mcp.ps1'" -or $config -match 'fullAgentic-GPT-v1') { throw 'Nonportable MCP config. Restore the checkout-relative .codex/config.toml before enabling MCP.' }
    $dependencyLog=$null
    $dependenciesReused=$false
    if($WithMcp){
        $dependencyDir=Join-Path $root 'reports/setup';New-Item -ItemType Directory -Force -Path $dependencyDir | Out-Null
        $markerPath=Join-Path $dependencyDir 'mcp-dependencies.json'
        $dependencyIdentity=(@('mcp/package.json','mcp/package-lock.json') | ForEach-Object {(Get-FileHash -LiteralPath (Join-Path $root $_) -Algorithm SHA256).Hash}) -join ':'
        $dependencyIdentity+=':'+$nodeSelection.node+':'+$nodeSelection.version+':'+$nodeSelection.npmVersion
        $marker=if(Test-Path -LiteralPath $markerPath){try{Get-Content -LiteralPath $markerPath -Raw -Encoding UTF8 | ConvertFrom-Json}catch{$null}}else{$null}
        $moduleLog=Join-Path $dependencyDir ('mcp-module-'+[DateTime]::UtcNow.ToString('yyyyMMddTHHmmssfffZ')+'.log')
        $moduleArguments=@('--input-type=module','-e',"import {pathToFileURL} from 'node:url'; const {createServer}=await import(pathToFileURL(process.argv[1])); const server=createServer(); await server.close();",(Join-Path $root 'mcp/tools.mjs'))
        if(-not $RepairMcp -and -not $SkipDependencies -and $marker.identity -eq $dependencyIdentity){
            try{Invoke-KitNodeProcess -Node $nodeSelection.node -Arguments $moduleArguments -Log $moduleLog -Stage 'MCP module load' -TimeoutSeconds 30;$dependenciesReused=$true}catch{[Console]::Error.WriteLine('Saved MCP installation failed its readiness check; reinstalling locked dependencies. '+$_.Exception.Message)}
        }
    }
    if ($WithMcp -and -not $SkipDependencies -and -not $dependenciesReused) {
        if(Test-Path -LiteralPath $markerPath){Remove-Item -LiteralPath $markerPath}
        $dependencyLog=Join-Path $dependencyDir ('npm-'+[DateTime]::UtcNow.ToString('yyyyMMddTHHmmssfffZ')+'.log')
        # stdout is the JSON contract. Preserve npm's human output in a log, not in that stream.
        [Console]::Error.WriteLine('MCP dependency log: '+$dependencyLog)
        Invoke-KitNodeProcess -Node $nodeSelection.node -Arguments @($nodeSelection.npm,'--prefix',(Join-Path $root 'mcp'),'ci','--no-audit','--no-fund') -Log $dependencyLog -Stage 'npm ci' -TimeoutSeconds $DependencyTimeoutSeconds
    }
    if($WithMcp){
        if(-not $dependenciesReused){Invoke-KitNodeProcess -Node $nodeSelection.node -Arguments $moduleArguments -Log $moduleLog -Stage 'MCP module load' -TimeoutSeconds 30}
        if(-not $SkipDependencies){@{identity=$dependencyIdentity} | ConvertTo-Json -Compress | Set-Content -LiteralPath $markerPath -Encoding UTF8}
        $mcpState='MODULE_LOAD_PASS'
        [Console]::Error.WriteLine('Node/npm and MCP module load passed. Restart Codex after a Node installation; app connection remains to be checked.')
    }
    $setupDir=Join-Path $root 'reports/setup';New-Item -ItemType Directory -Force -Path $setupDir | Out-Null
    @{gradle=$selection.gradle.home;java8=$selection.java8.home} | ConvertTo-Json -Compress | Set-Content -Encoding UTF8 (Join-Path $setupDir 'java-homes.json')
    Write-KitSetupResult ([ordered]@{ schemaVersion=1; status='PASS'; projectRoot=$root; node=$nodeVersion; nodeSelection=$nodeSelection; nodeDecision=$nodeDecision; mcpState=$mcpState; dependencyLog=$dependencyLog; moduleLog=$moduleLog; dependenciesReused=$dependenciesReused; gradleJava=$selection.gradle.java; gradleJavaHome=$selection.gradle.home; java8Home=$selection.java8.home; java8Compiler=$selection.java8.javac; installed=$installed; javaHomeScope=$JavaHomeScope; machineEnvironment=$machineEnvironment; mcpRequested=[bool]$WithMcp; scope='verified JDK prerequisites and requested Node/npm/MCP module load; app connection is separate; run environment check for actual pinned build' })
    exit 0
} catch {
    Write-KitSetupResult ([ordered]@{ schemaVersion=1; status='FAIL'; projectRoot=$root; message=$_.Exception.Message; mcpRequested=[bool]$WithMcp; mcpState=$mcpState; nodeDecision=$nodeDecision; dependencyLog=$dependencyLog; moduleLog=$moduleLog; machineEnvironment=$machineEnvironment })
    exit 1
}
