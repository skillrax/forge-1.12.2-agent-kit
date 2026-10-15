[CmdletBinding()]
param([string]$Workspace, [string]$ArchiveManifest, [ValidateSet('Reproduce','Regression')][string]$Mode='Reproduce')
$ErrorActionPreference = 'Stop'
[Console]::OutputEncoding = New-Object Text.UTF8Encoding($false)
$OutputEncoding = [Console]::OutputEncoding
$root = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$shell = (Get-Process -Id $PID).Path
$source = Join-Path $root 'qa/fixtures/aegis-apple'
$cases = New-Object 'System.Collections.Generic.List[object]'
function Invoke-Check {
    param([string]$Profile, [int]$ExpectedExit=0)
    $output = & $shell -NoProfile -File (Join-Path $Workspace 'agent.ps1') -Action check -Profile $Profile -TimeoutSeconds 180
    if ($LASTEXITCODE -ne $ExpectedExit) { throw "Unexpected $Profile exit: $output" }
    $result = ($output -join "`n") | ConvertFrom-Json
    if (($ExpectedExit -eq 0 -and $result.status -ne 'PASS') -or ($ExpectedExit -ne 0 -and $result.status -ne 'FAIL')) { throw 'Validation status/exit mismatch.' }
    $cases.Add(@{profile=$Profile; status=$result.status; runId=$result.runId; elapsedSeconds=$result.elapsedSeconds})
    return $result
}
function Assert-Marker {
    param($Result, [string]$Marker)
    if (-not [IO.File]::ReadAllText($Result.log).Contains($Marker)) { throw "Assertion marker missing: $Marker; $($Result.log)" }
}
try {
    if (-not $ArchiveManifest) { $active = Get-Content -Encoding UTF8 (Join-Path $root 'kit/kit.json') -Raw | ConvertFrom-Json; $ArchiveManifest = Join-Path $root ('releases/' + $active.version + '/manifest.json') }
    $archive = Get-Content -Encoding UTF8 -LiteralPath $ArchiveManifest -Raw | ConvertFrom-Json
    if ((Get-FileHash -LiteralPath $archive.archive -Algorithm SHA256).Hash -ne $archive.archiveSha256) { throw 'Frozen archive hash mismatch.' }
    if (-not $Workspace) {
        $destination = Join-Path $root ('.work/aegis-repeat/' + [Guid]::NewGuid().ToString('N'))
        Add-Type -AssemblyName System.IO.Compression.FileSystem
        [IO.Compression.ZipFile]::ExtractToDirectory($archive.archive, $destination)
        $Workspace = Join-Path $destination $archive.archiveRoot
        foreach ($name in @('main','evaluation')) { Copy-Item -LiteralPath (Join-Path $source ('src/' + $name)) -Destination (Join-Path $Workspace 'src') -Recurse }
        [IO.File]::AppendAllText((Join-Path $Workspace 'build.gradle'), ("`n" + [IO.File]::ReadAllText((Join-Path $source 'build-overlay.gradle'))))
    }
    $mod = Invoke-Check mod
    $server = Invoke-Check server
    Assert-Marker $server 'AEGIS_ASSERTIONS_PASS'
    if ($Mode -eq 'Regression') {
    $recipePath = Join-Path $Workspace 'src/main/resources/assets/aegisapples/recipes/aegis_apple.json'
    $recipeText = [IO.File]::ReadAllText($recipePath)
    try {
        $recipe = $recipeText | ConvertFrom-Json
        $recipe.key.A.data = 0
        [IO.File]::WriteAllText($recipePath, ($recipe | ConvertTo-Json -Depth 8))
        $badRecipe = Invoke-Check server 1
        Assert-Marker $badRecipe 'AEGIS_ASSERTIONS_FAIL: Exact DGD/GAG/DGD recipe rejected'
        $errors = & $shell -NoProfile -File (Join-Path $Workspace 'agent.ps1') -Action errors -RunId $badRecipe.runId
        if ($errors -notmatch 'AEGIS_ASSERTIONS_FAIL') { throw 'Recipe cause omitted from bounded errors.' }
    } finally { [IO.File]::WriteAllText($recipePath, $recipeText) }
    $itemPath = Join-Path $Workspace 'src/main/java/dev/agentic/aegisapples/AegisApple.java'
    $itemText = [IO.File]::ReadAllText($itemPath)
    try {
        if (-not $itemText.Contains('super(20, 1.0F, false)')) { throw 'Fault injection target absent.' }
        [IO.File]::WriteAllText($itemPath, $itemText.Replace('super(20, 1.0F, false)', 'super(20, 0.1F, false)'))
        $badFood = Invoke-Check server 1
        Assert-Marker $badFood 'AEGIS_ASSERTIONS_FAIL: Saturation not full from 0'
        $errors = & $shell -NoProfile -File (Join-Path $Workspace 'agent.ps1') -Action errors -RunId $badFood.runId
        if ($errors -notmatch 'AEGIS_ASSERTIONS_FAIL') { throw 'Food cause omitted from bounded errors.' }
    } finally { [IO.File]::WriteAllText($itemPath, $itemText) }
    $finalMod = Invoke-Check mod
    $finalServer = Invoke-Check server
    Assert-Marker $finalServer 'AEGIS_ASSERTIONS_PASS'
    } else { $finalMod = $mod; $finalServer = $server }
    Add-Type -AssemblyName System.IO.Compression.FileSystem
    $zip = [IO.Compression.ZipFile]::OpenRead($finalMod.artifact.path)
    try {
        if ($zip.Entries.FullName -match 'aegischecks|EnvironmentProbe|\.java$') { throw 'Test assets leaked into artifact.' }
        foreach ($entry in @('mcmod.info','assets/aegisapples/models/item/aegis_apple.json','assets/aegisapples/recipes/aegis_apple.json','assets/aegisapples/lang/en_us.lang')) {
            if (-not $zip.GetEntry($entry)) { throw "Missing production resource: $entry" }
        }
    } finally { $zip.Dispose() }
    $record = & $shell -NoProfile -File (Join-Path $Workspace 'tools/Record-Evidence.ps1') -Capability behavior -Outcome PASS -Method log-marker -Observation 'Aegis food/recipe/effects/consumption and player NBT assertions; no client or relog claim' -EvidenceFiles $finalServer.log -Artifact $finalMod.artifact.path -Marker 'AEGIS_ASSERTIONS_PASS'
    if ($LASTEXITCODE -ne 0) { throw "Evidence recording failed: $record" }
    $release = & $shell -NoProfile -File (Join-Path $Workspace 'tools/Export-Release.ps1')
    if ($LASTEXITCODE -ne 0) { throw "Release export failed: $release" }
    $saved = ($release -join "`n") | ConvertFrom-Json
    $result = [ordered]@{status='PASS'; mode=$Mode; faultCoverage=if ($Mode -eq 'Regression') { 'PASS' } else { 'NOT_RUN' }; workspace=$Workspace; kitArchiveSha256=$archive.archiveSha256; cases=$cases; artifact=$saved.artifact; graphicalClientTested=$false; scope=if ($Mode -eq 'Regression') { 'Real server acceptance plus deliberate recipe/saturation failures and repair' } else { 'Clean build/server acceptance and artifact checks; no deliberate faults rerun' }}
    $exitCode=0
} catch {
    $result=[ordered]@{status='FAIL'; message=$_.Exception.Message; workspace=$Workspace; cases=$cases}
    $exitCode=1
}
New-Item -ItemType Directory -Force -Path (Join-Path $root 'evidence/aegis-apple') | Out-Null
$json=$result | ConvertTo-Json -Depth 8 -Compress
[IO.File]::WriteAllText((Join-Path $root ('evidence/aegis-apple/acceptance-' + $archive.version + '-' + $Mode.ToLowerInvariant() + '-' + [DateTime]::UtcNow.ToString('yyyyMMddTHHmmssfff') + '.json')), $json)
$json
exit $exitCode
