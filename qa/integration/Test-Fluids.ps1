# Optional reproduction of the one small fluids proof; no fault or load campaign.
[CmdletBinding()]
param([string]$Workspace)
$ErrorActionPreference='Stop'
$taskRoot=Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
if(-not $Workspace){$Workspace=Join-Path $taskRoot ('.work/fluids-'+[DateTime]::UtcNow.ToString('yyyyMMddTHHmmssfffZ'))}
$Workspace=[IO.Path]::GetFullPath($Workspace)
function Invoke-FluidJson([string]$Script,[string[]]$Arguments) {
    $taskOutput=& powershell -NoProfile -File $Script @Arguments
    if($LASTEXITCODE -ne 0){throw ('Failed '+$Script+': '+($taskOutput -join "`n"))}
    return ($taskOutput -join "`n") | ConvertFrom-Json
}
try {
    $taskCheckout=Invoke-FluidJson (Join-Path $taskRoot 'kit/tools/New-Checkout.ps1') @('-Destination',$Workspace)
    $taskFixture=Join-Path $taskRoot 'qa/fixtures/fluids'
    Copy-Item -LiteralPath (Join-Path $taskFixture 'src/main') -Destination (Join-Path $Workspace 'src') -Recurse -Force
    Copy-Item -LiteralPath (Join-Path $taskFixture 'src/evaluation') -Destination (Join-Path $Workspace 'src') -Recurse -Force
    Copy-Item -LiteralPath (Join-Path $taskFixture 'build-overlay.gradle') -Destination $Workspace
    $taskBuild=[IO.File]::ReadAllText((Join-Path $Workspace 'build.gradle')).Replace("group = 'com.example'","group = 'dev.agentic.fluids'")
    $taskVersion=(Get-Content -LiteralPath (Join-Path $taskFixture 'src/main/resources/mcmod.info') -Raw | ConvertFrom-Json)[0].version
    $taskBuild=$taskBuild.Replace("version = '1.0.0'",("version = '"+$taskVersion+"'"))
    $taskBuild+="`nbase { archivesName = 'fluid-proof' }`napply from: 'build-overlay.gradle'`n"
    [IO.File]::WriteAllText((Join-Path $Workspace 'build.gradle'),$taskBuild,(New-Object Text.UTF8Encoding($false)))
    $taskSetup=Invoke-FluidJson (Join-Path $Workspace 'tools/Setup.ps1') @()
    $taskMod=Invoke-FluidJson (Join-Path $Workspace 'agent.ps1') @('-Action','check','-Profile','mod')
    $taskBehavior=Invoke-FluidJson (Join-Path $PSScriptRoot 'Invoke-FluidServer.ps1') @('-Workspace',$Workspace)
    Add-Type -AssemblyName System.IO.Compression.FileSystem
    $taskJar=[IO.Compression.ZipFile]::OpenRead($taskMod.artifact.path)
    try {
        if($taskJar.Entries.FullName -match 'fluidchecks|EnvironmentProbe|\.java$'){throw 'QA classes/source in production JAR'}
    }finally{$taskJar.Dispose()}
    $taskExport=Invoke-FluidJson (Join-Path $Workspace 'tools/Export-Release.ps1') @()
    $taskDirectory=Join-Path $taskRoot 'evidence/fluids'
    New-Item -ItemType Directory -Path $taskDirectory -Force | Out-Null
    $taskReport=Join-Path $taskDirectory ($taskBehavior.runId+'.json')
    $taskResult=[ordered]@{status='PASS';workspace=$Workspace;mod=$taskMod.runId;behavior=$taskBehavior;
        artifact=$taskExport.artifact;qaExcluded=$true;gameplay='PENDING_USER';scope='One small fluid line and local blocked/full boundaries; no fault, load, client or JVM-restart campaign.'}
    [IO.File]::WriteAllText($taskReport,($taskResult | ConvertTo-Json -Depth 6),(New-Object Text.UTF8Encoding($false)))
    [ordered]@{status='PASS';report=$taskReport;artifact=$taskExport.artifact} | ConvertTo-Json -Depth 3 -Compress
}catch{
    [ordered]@{status='FAIL';workspace=$Workspace;message=$_.Exception.Message} | ConvertTo-Json -Compress
    exit 1
}
