[CmdletBinding()]
param()
$ErrorActionPreference = 'Stop'
[Console]::OutputEncoding = New-Object Text.UTF8Encoding($false)
$OutputEncoding = [Console]::OutputEncoding
$root = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$fixture = Join-Path $root ('.work/package-fixtures/' + [Guid]::NewGuid().ToString('N'))
$shell = (Get-Process -Id $PID).Path
$cases = New-Object 'System.Collections.Generic.List[string]'
try {
    New-Item -ItemType Directory -Force -Path (Join-Path $fixture 'kit'), (Join-Path $fixture 'packaging') | Out-Null
    Copy-Item (Join-Path $root 'packaging/Build-KitZip.ps1') (Join-Path $fixture 'packaging')
    Set-Content (Join-Path $fixture 'kit/kit.json') '{"version":"0.3.0-alpha.1"}'
    Set-Content (Join-Path $fixture 'kit/README.md') 'Deterministic package fixture.'
    function Invoke-PackageFixture {
        param([string[]]$Files, [string]$Name, [int]$ExpectedExit)
        @{ files=$Files } | ConvertTo-Json | Set-Content (Join-Path $fixture 'packaging/files.json')
        $output = & $shell -NoProfile -File (Join-Path $fixture 'packaging/Build-KitZip.ps1') -OutputDirectory (Join-Path $fixture $Name)
        if ($LASTEXITCODE -ne $ExpectedExit) { throw "Unexpected packaging exit: $output" }
        return (($output -join "`n") | ConvertFrom-Json)
    }
    $first = Invoke-PackageFixture @('kit.json','README.md') 'first' 0
    $second = Invoke-PackageFixture @('kit.json','README.md') 'second' 0
    if ($first.status -ne 'PASS' -or $first.sha256 -ne $second.sha256 -or $first.fileCount -ne 2) { throw 'Repeated archive differed or inclusion incorrect.' }
    $cases.Add('same-source deterministic archive')
    $duplicateOutput = Invoke-PackageFixture @('kit.json','README.md') 'first' 1
    if ($duplicateOutput.message -notmatch 'already exists') { throw 'Existing candidate not protected.' }
    $cases.Add('existing archive protected')
    Set-Content (Join-Path $fixture 'kit/kit.json') '{"version":"0.4.0-beta.1"}'
    $beta=Invoke-PackageFixture @('kit.json','README.md') 'beta' 0
    $betaManifest=Get-Content -LiteralPath $beta.manifest -Raw | ConvertFrom-Json
    if($betaManifest.releaseState -ne 'private-beta-candidate'){throw 'Beta archive mislabeled'}
    $cases.Add('manifest stage follows beta version')
    foreach ($invalid in @(
        @{files=@('missing.txt'); name='missing'; cause='Required file missing'},
        @{files=@('../secret.txt'); name='traversal'; cause='Unsafe'},
        @{files=@('kit.json','kit.json'); name='duplicate'; cause='duplicate'},
        @{files=@('node_modules/private.txt'); name='dependency'; cause='Unsafe'}
    )) {
        $failed = Invoke-PackageFixture $invalid.files $invalid.name 1
        if ($failed.message -notmatch $invalid.cause) { throw "Wrong failure cause: $($failed.message)" }
        $cases.Add($invalid.name + ' rejected')
    }
    [ordered]@{ status='PASS'; cases=$cases; fixture=$fixture; scope='synthetic packaging contracts; no game evidence' } | ConvertTo-Json -Compress
    exit 0
} catch {
    [ordered]@{ status='FAIL'; message=$_.Exception.Message; cases=$cases; fixture=$fixture } | ConvertTo-Json -Compress
    exit 1
}
