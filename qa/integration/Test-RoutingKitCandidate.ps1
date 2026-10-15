# Focused changed-candidate extraction/link/build check; no fault or whole-suite campaign.
[CmdletBinding()]
param([Parameter(Mandatory)][string]$Manifest)
$ErrorActionPreference='Stop'
[Console]::OutputEncoding=New-Object Text.UTF8Encoding($false)
$root=Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
try {
    $release=Get-Content -LiteralPath $Manifest -Raw | ConvertFrom-Json
    if((Get-FileHash -LiteralPath $release.archive).Hash -ne $release.archiveSha256){throw 'ZIP hash mismatch'}
    $allowlist=(Get-Content (Join-Path $root 'packaging/files.json') -Raw | ConvertFrom-Json).files
    if($release.fileCount -ne $allowlist.Count){throw 'Allowlist count mismatch'}
    $destination=Join-Path $root ('.work/routing-kit-extraction/'+[Guid]::NewGuid().ToString('N'))
    Add-Type -AssemblyName System.IO.Compression.FileSystem
    [IO.Compression.ZipFile]::ExtractToDirectory($release.archive,$destination)
    $workspace=Join-Path $destination $release.archiveRoot
    $actual=@(Get-ChildItem -LiteralPath $workspace -Recurse -File -Force)
    if($actual.Count -ne $release.fileCount){throw 'Unexpected ZIP contents'}
    foreach($file in $release.files){
        if($allowlist -notcontains $file.path){throw "Not allowlisted: $($file.path)"}
        if((Get-FileHash (Join-Path $workspace $file.path)).Hash -ne $file.sha256){throw "Extracted hash mismatch: $($file.path)"}
        if($file.path -match '(^|/)(qa|evidence|reports|chunkEvaluation|evaluation)(/|$)'){throw 'Authoring QA/evidence leaked'}
    }
    $skill=Join-Path $workspace '.agents/skills/forge-1122-mod'
    $checkedLinks=0
    foreach($relative in @('SKILL.md','references/machines-and-energy.md','references/fluids-and-pickup.md','references/item-transfer-and-routing.md')){
        $file=Join-Path $skill $relative
        $markdown=[IO.File]::ReadAllText($file)
        foreach($link in [regex]::Matches($markdown,'\[[^\]]+\]\(([^)]+)\)')){
            $target=$link.Groups[1].Value
            if($target -match '^https?://'){continue}
            $parts=$target -split '#',2
            $linked=if($parts[0]){Join-Path (Split-Path $file) $parts[0]}else{$file}
            if(-not(Test-Path -LiteralPath $linked -PathType Leaf)){throw "Broken packaged link: $relative -> $target"}
            if($parts.Count -gt 1){
                $headings=[regex]::Matches([IO.File]::ReadAllText($linked),'(?m)^#+ (.+)$') | ForEach-Object {$_.Groups[1].Value.Trim().ToLowerInvariant() -replace '[^a-z0-9 -]','' -replace ' ','-'}
                if($headings -notcontains $parts[1]){throw "Missing section: $relative -> $target"}
            }
            $checkedLinks++
        }
    }
    $shell=(Get-Process -Id $PID).Path
    $status=(& $shell -NoProfile -File (Join-Path $workspace 'agent.ps1') -Action status) | ConvertFrom-Json
    if($LASTEXITCODE -ne 0 -or $status.lastRun -or $status.projectRoot -ne $workspace){throw 'Wrong extracted root or inherited validation'}
    $text=& $shell -NoProfile -File (Join-Path $workspace 'agent.ps1') -Action check -Profile build -TimeoutSeconds 120
    if($LASTEXITCODE -ne 0){throw "Extracted foundation build failed: $text"}
    $build=($text -join "`n") | ConvertFrom-Json
    if($build.status -ne 'PASS' -or $build.projectRoot -ne $workspace){throw 'Extracted build did not pass'}
    [ordered]@{status='PASS';version=$release.version;workspace=$workspace;archiveSha256=$release.archiveSha256;fileCount=$actual.Count;checkedRelativeLinks=$checkedLinks;buildReport=$build.report;runId=$build.runId;scope='Actual ZIP extraction, exact allowlist/hashes, changed-reference relative links/anchors, fresh root and focused foundation build; warm host caches, no setup/client/modpack claim'} | ConvertTo-Json -Depth 5 -Compress
    exit 0
} catch {
    [ordered]@{status='FAIL';message=$_.Exception.Message;workspace=$workspace} | ConvertTo-Json -Compress
    exit 1
}
