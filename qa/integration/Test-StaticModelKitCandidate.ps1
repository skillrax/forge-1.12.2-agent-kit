# Focused ZIP extraction, changed links, original starter build and server side loading.
[CmdletBinding()]
param([Parameter(Mandatory)][string]$Manifest,[string]$VerifiedManifest,[string]$VerifiedChecks)
$ErrorActionPreference='Stop'
$root=Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
try {
    $release=Get-Content -LiteralPath $Manifest -Raw | ConvertFrom-Json
    if((Get-FileHash -LiteralPath $release.archive).Hash -ne $release.archiveSha256){throw 'ZIP hash mismatch'}
    $allowlist=(Get-Content (Join-Path $root 'packaging/files.json') -Raw | ConvertFrom-Json).files
    if($release.fileCount -ne $allowlist.Count){throw 'Allowlist count mismatch'}
    $destination=Join-Path $root ('.work/static-kit-extraction/'+[Guid]::NewGuid().ToString('N'))
    Add-Type -AssemblyName System.IO.Compression.FileSystem
    [IO.Compression.ZipFile]::ExtractToDirectory($release.archive,$destination)
    $workspace=Join-Path $destination $release.archiveRoot
    $actual=@(Get-ChildItem -LiteralPath $workspace -Recurse -File -Force)
    if($actual.Count -ne $release.fileCount){throw 'Unexpected ZIP contents'}
    foreach($file in $release.files){
        if($allowlist -notcontains $file.path){throw "Not allowlisted: $($file.path)"}
        if((Get-FileHash (Join-Path $workspace $file.path)).Hash -ne $file.sha256){throw "Extracted hash mismatch: $($file.path)"}
        if($file.path -match '(^|/)(qa|evidence|external-testing-assets|evaluation|__pycache__)(/|$)'){throw 'Authoring/test assets leaked'}
    }
    $skill=Join-Path $workspace '.agents/skills/forge-1122-mod'
    $links=0
    foreach($relative in @('SKILL.md','references/static-models.md')){
        $file=Join-Path $skill $relative
        foreach($link in [regex]::Matches([IO.File]::ReadAllText($file),'\[[^\]]+\]\(([^)]+)\)')){
            $target=$link.Groups[1].Value
            if($target -match '^https?://'){continue}
            if(-not(Test-Path -LiteralPath (Join-Path (Split-Path $file) $target) -PathType Leaf)){throw "Broken packaged link: $relative -> $target"}
            $links++
        }
    }
    if($VerifiedManifest -or $VerifiedChecks){
        if(-not($VerifiedManifest -and $VerifiedChecks)){throw 'Supply both verified manifest and checks'}
        $previous=Get-Content -LiteralPath $VerifiedManifest -Raw | ConvertFrom-Json
        $checks=Get-Content -LiteralPath $VerifiedChecks -Raw | ConvertFrom-Json
        if($checks.status -ne 'PASS' -or $checks.archiveSha256 -ne $previous.archiveSha256){throw 'Previous checks do not match baseline'}
        if((Get-FileHash $previous.archive).Hash -ne $previous.archiveSha256){throw 'Baseline ZIP changed'}
        $changed=@()
        foreach($file in $release.files){
            $old=@($previous.files | Where-Object path -eq $file.path)
            if($old.Count -ne 1){throw 'Content set changed; run new build/loading checks'}
            if($old[0].sha256 -ne $file.sha256){
                if($file.path -notin @('.agents/skills/forge-1122-mod/references/static-models.md','THIRD-PARTY-NOTICES.md')){throw "Non-documentation input changed: $($file.path); run new checks"}
                $changed+=$file.path
            }
        }
        if($previous.fileCount -ne $release.fileCount){throw 'Content set changed'}
        if((Get-FileHash $checks.productionJar).Hash -ne $checks.productionJarSha256){throw 'Validated production JAR changed'}
        [ordered]@{status='PASS';version=$release.version;workspace=$workspace;archiveSha256=$release.archiveSha256;fileCount=$actual.Count;checkedRelativeLinks=$links;changedSinceBuild=$changed;verifiedChecks=[IO.Path]::GetFullPath($VerifiedChecks);buildReport=$checks.buildReport;serverReport=$checks.serverReport;productionJar=$checks.productionJar;productionJarSha256=$checks.productionJarSha256;scope='Final actual ZIP extracted/hash/link checked. Build/server/JAR checks reused only after exact manifest comparison proves every runtime, asset, exporter and build-tool input unchanged; only named guidance/notice files differ. No gameplay/client/cold-host claim.'} | ConvertTo-Json -Depth 5 -Compress
        exit 0
    }
    $shell=(Get-Process -Id $PID).Path
    $status=(& $shell -NoProfile -File (Join-Path $workspace 'agent.ps1') -Action status) | ConvertFrom-Json
    if($LASTEXITCODE -ne 0 -or $status.lastRun -or $status.projectRoot -ne $workspace){throw 'Wrong extracted root or inherited validation'}
    $starter=Join-Path $skill 'assets/static-model-starter/src/main'
    foreach($folder in @('java','resources')){
        $target=Join-Path $workspace ('src/main/'+$folder)
        New-Item -ItemType Directory -Force $target | Out-Null
        Get-ChildItem (Join-Path $starter $folder) | Copy-Item -Destination $target -Recurse -Force
    }
    $text=& $shell -NoProfile -File (Join-Path $workspace 'agent.ps1') -Action check -Profile mod -TimeoutSeconds 180
    if($LASTEXITCODE -ne 0){throw "Extracted starter build failed: $text"}
    $build=($text -join "`n") | ConvertFrom-Json
    if($build.status -ne 'PASS' -or $build.projectRoot -ne $workspace){throw 'Extracted starter build did not pass'}
    $jar=Get-ChildItem (Join-Path $workspace 'build/libs') -Filter '*-srg.jar' | Select-Object -First 1
    $archive=[IO.Compression.ZipFile]::OpenRead($jar.FullName)
    try {
        $names=@($archive.Entries.FullName)
        foreach($required in @('com/example/staticmodel/ModelStatue.class','com/example/staticmodel/StatueRenderer.class','assets/staticmodel/models/statue/sample.mesh','assets/staticmodel/textures/statue/checker.png')){
            if($names -notcontains $required){throw "Missing production asset/class: $required"}
        }
        if($names -match 'statuechecks|evaluation|bunny|nekopixil|\.py$|report\.json'){throw 'Unexpected QA/source assets in production JAR'}
    }finally{$archive.Dispose()}
    $text=& $shell -NoProfile -File (Join-Path $workspace 'agent.ps1') -Action check -Profile server -TimeoutSeconds 180
    if($LASTEXITCODE -ne 0){throw "Extracted server loading failed: $text"}
    $server=($text -join "`n") | ConvertFrom-Json
    if($server.status -ne 'PASS'){throw 'Server loading did not pass'}
    [ordered]@{status='PASS';version=$release.version;workspace=$workspace;archiveSha256=$release.archiveSha256;fileCount=$actual.Count;checkedRelativeLinks=$links;buildReport=$build.report;serverReport=$server.report;productionJar=$jar.FullName;productionJarSha256=(Get-FileHash $jar.FullName).Hash;scope='Actual ZIP extraction, allowlist/hashes, changed links, original starter build/JAR exclusions, dedicated-server side loading. Warm host caches; no new gameplay/client/cold-host claim.'} | ConvertTo-Json -Depth 5 -Compress
    exit 0
} catch {
    [ordered]@{status='FAIL';message=$_.Exception.Message;workspace=$workspace} | ConvertTo-Json -Compress
    exit 1
}
