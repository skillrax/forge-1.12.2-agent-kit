# Negative launcher outcomes under bounded draining. No Minecraft fixture needed.
[CmdletBinding()]
param()
$ErrorActionPreference='Stop'
$root=Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$fixture=Join-Path $root ('.work/workshop runner negative space/'+[Guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Force -Path $fixture | Out-Null
[IO.File]::WriteAllText((Join-Path $fixture 'agent.ps1'),"@{projectRoot=`$PSScriptRoot;inputFingerprint='fixed';toolchainFingerprint='fixed'} | ConvertTo-Json -Compress")
$cases=@()
foreach($case in @(
    @{name='exit without readiness';body='exit /b 0';timeout=10},
    @{name='readiness without acceptance';body="echo Done ^(1.0s^)!`r`nexit /b 0";timeout=10},
    @{name='bounded timeout';body="echo still starting`r`nping -n 31 127.0.0.1 >nul`r`nexit /b 0";timeout=1}
)) {
    [IO.File]::WriteAllText((Join-Path $fixture 'gradlew.bat'),"@echo off`r`n"+$case.body)
    $output=& powershell -NoProfile -File (Join-Path $root 'qa/integration/Invoke-WorkshopServer.ps1') -Workspace $fixture -TimeoutSeconds $case.timeout
    $code=$LASTEXITCODE;$result=($output -join "`n") | ConvertFrom-Json
    if($code -ne 1 -or $result.status -ne 'FAIL' -or -not (Test-Path $result.log)){throw "Negative outcome accepted: $output"}
    if($case.name -eq 'bounded timeout' -and $result.message -ne 'Server acceptance timeout'){throw 'Wrong timeout cause'}
    if($case.name -eq 'readiness without acceptance' -and (-not $result.serverReady -or $result.acceptanceMarker)){throw 'Wrong readiness/marker state'}
    $cases+=@{name=$case.name;result=$result}
}
@{status='PASS';fixture=$fixture;cases=$cases;scope='synthetic early exits and timeout; raw failed reports/logs retained'} | ConvertTo-Json -Depth 8 -Compress
