# Copy the checked mod jar to releases/<modid>-<version>.jar.
# Requires a server or game check that passed after the last source change.
# Refuses to overwrite an exported version with different bytes: bump the version instead.
[CmdletBinding()]
param([switch]$Json)
$ErrorActionPreference = 'Stop'
[Console]::OutputEncoding = New-Object Text.UTF8Encoding($false)
$root = Split-Path -Parent $PSScriptRoot
$reports = Join-Path $root 'reports/testing-tools'
try {
    $newest = [DateTime]::MinValue
    foreach ($file in [IO.Directory]::GetFiles((Join-Path $root 'src'), '*', [IO.SearchOption]::AllDirectories) + @(Join-Path $root 'build.gradle')) {
        $time = [IO.File]::GetLastWriteTimeUtc($file); if ($time -gt $newest) { $newest = $time }
    }
    $passing = @(@(foreach ($profile in @('server', 'game')) {
        $path = Join-Path $reports ($profile + '-latest.json')
        if ([IO.File]::Exists($path) -and [IO.File]::GetLastWriteTimeUtc($path) -gt $newest) {
            $result = [IO.File]::ReadAllText($path) | ConvertFrom-Json
            if ($result.status -eq 'PASS') { $result }
        }
    }) | Sort-Object { [IO.File]::GetLastWriteTimeUtc($_.report) } -Descending)
    if (-not $passing.Count) { throw 'Export needs a passing check after the last source change: run agent.ps1 check server (or check game).' }
    # server/game checks build and inspect the jar first; their log records its path and version.
    $build = [regex]::Match([IO.File]::ReadAllText($passing[0].log), '(?m)^MOD_ARTIFACT: (.+?)\r?$')
    $version = [regex]::Match([IO.File]::ReadAllText($passing[0].log), '(?m)^MOD_VERSION: (\S+)')
    if (-not $build.Success -or -not $version.Success) { throw 'The passing check did not record a jar; rerun agent.ps1 check server.' }
    $jar = $build.Groups[1].Value.Trim()
    if (-not [IO.File]::Exists($jar)) { throw "Checked jar no longer exists: $jar. Rerun agent.ps1 check server." }
    $name = [IO.Path]::GetFileName($jar) -replace '-srg\.jar$', '.jar'
    if ($name -notmatch [regex]::Escape($version.Groups[1].Value)) { throw "Jar name $name does not contain version $($version.Groups[1].Value); set base.archivesName and version in build.gradle." }
    $destination = Join-Path $root ('releases/' + $name)
    $hash = (Get-FileHash -LiteralPath $jar -Algorithm SHA256).Hash
    if ([IO.File]::Exists($destination)) {
        if ((Get-FileHash -LiteralPath $destination -Algorithm SHA256).Hash -eq $hash) { $note = 'already exported (identical)' }
        else { throw "releases/$name already exists with different contents. Bump the version (build.gradle version, @Mod version, mcmod.info), rerun check server, then export." }
    } else {
        New-Item -ItemType Directory -Force -Path (Split-Path -Parent $destination) | Out-Null
        Copy-Item -LiteralPath $jar -Destination $destination
        $note = 'exported'
    }
    if ($Json) { [ordered]@{ schemaVersion = 1; status = 'PASS'; jar = $destination; version = $version.Groups[1].Value; sha256 = $hash; checkedBy = $passing[0].runId } | ConvertTo-Json -Compress }
    else { "$note releases/$name (version $($version.Groups[1].Value), checked by $($passing[0].profile) run $($passing[0].runId))" }
    exit 0
} catch {
    if ($Json) { [ordered]@{ schemaVersion = 1; status = 'FAIL'; message = $_.Exception.Message } | ConvertTo-Json -Compress } else { 'FAIL: ' + $_.Exception.Message }
    exit 1
}
