# Preserve the exact validated SRG artifact and its identity; never export a stale PASS.
[CmdletBinding()]
param()
$ErrorActionPreference = 'Stop'
[Console]::OutputEncoding = New-Object Text.UTF8Encoding($false)
$OutputEncoding = [Console]::OutputEncoding
$root = Split-Path -Parent $PSScriptRoot
try {
    $shell = (Get-Process -Id $PID).Path
    $text = & $shell -NoProfile -File (Join-Path $root 'agent.ps1') -Action status
    if ($LASTEXITCODE -ne 0) { throw 'Cannot inspect current evidence.' }
    $status = ($text -join "`n") | ConvertFrom-Json
    if ($status.capabilities.mod.state -ne 'PASS') { throw 'Release requires a fresh mod PASS. Run agent.ps1 -Action check -Profile mod.' }
    $mod = [IO.File]::ReadAllText((Join-Path $root 'reports/testing-tools/mod-latest.json')) | ConvertFrom-Json
    if (-not $mod.artifact -or $mod.modEntryPoints -lt 1) { throw 'Validated mod artifact missing.' }
    if(-not $mod.modVersion){throw 'Validated mod version missing. Run a fresh mod check with the current tools.'}
    $artifactName=[IO.Path]::GetFileName($mod.artifact.path)
    if(-not $artifactName.EndsWith('-'+$mod.modVersion+'-srg.jar')){throw 'Artifact filename must include the validated mod version before -srg.jar. Fix archive naming and rebuild.'}
    $identity=$artifactName.Substring(0,$artifactName.Length-('-'+$mod.modVersion+'-srg.jar').Length)
    $priorRoot=Join-Path $root 'reports/releases'
    if(Test-Path -LiteralPath $priorRoot){
        foreach($folder in (Get-ChildItem -LiteralPath $priorRoot -Directory)){
            $priorPath=Join-Path $folder.FullName 'manifest.json'
            if(-not (Test-Path -LiteralPath $priorPath)){continue}
            $prior=Get-Content -Encoding UTF8 -LiteralPath $priorPath -Raw | ConvertFrom-Json
            $priorName=[IO.Path]::GetFileName($prior.artifact.path)
            if($priorName -eq $artifactName -and $prior.artifact.sha256 -ne $mod.artifact.sha256){throw ('Changed mod bytes already delivered as '+$mod.modVersion+'. Bump the mod version (normally PATCH for a fix), synchronize metadata, rebuild and export. Previous release preserved.')}
        }
    }
    $destination = Join-Path $root ('reports/releases/' + $mod.runId)
    if (Test-Path -LiteralPath $destination) { throw "Release snapshot already exists: $destination" }
    New-Item -ItemType Directory -Path $destination | Out-Null
    $jarPath = Join-Path $destination ([IO.Path]::GetFileName($mod.artifact.path))
    Copy-Item -LiteralPath $mod.artifact.path -Destination $jarPath
    if ((Get-FileHash -LiteralPath $jarPath -Algorithm SHA256).Hash -ne $mod.artifact.sha256) { throw 'Release copy hash mismatch.' }
    $manifest = [ordered]@{ schemaVersion=1; projectRoot=$root; runId=$mod.runId; modVersion=$mod.modVersion; modIdentity=$identity; inputFingerprint=$mod.inputFingerprint; toolchainFingerprint=$mod.toolchainFingerprint; artifact=@{path=$jarPath; sha256=$mod.artifact.sha256}; validationScope=$mod.scope; capabilities=$status.capabilities; note='Preserved build artifact; client and independent install require their own observations. Keep source checkout with this manifest.' }
    $json = $manifest | ConvertTo-Json -Depth 8 -Compress
    [IO.File]::WriteAllText((Join-Path $destination 'manifest.json'), $json)
    $json
    exit 0
} catch {
    [ordered]@{ status='FAIL'; message=$_.Exception.Message } | ConvertTo-Json -Compress
    exit 1
}
