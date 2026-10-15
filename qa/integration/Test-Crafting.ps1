# Optional reproduction of the one small automated-crafting proof; no fault or load campaign.
[CmdletBinding()]
param([string]$Workspace)
$ErrorActionPreference='Stop'
$taskRoot=Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
if(-not $Workspace){$Workspace=Join-Path $taskRoot ('.work/automated-crafting-'+[DateTime]::UtcNow.ToString('yyyyMMddTHHmmssfffZ'))}
$Workspace=[IO.Path]::GetFullPath($Workspace)
function Invoke-CraftingJson([string]$Script,[string[]]$Arguments) {
    $taskOutput=& powershell -NoProfile -File $Script @Arguments
    if($LASTEXITCODE -ne 0){throw ('Failed '+$Script+': '+($taskOutput -join "`n"))}
    return ($taskOutput -join "`n") | ConvertFrom-Json
}
try {
    $taskCheckout=Invoke-CraftingJson (Join-Path $taskRoot 'kit/tools/New-Checkout.ps1') @('-Destination',$Workspace)
    $taskFixture=Join-Path $taskRoot 'qa/fixtures/automated-crafting'
    Copy-Item -LiteralPath (Join-Path $taskFixture 'src/main') -Destination (Join-Path $Workspace 'src') -Recurse -Force
    Copy-Item -LiteralPath (Join-Path $taskFixture 'src/evaluation') -Destination (Join-Path $Workspace 'src') -Recurse -Force
    Copy-Item -LiteralPath (Join-Path $taskFixture 'build-overlay.gradle') -Destination $Workspace
    $taskBuild=[IO.File]::ReadAllText((Join-Path $Workspace 'build.gradle')).Replace("group = 'com.example'","group = 'dev.agentic.crafting'")
    $taskVersion=(Get-Content -LiteralPath (Join-Path $taskFixture 'src/main/resources/mcmod.info') -Raw | ConvertFrom-Json)[0].version
    $taskBuild=$taskBuild.Replace("version = '1.0.0'",("version = '"+$taskVersion+"'"))
    $taskBuild+="`nbase { archivesName = 'automated-crafting-proof' }`napply from: 'build-overlay.gradle'`n"
    [IO.File]::WriteAllText((Join-Path $Workspace 'build.gradle'),$taskBuild,(New-Object Text.UTF8Encoding($false)))
    $taskSetup=Invoke-CraftingJson (Join-Path $Workspace 'tools/Setup.ps1') @()
    $taskMod=Invoke-CraftingJson (Join-Path $Workspace 'agent.ps1') @('-Action','check','-Profile','mod')
    $taskBehavior=Invoke-CraftingJson (Join-Path $PSScriptRoot 'Invoke-CraftingServer.ps1') @('-Workspace',$Workspace)
    $taskClient=Invoke-CraftingJson (Join-Path $PSScriptRoot 'Invoke-CraftingClient.ps1') @('-Workspace',$Workspace,'-TimeoutSeconds','180')
    Add-Type -AssemblyName System.IO.Compression.FileSystem
    $taskJar=[IO.Compression.ZipFile]::OpenRead($taskMod.artifact.path)
    try {
        if($taskJar.Entries.FullName -match 'craftingchecks|EnvironmentProbe|\.java$'){throw 'QA classes/source in production JAR'}
    }finally{$taskJar.Dispose()}
    $taskExport=Invoke-CraftingJson (Join-Path $Workspace 'tools/Export-Release.ps1') @()
    $taskDirectory=Join-Path $taskRoot 'evidence/automated-crafting'
    New-Item -ItemType Directory -Path $taskDirectory -Force | Out-Null
    $taskReport=Join-Path $taskDirectory ($taskBehavior.runId+'.json')
    $taskResult=[ordered]@{status='PASS';workspace=$Workspace;mod=$taskMod.runId;behavior=$taskBehavior;
        client=$taskClient;artifact=$taskExport.artifact;qaExcluded=$true;gameplay='PENDING_USER';scope='Focused actual server accounting and graphical container packets/screenshots; NBT restoration, no separate-JVM restart or broad recipe compatibility claim.'}
    [IO.File]::WriteAllText($taskReport,($taskResult | ConvertTo-Json -Depth 6),(New-Object Text.UTF8Encoding($false)))
    [ordered]@{status='PASS';report=$taskReport;artifact=$taskExport.artifact} | ConvertTo-Json -Depth 3 -Compress
}catch{
    [ordered]@{status='FAIL';workspace=$Workspace;message=$_.Exception.Message} | ConvertTo-Json -Compress
    exit 1
}
