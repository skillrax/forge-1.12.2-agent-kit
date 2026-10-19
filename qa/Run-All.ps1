# Release gate: every suite that covers recipient content, in one run, with one PASS/FAIL table.
# Required before a kit version bump (docs/DEVELOPMENT.md). From the repository root:
#   powershell -NoProfile -File qa/Run-All.ps1            (all, ~4 min warm: real Minecraft builds)
#   powershell -NoProfile -File qa/Run-All.ps1 -Quick     (no Minecraft: unit suites only, ~2 min)
# The ZIP check builds a fresh archive of the current kit into .work/ (releases/ is left alone).
[CmdletBinding()]
param([switch]$Quick)
$ErrorActionPreference = 'Stop'
$repo = Split-Path -Parent $PSScriptRoot
$shell = (Get-Process -Id $PID).Path
$logs = Join-Path $repo ('.work/run-all/' + [DateTime]::UtcNow.ToString('yyyyMMddTHHmmss'))
New-Item -ItemType Directory -Force -Path $logs | Out-Null

$suites = [ordered]@{
    'agent interface' = @('qa/unit/Test-AgentInterface.ps1')
    'runner contract' = @('qa/unit/Test-TestingTools.ps1')
    'guide snippets'  = @('qa/unit/Test-GuideSnippets.ps1')
    'packaging'       = @('qa/unit/Test-Packaging.ps1')
}
if (-not $Quick) {
    $suites['samples (game)'] = @('qa/integration/Test-KitSamples.ps1')
    $suites['kit ZIP'] = 'zip'
}

$rows = @(); $failed = 0
foreach ($name in $suites.Keys) {
    $log = Join-Path $logs (($name -replace '\W+', '-') + '.log')
    $watch = [Diagnostics.Stopwatch]::StartNew()
    Write-Host "running $name ..."
    $ErrorActionPreference = 'Continue'
    if ($suites[$name] -eq 'zip') {
        # A throwaway archive of the current tree, so the gate never collides with an existing release.
        $out = Join-Path $logs 'zip'
        $built = & $shell -NoProfile -File (Join-Path $repo 'packaging/Build-KitZip.ps1') -OutputDirectory $out 2>&1
        $code = $LASTEXITCODE
        $built | Out-File -LiteralPath $log -Encoding utf8
        if ($code -eq 0) {
            $manifest = (($built | Where-Object { "$_".StartsWith('{') } | Select-Object -Last 1) | ConvertFrom-Json).manifest
            $output = & $shell -NoProfile -File (Join-Path $repo 'qa/integration/Test-KitZip.ps1') -Manifest $manifest 2>&1
            $code = $LASTEXITCODE
            $output | Out-File -LiteralPath $log -Encoding utf8 -Append
        }
    } else {
        $output = & $shell -NoProfile -File (Join-Path $repo $suites[$name][0]) 2>&1
        $code = $LASTEXITCODE
        $output | Out-File -LiteralPath $log -Encoding utf8
    }
    $ErrorActionPreference = 'Stop'
    $status = if ($code -eq 0) { 'PASS' } else { $failed++; 'FAIL' }
    $rows += [pscustomobject]@{ Suite = $name; Result = $status; Seconds = [int]$watch.Elapsed.TotalSeconds; Log = $log.Substring($repo.Length + 1) }
}
''
$rows | Format-Table -AutoSize | Out-String -Width 200
if ($Quick) { 'Quick run: the game samples and the kit ZIP were not checked.' }
if ($failed) { "FAIL: $failed of $($rows.Count) suites failed; read the log named in the table."; exit 1 }
"PASS: all $($rows.Count) suites"
exit 0
