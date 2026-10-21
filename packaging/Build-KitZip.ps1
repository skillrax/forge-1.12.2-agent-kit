[CmdletBinding()]
param([string]$OutputDirectory)
$ErrorActionPreference = 'Stop'
[Console]::OutputEncoding = New-Object Text.UTF8Encoding($false)
$OutputEncoding = [Console]::OutputEncoding
$root = Split-Path -Parent $PSScriptRoot
$kit = Join-Path $root 'kit'
try {
    $info = Get-Content -Encoding UTF8 (Join-Path $kit 'kit.json') -Raw | ConvertFrom-Json
    $version = $info.version
    if ($version -notmatch '^\d+\.\d+\.\d+(-[a-z]+\.\d+)?$') { throw 'Invalid kit version.' }
    $allowlist = Get-Content -Encoding UTF8 (Join-Path $PSScriptRoot 'files.json') -Raw | ConvertFrom-Json
    if (-not $OutputDirectory) { $OutputDirectory = Join-Path $root ('releases/' + $version) }
    $destination = [IO.Path]::GetFullPath($OutputDirectory)
    New-Item -ItemType Directory -Force -Path $destination | Out-Null
    $name = 'forge-1.12.2-agent-kit-' + $version + '-windows'
    $zipPath = Join-Path $destination ($name + '.zip')
    if (Test-Path -LiteralPath $zipPath) { throw 'Archive already exists; never overwrite a release candidate.' }
    $seen = New-Object 'System.Collections.Generic.HashSet[string]' ([StringComparer]::OrdinalIgnoreCase)
    $entries = foreach ($relative in ($allowlist.files | Sort-Object)) {
        if ($relative -match '(^/|\\|(^|/)\.\.(/|$)|(^|/)(node_modules|build|run|reports|\.gradle|\.git)(/|$))' -or -not $seen.Add($relative)) { throw "Unsafe or duplicate inclusion: $relative" }
        $path = Join-Path $kit $relative
        if (-not [IO.File]::Exists($path)) { throw "Required file missing: $relative" }
        $item = Get-Item -LiteralPath $path -Force
        if ($item.Attributes -band [IO.FileAttributes]::ReparsePoint) { throw "Cannot package a link: $relative" }
        [ordered]@{ path=$relative; sha256=(Get-FileHash -LiteralPath $path -Algorithm SHA256).Hash; bytes=$item.Length }
    }
    # Only explicit paths become archive entries; source caches are never enumerated.
    Add-Type -AssemblyName System.IO.Compression
    Add-Type -AssemblyName System.IO.Compression.FileSystem
    $zip = [IO.Compression.ZipFile]::Open($zipPath, [IO.Compression.ZipArchiveMode]::Create)
    try {
        foreach ($entry in $entries) {
            $created = $zip.CreateEntry(($name + '/' + $entry.path), [IO.Compression.CompressionLevel]::Optimal)
            $created.LastWriteTime = [DateTimeOffset]::new(2000,1,1,0,0,0,[TimeSpan]::Zero)
            $sourceStream = [IO.File]::OpenRead((Join-Path $kit $entry.path))
            $outputStream = $created.Open()
            try { $sourceStream.CopyTo($outputStream) } finally { $outputStream.Dispose(); $sourceStream.Dispose() }
        }
    } finally { $zip.Dispose() }
    $sha = (Get-FileHash -LiteralPath $zipPath -Algorithm SHA256).Hash
    [long]$sourceBytes = 0
    foreach ($file in $entries) { $sourceBytes += [long]$file['bytes'] }
    $releaseState=if($version -match '-(alpha|beta)\.\d+$'){'private-'+$Matches[1]+'-candidate'}else{'release'}
    $manifest = [ordered]@{ schemaVersion=1; version=$version; releaseState=$releaseState; archive=$zipPath; archiveRoot=$name; archiveSha256=$sha; compressedBytes=(Get-Item $zipPath).Length; sourceBytes=$sourceBytes; fileCount=@($entries).Count; files=@($entries); limits=$(if($releaseState -eq 'release'){'Exact content/checksums. MIT for the kit''s own files (LICENSE); graphical gameplay is checked by hand with runClient.'}else{'Exact candidate content/checksums; separate-PC setup, agent client connection and installed gameplay remain unverified. Private distribution only.'}) }
    $json = $manifest | ConvertTo-Json -Depth 8 -Compress
    [IO.File]::WriteAllText((Join-Path $destination 'manifest.json'), $json)
    [IO.File]::WriteAllText(($zipPath + '.sha256'), ($sha + '  ' + [IO.Path]::GetFileName($zipPath) + "`n"))
    [ordered]@{ status='PASS'; archive=$zipPath; manifest=(Join-Path $destination 'manifest.json'); sha256=$sha; fileCount=@($entries).Count; compressedBytes=$manifest.compressedBytes } | ConvertTo-Json -Compress
    exit 0
} catch {
    [ordered]@{ status='FAIL'; message=$_.Exception.Message; line=$_.InvocationInfo.ScriptLineNumber } | ConvertTo-Json -Compress
    exit 1
}
