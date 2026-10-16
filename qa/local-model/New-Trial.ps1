# Creates one local-model trial folder: the packaged kit (releases/<version>) extracted into a fresh
# folder outside this repository, plus an opencode.json for an OpenAI-compatible endpoint.
# Usage: qa/local-model/New-Trial.ps1 -Id p3-magnet -Model <served model id> [-BaseUrl http://localhost:8080/v1]
[CmdletBinding()]
param(
    [Parameter(Mandatory)][ValidatePattern('^[a-z0-9][a-z0-9-]*$')][string]$Id,
    [Parameter(Mandatory)][string]$Model,
    [string]$BaseUrl = 'http://localhost:8080/v1',
    [int]$Context = 65536,
    [int]$Output = 8192,
    [string]$Root,
    [switch]$AutoApprove
)
$ErrorActionPreference = 'Stop'
$repo = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
# Outside the repository, so the agent cannot find maintainer files, QA fixtures or these prompts.
if (-not $Root) { $Root = Join-Path (Split-Path -Parent $repo) 'kit-trials' }
$version = (Get-Content -Raw (Join-Path $repo 'kit/kit.json') | ConvertFrom-Json).version
$zip = Join-Path $repo "releases/$version/agentic-mc1.12.2-kit-$version-windows.zip"
if (-not (Test-Path -LiteralPath $zip)) { throw "No ZIP for $version; run packaging/Build-KitZip.ps1 first." }
$dir = Join-Path $Root $Id
if (Test-Path -LiteralPath $dir) { throw "$dir exists; use a new -Id (every trial starts from a fresh kit)." }

New-Item -ItemType Directory -Force -Path $Root | Out-Null
$staging = "$dir.extracting"
Add-Type -AssemblyName System.IO.Compression.FileSystem
[IO.Compression.ZipFile]::ExtractToDirectory($zip, $staging)
# The ZIP wraps the kit in one folder; move that folder to $dir to keep paths short (Windows 260-char limit).
$agent = Get-ChildItem -Path $staging -Recurse -Filter agent.ps1 -File | Select-Object -First 1
if (-not $agent) { throw "agent.ps1 not found in $zip" }
Move-Item -LiteralPath $agent.Directory.FullName -Destination $dir
Remove-Item -LiteralPath $staging -Recurse -Force -ErrorAction SilentlyContinue
$workspace = $dir

$approve = if ($AutoApprove) { 'allow' } else { 'ask' }
$config = [ordered]@{
    '$schema' = 'https://opencode.ai/config.json'
    model = "local/$Model"
    share = 'disabled'
    provider = [ordered]@{
        local = [ordered]@{
            npm = '@ai-sdk/openai-compatible'
            name = 'Local'
            options = [ordered]@{ baseURL = $BaseUrl }
            models = [ordered]@{ $Model = [ordered]@{ name = $Model; limit = [ordered]@{ context = $Context; output = $Output } } }
        }
    }
    permission = [ordered]@{ edit = 'allow'; bash = $approve }
}
[IO.File]::WriteAllText((Join-Path $workspace 'opencode.json'), ($config | ConvertTo-Json -Depth 8), (New-Object Text.UTF8Encoding($false)))

"kit $version -> $workspace"
"model local/$Model at $BaseUrl (context $Context, bash: $approve)"
"next: cd `"$workspace`"; opencode   then paste the prompt from qa/local-model/PROMPTS.md"
