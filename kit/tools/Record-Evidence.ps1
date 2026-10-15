# Record an explicit observation; this does not run or infer graphical gameplay.
[CmdletBinding()]
param(
    [Parameter(Mandatory)][ValidateSet('behavior','client','installedArtifact')][string]$Capability,
    [Parameter(Mandatory)][ValidateSet('PASS','FAIL')][string]$Outcome,
    [Parameter(Mandatory)][ValidateSet('manual','log-marker')][string]$Method,
    [Parameter(Mandatory)][string]$Observation,
    [Parameter(Mandatory)][string[]]$EvidenceFiles,
    [string]$Artifact,
    [string]$Marker
)
$ErrorActionPreference = 'Stop'
[Console]::OutputEncoding = New-Object Text.UTF8Encoding($false)
$OutputEncoding = [Console]::OutputEncoding
$root = Split-Path -Parent $PSScriptRoot
try {
    $shell = (Get-Process -Id $PID).Path
    $statusText = & $shell -NoProfile -File (Join-Path $root 'agent.ps1') -Action status
    if ($LASTEXITCODE -ne 0) { throw 'Cannot read project identity.' }
    $status = ($statusText -join "`n") | ConvertFrom-Json
    if ($Method -eq 'log-marker' -and -not $Marker) { throw 'A log-marker observation requires an explicit literal Marker.' }
    $files = foreach ($file in $EvidenceFiles) {
        $resolved = (Resolve-Path -LiteralPath $file).Path
        if (-not [IO.File]::Exists($resolved)) { throw "Evidence is not a file: $resolved" }
        [ordered]@{ path=$resolved; sha256=(Get-FileHash -LiteralPath $resolved -Algorithm SHA256).Hash }
    }
    if ($Method -eq 'log-marker') {
        $found = $false
        foreach ($file in $files) { if ([IO.File]::ReadAllText($file.path).Contains($Marker)) { $found = $true } }
        if (-not $found) { throw 'Expected literal marker absent from supplied evidence.' }
    }
    if ($Capability -in @('client','installedArtifact') -and -not $Artifact) { throw 'Client/install observations require the exact artifact path.' }
    $artifactInfo = if ($Artifact) { $resolved = (Resolve-Path -LiteralPath $Artifact).Path; @{path=$resolved; sha256=(Get-FileHash -LiteralPath $resolved -Algorithm SHA256).Hash} } else { $null }
    $result = [ordered]@{ schemaVersion=1; capability=$Capability; status=$Outcome; method=$Method; observation=$Observation; timestampUtc=[DateTime]::UtcNow.ToString('o'); projectRoot=$status.projectRoot; inputFingerprint=$status.inputFingerprint; toolchainFingerprint=$status.toolchainFingerprint; inputsChangedDuringRun=$false; toolchainChangedDuringRun=$false; files=@($files); artifact=$artifactInfo; scope='Explicit recorded observation; inspect linked evidence before relying on it.' }
    $directory = Join-Path $root 'reports/capability-evidence'
    New-Item -ItemType Directory -Force -Path $directory | Out-Null
    $json = $result | ConvertTo-Json -Depth 8 -Compress
    [IO.File]::WriteAllText((Join-Path $directory ($Capability + '.json')), $json)
    $json
    exit 0
} catch {
    [ordered]@{ schemaVersion=1; status='FAIL'; message=$_.Exception.Message } | ConvertTo-Json -Compress
    exit 1
}
