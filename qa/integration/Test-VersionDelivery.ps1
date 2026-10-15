# Exact packaged version consistency/export rejection, using the preserved generic starter.
[CmdletBinding()]
param()
$ErrorActionPreference='Stop'
$root=Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$version=(Get-Content -Encoding UTF8 (Join-Path $root 'kit/kit.json') -Raw | ConvertFrom-Json).version
$release=Get-Content -Encoding UTF8 (Join-Path $root ('releases/'+$version+'/manifest.json')) -Raw | ConvertFrom-Json
if((Get-FileHash $release.archive).Hash -ne $release.archiveSha256){throw 'Archive differs'}
Add-Type -AssemblyName System.IO.Compression.FileSystem
$destination=Join-Path $root ('.work/version delivery space/'+[Guid]::NewGuid().ToString('N'))
[IO.Compression.ZipFile]::ExtractToDirectory($release.archive,$destination)
$workspace=Join-Path $destination $release.archiveRoot
Copy-Item -LiteralPath (Join-Path $workspace '.agents/skills/forge-1122-mod/assets/starter/src/main') -Destination (Join-Path $workspace 'src') -Recurse
$checks=@()
function Invoke-Check([int]$Expected=0){
    $out=& powershell -NoProfile -File (Join-Path $workspace 'agent.ps1') -Action check -Profile mod -TimeoutSeconds 180
    $code=$LASTEXITCODE;$r=($out -join "`n") | ConvertFrom-Json;$script:checks+=$r
    if($code -ne $Expected){throw "Wrong mod exit: $out"};return $r
}
function Invoke-Export([int]$Expected=0){
    $out=& powershell -NoProfile -File (Join-Path $workspace 'tools/Export-Release.ps1')
    if($LASTEXITCODE -ne $Expected){throw "Wrong export exit: $out"};($out -join "`n") | ConvertFrom-Json
}
try{
    $baseline=Invoke-Check;$first=Invoke-Export
    $language=Join-Path $workspace 'src/main/resources/assets/skillprobe/lang/en_us.lang'
    [IO.File]::AppendAllText($language,"`n# deliberate changed delivery fixture`n")
    $changed=Invoke-Check
    if($changed.artifact.sha256 -eq $baseline.artifact.sha256){throw 'Deliberate changed bytes absent'}
    $rejected=Invoke-Export 1
    if($rejected.message -notmatch 'Bump the mod version'){throw 'Same-version change not rejected'}
    $build=Join-Path $workspace 'build.gradle'
    [IO.File]::WriteAllText($build,[IO.File]::ReadAllText($build).Replace("version = '1.0.0'","version = '1.0.1'"))
    $bad=Invoke-Check 1
    if([IO.File]::ReadAllText($bad.log) -notmatch 'differs from Gradle version'){throw 'Wrong metadata fault cause'}
    $stale=Invoke-Export 1
    if($stale.message -notmatch 'fresh mod PASS'){throw 'Failed mod evidence allowed export'}
    foreach($path in @((Join-Path $workspace 'src/main/java/com/example/skillprobe/SkillProbeMod.java'),(Join-Path $workspace 'src/main/resources/mcmod.info'))){[IO.File]::WriteAllText($path,[IO.File]::ReadAllText($path).Replace('1.0.0','1.0.1'))}
    $repaired=Invoke-Check;$second=Invoke-Export
    if($second.modVersion -ne '1.0.1' -or (Get-FileHash $first.artifact.path).Hash -ne $first.artifact.sha256){throw 'Version/export preservation failed'}
    $result=@{status='PASS';workspace=$workspace;kitArchiveSha256=$release.archiveSha256;checks=$checks;first=$first;rejected=$rejected;staleRejected=$stale;second=$second;scope='Generic seeded starter: changed-version delivery guard, deliberate metadata mismatch, repaired version and preserved old export; no gameplay assertion'}
    $exit=0
}catch{$result=@{status='FAIL';message=$_.Exception.Message;workspace=$workspace;checks=$checks};$exit=1}
$folder=Join-Path $root ('evidence/'+$version);New-Item -ItemType Directory -Force -Path $folder | Out-Null
$json=$result | ConvertTo-Json -Depth 10
[IO.File]::WriteAllText((Join-Path $folder 'version-delivery.json'),$json)
[pscustomobject]$result | Select-Object status,message,workspace | ConvertTo-Json -Compress
exit $exit
