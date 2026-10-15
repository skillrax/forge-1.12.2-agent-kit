# Focused candidate extraction, RF starter adaptation and existing-tool sanity. No accepted campaign replay.
[CmdletBinding()]
param([Parameter(Mandatory)][string]$Manifest)
$ErrorActionPreference='Stop'
$root=Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
function Invoke-Json([string]$Script,[string[]]$Arguments){
    $result=& powershell -NoProfile -File $Script @Arguments
    if($LASTEXITCODE -ne 0){throw ($result -join "`n")}
    ($result -join "`n") | ConvertFrom-Json
}
try {
    $release=Get-Content -LiteralPath $Manifest -Raw | ConvertFrom-Json
    $allowlist=(Get-Content (Join-Path $root 'packaging/files.json') -Raw | ConvertFrom-Json).files
    if((Get-FileHash $release.archive).Hash -ne $release.archiveSha256 -or $release.fileCount -ne $allowlist.Count){throw 'ZIP identity/allowlist mismatch'}
    Add-Type -AssemblyName System.IO.Compression.FileSystem
    $destination=Join-Path $root ('.work/rf network kit/'+[Guid]::NewGuid().ToString('N'))
    [IO.Compression.ZipFile]::ExtractToDirectory($release.archive,$destination)
    $workspace=Join-Path $destination $release.archiveRoot
    if(@(Get-ChildItem $workspace -Recurse -File -Force).Count -ne $release.fileCount){throw 'ZIP count mismatch'}
    foreach($file in $release.files){
        if($allowlist -notcontains $file.path -or (Get-FileHash (Join-Path $workspace $file.path)).Hash -ne $file.sha256){throw "Unexpected/changed file: $($file.path)"}
        if($file.path -match '(^|/)(qa|evidence|evaluation|external-testing-assets|__pycache__)(/|$)'){throw 'QA leaked into ZIP'}
    }
    $skill=Join-Path $workspace '.agents/skills/forge-1122-mod';$links=0
    foreach($relative in @('SKILL.md','references/rf-networks-and-fuel.md','references/machines-and-energy.md')){
        $path=Join-Path $skill $relative
        foreach($link in [regex]::Matches([IO.File]::ReadAllText($path),'\[[^\]]+\]\(([^)]+)\)')){
            $target=$link.Groups[1].Value;if($target -match '^https?://'){continue}
            $parts=$target -split '#',2;$linked=Join-Path (Split-Path $path) $parts[0]
            if(-not(Test-Path -LiteralPath $linked -PathType Leaf)){throw "Broken link: $relative -> $target"}
            if($parts.Count -gt 1){
                $headings=[regex]::Matches([IO.File]::ReadAllText($linked),'(?m)^#+ (.+)$') | ForEach-Object {$_.Groups[1].Value.Trim().ToLowerInvariant() -replace '[^a-z0-9 -]','' -replace ' ','-'}
                if($headings -notcontains $parts[1]){throw "Broken anchor: $target"}
            }
            $links++
        }
    }
    $identity=Invoke-Json (Join-Path $workspace 'agent.ps1') @('-Action','status')
    if($identity.projectRoot -ne $workspace -or $identity.lastRun){throw 'Wrong root/inherited evidence'}
    $checkout=Join-Path $destination 'checkout sanity'
    $copy=Invoke-Json (Join-Path $workspace 'tools/New-Checkout.ps1') @('-Destination',$checkout)
    $copyStatus=Invoke-Json (Join-Path $checkout 'agent.ps1') @('-Action','status')
    if($copy.projectRoot -ne $checkout -or $copyStatus.projectRoot -ne $checkout -or $copyStatus.lastRun){throw 'Checkout copy/root sanity failed'}
    $starter=Join-Path $skill 'assets/rf-network-starter/src/main'
    foreach($folder in @('java','resources')){
        $target=Join-Path $workspace ('src/main/'+$folder);New-Item -ItemType Directory -Force $target | Out-Null
        Get-ChildItem (Join-Path $starter $folder) | Copy-Item -Destination $target -Recurse -Force
    }
    $setup=Invoke-Json (Join-Path $workspace 'tools/Setup.ps1') @('-WithMcp')
    $mcpOutput=& node (Join-Path $PSScriptRoot 'mcp-candidate-smoke.mjs') $workspace
    if($LASTEXITCODE -ne 0){throw ($mcpOutput -join "`n")}
    $mcp=Get-Content (Join-Path $workspace 'mcp-sanity.json') -Raw | ConvertFrom-Json
    $build=$mcp.mod
    $zip=[IO.Compression.ZipFile]::OpenRead($build.artifact.path)
    try{
        $entries=@($zip.Entries.FullName)
        foreach($required in @('com/example/networkstarter/NetworkMod.class','com/example/networkstarter/NetworkSettings.class','com/example/networkstarter/LoadedView.class','assets/networkstarter/blockstates/cable.json','pack.mcmeta')){if($entries -notcontains $required){throw "Missing starter content: $required"}}
        if($entries -match 'rfchecks|networkkitchecks|networkqueuedchecks|evaluation|EnvironmentProbe|\.java$'){throw 'QA leaked into production JAR'}
    }finally{$zip.Dispose()}
    $export=Invoke-Json (Join-Path $workspace 'tools/Export-Release.ps1') @()
    $source=Invoke-Json (Join-Path $workspace 'tools/Read-MappedSource.ps1') @('-Class','net.minecraft.world.gen.ChunkProviderServer','-Member','getLoadedChunk','-MaxLines','15')
    if(-not($source.lines.text -match 'unloadQueued = false')){throw 'Pinned mapped-source inspection failed'}
    Copy-Item (Join-Path $root 'qa/fixtures/rf-network-starter-checks/src/evaluation') (Join-Path $workspace 'src/') -Recurse -Force
    Copy-Item (Join-Path $root 'qa/fixtures/rf-network/build-overlay.gradle') $workspace
    Add-Content (Join-Path $workspace 'build.gradle') "`napply from: 'build-overlay.gradle'"
    $server=Invoke-Json (Join-Path $PSScriptRoot 'Invoke-RfNetworkServer.ps1') @('-Workspace',$workspace)
    if(-not([IO.File]::ReadAllText($server.log).Contains('RF_STARTER_ADAPTATION_PASS'))){throw 'Adaptation marker absent'}
    $observation=Invoke-Json (Join-Path $workspace 'tools/Record-Evidence.ps1') @('-Capability','behavior','-Outcome','PASS','-Method','log-marker','-Observation','Focused renamed starter accounting and pending-unload checks.','-EvidenceFiles',$server.log,'-Artifact',$export.artifact.path,'-Marker','RF_SERVER_PASS')
    $client=Invoke-Json (Join-Path $PSScriptRoot 'Invoke-RfNetworkClient.ps1') @('-Workspace',$workspace,'-TimeoutSeconds','150')
    [ordered]@{status='PASS';workspace=$workspace;archive=$release.archive;archiveSha256=$release.archiveSha256;fileCount=$release.fileCount;checkedLinks=$links;setup=$setup;checkout=$copyStatus.projectRoot;build=$build;modExport=$export.artifact;mcp=$mcp;mappedSource=$source.status;evidenceRecording=$observation;server=$server;client=$client;qaExcluded=$true;scope='Actual ZIP hashes/allowlist/links; extracted starter mod build/export; focused fuel/shared budgets/held port/safe dirty tests plus middle pending/actual unload; client controls/live sink; real MCP stdio status/validate/diagnostics. Warm host; no accepted campaign replay or desktop connection claim.'} | ConvertTo-Json -Depth 9 -Compress
}catch{[ordered]@{status='FAIL';message=$_.Exception.Message;workspace=$workspace} | ConvertTo-Json -Compress;exit 1}
