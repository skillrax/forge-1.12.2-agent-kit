# Relative entrypoint: a copied config can only launch the active checkout.
[CmdletBinding()]
param()
$ErrorActionPreference = 'Stop'
[Console]::OutputEncoding = New-Object Text.UTF8Encoding($false)
$OutputEncoding = [Console]::OutputEncoding
$env:PSModulePath=(Join-Path $PSHOME 'Modules')+';'+$env:PSModulePath
$root = [IO.Path]::GetFullPath((Split-Path -Parent $PSScriptRoot))
if ([IO.Path]::GetFullPath((Get-Location).Path) -ne $root) {
    throw "MCP working directory differs from launcher root $root. Open the intended checkout and run tools/Setup.ps1 -WithMcp."
}
if (-not (Test-Path -LiteralPath (Join-Path $root 'mcp/node_modules/@modelcontextprotocol/server'))) {
    throw 'MCP dependencies missing. Run powershell -NoProfile -File ./tools/Setup.ps1 -WithMcp.'
}
$env:MINECRAFT_KIT_ROOT = $root
. (Join-Path $PSScriptRoot 'NodeEnvironment.ps1')
$nodeSelection=Get-KitNodeSelection
if(-not $nodeSelection){throw 'MCP needs Node.js22+ and npm. Run tools/Setup.ps1 -WithMcp -InstallMissing, then restart Codex.'}
& $nodeSelection.node (Join-Path $root 'mcp/server.mjs')
exit $LASTEXITCODE
