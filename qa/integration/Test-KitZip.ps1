[CmdletBinding()]
param([Parameter(Mandatory)][string]$Manifest)
$ErrorActionPreference = 'Stop'
[Console]::OutputEncoding = New-Object Text.UTF8Encoding($false)
$OutputEncoding = [Console]::OutputEncoding
$root = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$fixture = Join-Path $root (('.work/zip extraction space-' + [char]0x00E9 + '/') + [Guid]::NewGuid().ToString('N'))
$shell = (Get-Process -Id $PID).Path
try {
    $release = Get-Content -Encoding UTF8 -LiteralPath $Manifest -Raw | ConvertFrom-Json
    if ((Get-FileHash -LiteralPath $release.archive -Algorithm SHA256).Hash -ne $release.archiveSha256) { throw 'Archive checksum mismatch.' }
    Add-Type -AssemblyName System.IO.Compression.FileSystem
    [IO.Compression.ZipFile]::ExtractToDirectory($release.archive, $fixture)
    $workspace = Join-Path $fixture $release.archiveRoot
    $actual = @(Get-ChildItem -LiteralPath $workspace -Recurse -File -Force)
    if ($actual.Count -ne $release.fileCount) { throw 'Archive entry count mismatch.' }
    foreach ($file in $release.files) {
        $path = Join-Path $workspace $file.path
        if ((Get-FileHash -LiteralPath $path -Algorithm SHA256).Hash -ne $file.sha256) { throw "Extracted hash mismatch: $($file.path)" }
    }
    if (Test-Path (Join-Path $workspace 'mcp/test')) { throw 'Producer tests leaked into archive.' }
    # A shell-only user must not need Node or any authoring paths.
    $savedPath = $env:PATH
    try {
        $env:PATH = Join-Path $env:SystemRoot 'System32'
        $output = & $shell -NoProfile -File (Join-Path $workspace 'tools/Setup.ps1')
        if ($LASTEXITCODE -ne 0) { throw "Shell-only setup failed: $output" }
        $setup = ($output -join "`n") | ConvertFrom-Json
        if ($setup.status -ne 'PASS' -or $setup.mcpRequested) { throw 'Shell-only setup invoked MCP.' }
    } finally { $env:PATH = $savedPath }
    $status = (& $shell -NoProfile -File (Join-Path $workspace 'agent.ps1') -Action status) | ConvertFrom-Json
    if ($status.lastRun -or $status.projectRoot -ne $workspace) { throw 'Extracted workspace claimed old evidence or wrong root.' }
    $output = & $shell -NoProfile -File (Join-Path $workspace 'agent.ps1') -Action check -Profile all -TimeoutSeconds 120
    if ($LASTEXITCODE -ne 0) { throw "Extracted build failed: $output" }
    $check = ($output -join "`n") | ConvertFrom-Json
    if ($check.status -ne 'PASS' -or $check.projectRoot -ne $workspace) { throw 'Extracted check did not validate the recipient root.' }
    # Test UTF-8 Java8 compilation using a real character and an independent numeric oracle.
    $main = Join-Path $workspace 'src/main/java'
    $tests = Join-Path $workspace 'src/test/java'
    New-Item -ItemType Directory -Force -Path $main,$tests | Out-Null
    $utf8Source = 'public class UnicodeProbe { public static final String VALUE = "caf' + [char]0x00E9 + '"; }'
    [IO.File]::WriteAllText((Join-Path $main 'UnicodeProbe.java'), $utf8Source, (New-Object Text.UTF8Encoding($false)))
    $oracle = 'import org.junit.Test; import static org.junit.Assert.*; public class UnicodeProbeTest { @Test public void characterIsPreserved() { assertEquals(4, UnicodeProbe.VALUE.length()); assertEquals(233, (int)UnicodeProbe.VALUE.charAt(3)); } }'
    [IO.File]::WriteAllText((Join-Path $tests 'UnicodeProbeTest.java'), $oracle, (New-Object Text.UTF8Encoding($false)))
    $output = & $shell -NoProfile -File (Join-Path $workspace 'agent.ps1') -Action check -Profile unit -TimeoutSeconds 120
    if ($LASTEXITCODE -ne 0) { throw "Unicode compilation test failed: $output" }
    $unicode = ($output -join "`n") | ConvertFrom-Json
    if ($unicode.status -ne 'PASS' -or $unicode.unitTestCount -lt 1) { throw 'Unicode Java check did not execute.' }
    [ordered]@{ status='PASS'; archiveSha256=$release.archiveSha256; workspace=$workspace; runId=$check.runId; unicodeRun=$unicode.runId; cases=@('archive/file hashes','exact inclusion','shell-only setup without Node','fresh root/no inherited evidence','real extracted foundation build','UTF-8 Java8 compilation/JUnit','Unicode extraction path'); limits='Warm shared host caches; no cold-machine, app connectivity or graphical gameplay claim.' } | ConvertTo-Json -Depth 5 -Compress
    exit 0
} catch {
    [ordered]@{ status='FAIL'; message=$_.Exception.Message; fixture=$fixture } | ConvertTo-Json -Compress
    exit 1
}
