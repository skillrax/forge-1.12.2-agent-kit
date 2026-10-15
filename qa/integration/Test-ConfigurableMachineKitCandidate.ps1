# Focused actual candidate extraction and promoted starter checks; no accepted campaign replay.
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
    $destination=Join-Path $root ('.work/config-machine-kit/'+[Guid]::NewGuid().ToString('N'))
    [IO.Compression.ZipFile]::ExtractToDirectory($release.archive,$destination)
    $workspace=Join-Path $destination $release.archiveRoot
    $files=@(Get-ChildItem $workspace -Recurse -File -Force)
    if($files.Count -ne $release.fileCount){throw 'ZIP file count mismatch'}
    foreach($file in $release.files){
        if($allowlist -notcontains $file.path -or (Get-FileHash (Join-Path $workspace $file.path)).Hash -ne $file.sha256){throw "Unexpected/changed file: $($file.path)"}
        if($file.path -match '(^|/)(qa|evidence|evaluation|external-testing-assets|__pycache__)(/|$)'){throw 'QA/external assets leaked'}
    }
    $skill=Join-Path $workspace '.agents/skills/forge-1122-mod';$links=0
    foreach($relative in @('SKILL.md','references/configurable-machines.md','references/machines-and-energy.md','references/item-transfer-and-routing.md')){
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
    if($identity.projectRoot -ne $workspace -or $identity.lastRun){throw 'Wrong root/inherited validation'}
    $starter=Join-Path $skill 'assets/configurable-machine-starter/src/main'
    foreach($folder in @('java','resources')){
        $target=Join-Path $workspace ('src/main/'+$folder);New-Item -ItemType Directory -Force $target | Out-Null
        Get-ChildItem (Join-Path $starter $folder) | Copy-Item -Destination $target -Recurse -Force
    }
    $build=Invoke-Json (Join-Path $workspace 'agent.ps1') @('-Action','check','-Profile','mod','-TimeoutSeconds','180')
    if($build.status -ne 'PASS'){throw 'Starter build failed'}
    $zip=[IO.Compression.ZipFile]::OpenRead($build.artifact.path)
    try {
        $entries=@($zip.Entries.FullName)
        foreach($required in @('com/example/configmachine/MachineMod.class','com/example/configmachine/MachineTile.class','assets/configmachine/models/item/recipe_upgrade.json')){if($entries -notcontains $required){throw "Missing starter content: $required"}}
        if($entries -match 'configmachinechecks|smelterchecks|GeneratorTile|PipeTile|evaluation|\.java$'){throw 'Proof helpers/QA leaked into starter JAR'}
    }finally{$zip.Dispose()}
    Copy-Item (Join-Path $root 'qa/fixtures/configurable-machine-starter-checks/src/evaluation') (Join-Path $workspace 'src/') -Recurse -Force
    Copy-Item (Join-Path $root 'qa/fixtures/configurable-smelter/build-overlay.gradle') $workspace
    Add-Content (Join-Path $workspace 'build.gradle') "`napply from: 'build-overlay.gradle'"
    $server=Invoke-Json (Join-Path $PSScriptRoot 'Invoke-SmelterServer.ps1') @('-Workspace',$workspace)
    $client=Invoke-Json (Join-Path $PSScriptRoot 'Invoke-SmelterClient.ps1') @('-Workspace',$workspace,'-TimeoutSeconds','150')
    if($server.status -ne 'PASS' -or $client.status -ne 'PASS'){throw 'Starter integration failed'}
    [ordered]@{status='PASS';workspace=$workspace;archive=$release.archive;archiveSha256=$release.archiveSha256;fileCount=$files.Count;checkedLinks=$links;build=$build;server=$server;client=$client;qaInProductionJar=$false;scope='Actual ZIP hashes/allowlist/links, new starter build, focused configured-count/registry/accounting/server checks and real client menu/control/diagnostic capture. Warm host; no new gameplay campaign or universal compatibility claim.'} | ConvertTo-Json -Depth 8 -Compress
}catch{[ordered]@{status='FAIL';message=$_.Exception.Message;workspace=$workspace} | ConvertTo-Json -Compress;exit 1}
