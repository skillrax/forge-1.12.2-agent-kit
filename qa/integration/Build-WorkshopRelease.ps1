[CmdletBinding()]
param([Parameter(Mandatory)][string]$SourceWorkspace,[Parameter(Mandatory)][string]$Acceptance)
$ErrorActionPreference='Stop'
$taskRoot=Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$SourceWorkspace=[IO.Path]::GetFullPath($SourceWorkspace)
$native=Get-Content -Encoding UTF8 -LiteralPath $Acceptance -Raw | ConvertFrom-Json
if($native.status -ne 'PASS' -or $native.faultCoverage -ne 'PASS'){throw 'Clean native/regression evidence required'}
$sourceCheck=Get-Content -Encoding UTF8 (Join-Path $SourceWorkspace 'reports/testing-tools/mod-latest.json') -Raw | ConvertFrom-Json
$status=& powershell -NoProfile -File (Join-Path $SourceWorkspace 'agent.ps1') -Action status
if($LASTEXITCODE -ne 0){throw 'Cannot check source freshness'}
$identity=($status -join "`n") | ConvertFrom-Json
if($sourceCheck.status -ne 'PASS' -or $sourceCheck.inputFingerprint -ne $identity.inputFingerprint -or $sourceCheck.toolchainFingerprint -ne $identity.toolchainFingerprint){throw 'Stale source evidence'}
if($sourceCheck.artifact.sha256 -ne $native.mod.artifact.sha256){throw 'Recipient/native JAR mismatch'}
$fixture=Join-Path $taskRoot 'qa/fixtures/workshop/src/main'
foreach($file in (Get-ChildItem $fixture -Recurse -File)) {
    $relative=$file.FullName.Substring($fixture.Length+1)
    if((Get-FileHash $file.FullName).Hash -ne (Get-FileHash (Join-Path $SourceWorkspace ('src/main/'+$relative))).Hash){throw ('Source fixture mismatch '+$relative)}
}
$version=(Get-Content -Encoding UTF8 (Join-Path $SourceWorkspace 'src/main/resources/mcmod.info') -Raw | ConvertFrom-Json)[0].version;$dest=Join-Path $taskRoot ('releases/examples/kit-workshop/'+$version)
if(Test-Path $dest){throw 'Immutable release already exists'}
New-Item -ItemType Directory -Path $dest | Out-Null
$jar=Join-Path $dest ('kit-workshop-'+$version+'-srg.jar')
Copy-Item -LiteralPath $sourceCheck.artifact.path -Destination $jar
if((Get-FileHash $jar).Hash -ne $sourceCheck.artifact.sha256){throw 'JAR copy mismatch'}
foreach($name in @('README.md','PROMPT.md')){Copy-Item -LiteralPath (Join-Path $SourceWorkspace $name) -Destination $dest}
$allow=Get-Content -Encoding UTF8 (Join-Path $taskRoot 'packaging/files.json') -Raw | ConvertFrom-Json
$paths=@($allow.files)+@('PROMPT.md')+@(Get-ChildItem (Join-Path $SourceWorkspace 'src/main') -Recurse -File | ForEach-Object {$_.FullName.Substring($SourceWorkspace.Length+1).Replace('\','/')})
$paths=@($paths | Sort-Object -Unique)
if($paths -match 'evaluation|reports/|build-overlay|baseline|workshopchecks'){throw 'QA source inclusion'}
Add-Type -AssemblyName System.IO.Compression.FileSystem
$jarZip=[IO.Compression.ZipFile]::OpenRead($jar)
try{if($jarZip.Entries.FullName -match 'workshopchecks|EnvironmentProbe|Test\.class|\.java$'){throw 'QA JAR inclusion'}}finally{$jarZip.Dispose()}
$sourceZip=Join-Path $dest ('kit-workshop-'+$version+'-source.zip')
$zip=[IO.Compression.ZipFile]::Open($sourceZip,[IO.Compression.ZipArchiveMode]::Create)
try {
    foreach($path in $paths) {
        $file=Join-Path $SourceWorkspace $path
        if(-not [IO.File]::Exists($file)){throw ('Missing source file '+$path)}
        $entry=$zip.CreateEntry(('kit-workshop-'+$version+'/'+$path),[IO.Compression.CompressionLevel]::Optimal)
        $entry.LastWriteTime=[DateTimeOffset]::new(2020,1,1,0,0,0,[TimeSpan]::Zero)
        $stream=$entry.Open();try{$bytes=[IO.File]::ReadAllBytes($file);$stream.Write($bytes,0,$bytes.Length)}finally{$stream.Dispose()}
    }
}finally{$zip.Dispose()}
$verify=[IO.Compression.ZipFile]::OpenRead($sourceZip)
try{foreach($entry in $verify.Entries){$relative=$entry.FullName.Substring(('kit-workshop-'+$version+'/').Length);$stream=$entry.Open();$memory=New-Object IO.MemoryStream;try{$stream.CopyTo($memory);if([Convert]::ToBase64String($memory.ToArray()) -ne [Convert]::ToBase64String([IO.File]::ReadAllBytes((Join-Path $SourceWorkspace $relative)))){throw ('Archive mismatch '+$relative)}}finally{$stream.Dispose();$memory.Dispose()}}}finally{$verify.Dispose()}
$sourceFiles=@(Get-ChildItem (Join-Path $SourceWorkspace 'src/main') -Recurse -File | ForEach-Object {@{path=$_.FullName.Substring($SourceWorkspace.Length+1).Replace('\','/');sha256=(Get-FileHash $_.FullName).Hash}})
$manifest=[ordered]@{schemaVersion=1;private=$true;mod='Kit Capability Workshop';version=$version;toolkitVersion='0.3.0-alpha.3';minecraft='1.12.2';forge='14.23.5.2859';kitArchiveSha256=$native.kitArchiveSha256;jar=@{path=$jar;sha256=(Get-FileHash $jar).Hash;bytes=(Get-Item $jar).Length};sourceZip=@{path=$sourceZip;sha256=(Get-FileHash $sourceZip).Hash;bytes=(Get-Item $sourceZip).Length};sourceFiles=$sourceFiles;nativeAcceptance=[IO.Path]::GetFullPath($Acceptance);sourceMod=$sourceCheck.runId;nativeMod=$native.mod.runId;jarByteIdentical=$true;unitTests=0;graphical='PENDING_USER_TEST';realMultiplayer='NOT_RUN'}
$manifest | ConvertTo-Json -Depth 8 | Set-Content -Encoding UTF8 (Join-Path $dest 'manifest.json')
@{nativeAcceptance=$native;sourceBuild=$sourceCheck;scope=('Barebones Workshop'+$version+'; exact source archive entry verification; graphical/two-client acceptance recorded separately; no cold-machine claim')} | ConvertTo-Json -Depth 12 | Set-Content -Encoding UTF8 (Join-Path $dest 'provenance.json')
@($jar,$sourceZip) | ForEach-Object {('{0}  {1}' -f (Get-FileHash $_).Hash,[IO.Path]::GetFileName($_))} | Set-Content -Encoding ASCII (Join-Path $dest 'SHA256SUMS.txt')
$manifest | ConvertTo-Json -Depth 2 -Compress
