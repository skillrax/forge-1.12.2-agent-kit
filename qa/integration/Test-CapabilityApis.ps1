[CmdletBinding()]
param()
$ErrorActionPreference='Stop'
[Console]::OutputEncoding=New-Object Text.UTF8Encoding($false)
$taskRoot=Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$kit=Get-Content -Encoding UTF8 (Join-Path $taskRoot 'kit/kit.json') -Raw | ConvertFrom-Json
$archive=Get-Content -Encoding UTF8 (Join-Path $taskRoot ('releases/'+$kit.version+'/manifest.json')) -Raw | ConvertFrom-Json
if((Get-FileHash -LiteralPath $archive.archive).Hash -ne $archive.archiveSha256){throw 'Archive hash differs'}
Add-Type -AssemblyName System.IO.Compression.FileSystem
$destination=Join-Path $taskRoot ('.work/capability-api/'+[Guid]::NewGuid().ToString('N'))
[IO.Compression.ZipFile]::ExtractToDirectory($archive.archive,$destination)
$workspace=Join-Path $destination $archive.archiveRoot
Copy-Item -LiteralPath (Join-Path $taskRoot 'qa/fixtures/capability-api/src/main') -Destination (Join-Path $workspace 'src') -Recurse -Force
$output=& powershell -NoProfile -File (Join-Path $workspace 'agent.ps1') -Action check -Profile all -TimeoutSeconds 180
if($LASTEXITCODE -ne 0){$output;throw 'API probe failed; inspect errors for the reported run ID'}
$check=($output -join "`n") | ConvertFrom-Json
$result=[ordered]@{schemaVersion=1;status=$check.status;kitArchiveSha256=$archive.archiveSha256;workspace=$workspace;validation=$check;scope='API compile/package feasibility only; zero runtime gameplay tests, no mod registration, no graphical or server-load claim; QA artifact must not be distributed'}
$evidence=Join-Path $taskRoot 'evidence/capability-api'
New-Item -ItemType Directory -Force -Path $evidence | Out-Null
$json=$result | ConvertTo-Json -Depth 8
[IO.File]::WriteAllText((Join-Path $evidence ($check.runId+'.json')),$json)
$json
