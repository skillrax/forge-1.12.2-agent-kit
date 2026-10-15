# Focused real build checks for workflow changes, not gameplay qualification.
[CmdletBinding()]
param()
$ErrorActionPreference='Stop'
$root=Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$fixture=Join-Path $root ('.work/beta workflow space/'+[Guid]::NewGuid().ToString('N'))
$shell=(Get-Process -Id $PID).Path
$cases=@();$runs=@()
function Invoke-File {
 param($Path,[string[]]$Arguments,[int]$Expected=0)
 $start=New-Object Diagnostics.ProcessStartInfo
 $start.FileName=$shell;$start.Arguments='-NoProfile -File "'+$Path+'" '+($Arguments -join ' ')
 $start.UseShellExecute=$false;$start.CreateNoWindow=$true;$start.RedirectStandardOutput=$true;$start.RedirectStandardError=$true
 $p=[Diagnostics.Process]::Start($start)
 try{
  $stdout=$p.StandardOutput.ReadToEndAsync();$stderr=$p.StandardError.ReadToEndAsync()
  if(-not $p.WaitForExit(180000)){$p.Kill();throw 'Workflow check exceeded 3 minutes'}
  $text=$stdout.GetAwaiter().GetResult();$progress=$stderr.GetAwaiter().GetResult()
  if($p.ExitCode -ne $Expected){throw ('Unexpected exit: '+$text+' '+$progress)}
  $text | ConvertFrom-Json
 }finally{$p.Dispose()}
}
function Check-Mod {
 param([string[]]$Extra=@(),[int]$Expected=0)
 $result=Invoke-File (Join-Path $fixture 'agent.ps1') (@('-Action check -Profile mod -TimeoutSeconds 120')+$Extra) $Expected
 $script:runs+=@{runId=$result.runId;status=$result.status;log=$result.log;cleanReason=$result.cleanReason}
 return $result
}
try{
 $bad=Join-Path $root ('kit/tools/recursive-'+[Guid]::NewGuid().ToString('N'))
 $reject=Invoke-File (Join-Path $root 'kit/tools/New-Checkout.ps1') @('-Destination "'+$bad+'"') 1
 if($reject.message -notmatch 'outside' -or (Test-Path -LiteralPath $bad)){throw 'Descendant destination created'}
 $copy=Invoke-File (Join-Path $root 'kit/tools/New-Checkout.ps1') @('-Destination "'+$fixture+'"')
 if(Test-Path (Join-Path $fixture 'reports')){throw 'Copied stale reports'}
 $cases+='source-only checkout with spaces; descendant rejected before creation'
 $missingDest=$fixture+'-missing-check'
 Move-Item -LiteralPath (Join-Path $fixture 'README.md') -Destination (Join-Path $fixture 'README.saved')
 $reject=Invoke-File (Join-Path $fixture 'tools/New-Checkout.ps1') @('-Destination "'+$missingDest+'"') 1
 if($reject.message -notmatch 'Required source' -or (Test-Path -LiteralPath $missingDest)){throw 'Missing source created destination'}
 Move-Item -LiteralPath (Join-Path $fixture 'README.saved') -Destination (Join-Path $fixture 'README.md')
 $cases+='required entries preflight before creating destination'
 $init=Invoke-File (Join-Path $fixture 'tools/Initialize-Mod.ps1') @('-ModId betacheck -Name "Beta Check" -Package demo.mod -Version 1.0.0')
 $before=(Get-FileHash -LiteralPath (Join-Path $fixture 'build.gradle') -Algorithm SHA256).Hash
 $reject=Invoke-File (Join-Path $fixture 'tools/Initialize-Mod.ps1') @('-ModId secondmod') 1
 if($reject.message -notmatch 'already contains' -or (Get-FileHash -LiteralPath (Join-Path $fixture 'build.gradle') -Algorithm SHA256).Hash -ne $before){throw 'Initializer overwrote existing mod'}
 $cases+='initializer creates matching identity/metadata and rejects repeated initialization'
 $java=Join-Path $fixture 'src/main/java/demo';$resources=Join-Path $fixture 'src/main/resources'
 New-Item -ItemType Directory -Force -Path $java,$resources | Out-Null
 [IO.File]::WriteAllText((Join-Path $java 'Old.java'),'package demo; public class Old {}')
 [IO.File]::WriteAllText((Join-Path $resources 'old-resource.txt'),'old')
 $first=Check-Mod
 if(-not $first.cleanReason -or -not $first.artifact){throw 'First baseline not clean/identified'}
 $next=Invoke-File (Join-Path $fixture 'agent.ps1') @('-Action status')
 if($next.nextAction -notmatch 'Export the validated'){throw 'Fresh mod export recommendation missing'}
 $repeat=Check-Mod
 if($repeat.cleanReason -or $repeat.sourcePaths -or $repeat.buildIdentity){throw 'Repeat not incremental/compact'}
 if([IO.File]::ReadAllText($repeat.log) -notmatch 'compileJava UP-TO-DATE'){throw 'Unchanged compilation was not reused'}
 $cases+='fresh mod recommends export; unchanged check reuses compilation and omits inventories'
 Add-Type -AssemblyName System.IO.Compression.FileSystem
 Remove-Item -LiteralPath (Join-Path $java 'Old.java'),(Join-Path $resources 'old-resource.txt')
 [IO.File]::WriteAllText((Join-Path $java 'New.java'),'package demo; public class New {}')
 [IO.File]::WriteAllText((Join-Path $resources 'new-resource.txt'),'new')
 $stale=Invoke-File (Join-Path $fixture 'agent.ps1') @('-Action status')
 if($stale.nextAction -notmatch 'check -Profile mod'){throw 'Changed mod requested a redundant standalone environment check'}
 $renamed=Check-Mod
 if($renamed.cleanReason -ne 'source/resource path removed'){throw 'Removal did not clean'}
 $zip=[IO.Compression.ZipFile]::OpenRead($renamed.artifact.path)
 try{
  $names=@($zip.Entries | ForEach-Object {$_.FullName})
  if('demo/Old.class' -in $names -or 'old-resource.txt' -in $names -or 'demo/New.class' -notin $names -or 'new-resource.txt' -notin $names){throw 'Renamed source/resources packaged incorrectly'}
 }finally{$zip.Dispose()}
 $cases+='changed mod recommends mod profile; renamed/deleted class and resource removed from actual SRG JAR'
 [IO.File]::WriteAllText((Join-Path $fixture 'build/libs/000-decoy-srg.jar'),'not a jar')
 $exact=Check-Mod
 if($exact.cleanReason -or $exact.artifact.path -match 'decoy'){throw 'Decoy selected or unnecessary clean'}
 $export=Invoke-File (Join-Path $fixture 'tools/Export-Release.ps1') @()
 if($export.artifact.sha256 -ne $exact.artifact.sha256){throw 'Export did not preserve inspected artifact'}
 $cases+='extra JAR ignored; export preserves exact inspected bytes'
 [IO.File]::WriteAllText((Join-Path $java 'Broken.java'),'package demo; public class Broken { invalid }')
 $failure=Check-Mod @() 1
 if(-not $failure.diagnostics -or ($failure.diagnostics -join '|') -notmatch 'error:'){throw 'Compile failure did not return immediate diagnostics'}
 Remove-Item -LiteralPath (Join-Path $java 'Broken.java')
 $recovery=Check-Mod @('-Clean')
 if($recovery.cleanReason -ne 'requested'){throw 'Explicit clean recovery missing'}
 $cases+='real compiler failure returns causes; explicit clean recovery passes'
 $result=@{status='PASS';fixture=$fixture;cases=$cases;runs=$runs;limits='Warm pinned builds/export only; no gameplay, cold-PC or token-savings claim'}
 $evidence=Join-Path $root 'evidence/beta-workflow';New-Item -ItemType Directory -Force -Path $evidence | Out-Null
 $result | ConvertTo-Json -Depth 6 | Set-Content -LiteralPath (Join-Path $evidence 'build-checks.json') -Encoding UTF8
 $result | ConvertTo-Json -Depth 6 -Compress
}catch{
 @{status='FAIL';fixture=$fixture;message=$_.Exception.Message;cases=$cases;runs=$runs} | ConvertTo-Json -Depth 5 -Compress
 exit 1
}
