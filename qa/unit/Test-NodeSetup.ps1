# Real setup/prompt flow with synthetic prerequisites/installers; no host changes.
[CmdletBinding()]
param()
$ErrorActionPreference='Stop'
$root=Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$fixture=Join-Path $root ('.work/node setup space/'+[Guid]::NewGuid().ToString('N'))
$tools=Join-Path $fixture 'tools'
$nodeHome=Join-Path $fixture 'node home'
New-Item -ItemType Directory -Force -Path $tools,$nodeHome,(Join-Path $nodeHome 'node_modules/npm/bin'),(Join-Path $fixture '.codex') | Out-Null
Copy-Item -LiteralPath (Join-Path $root 'kit/tools/Setup.ps1') -Destination $tools
Copy-Item -LiteralPath (Join-Path $root 'kit/.codex/config.toml') -Destination (Join-Path $fixture '.codex')
$code=@'
using System; using System.IO;
public class NodeSetupProbe {
 public static int Main(string[] args) {
  string home=Path.GetDirectoryName(System.Reflection.Assembly.GetExecutingAssembly().Location);
  string root=Path.GetDirectoryName(home);
  File.AppendAllText(Path.Combine(root,"node-calls"),string.Join(" ",args)+Environment.NewLine);
  if(args.Length==1 && args[0]=="--version") { Console.WriteLine(File.ReadAllText(Path.Combine(home,"version"))); return 0; }
  if(args.Length==2 && args[1]=="--version") { Console.WriteLine("10.0.0"); return 0; }
  if(args.Length>1 && args[1]=="--prefix") {
   Console.WriteLine("npm stdout fixture"); Console.Error.WriteLine("npm stderr fixture");
   if(File.Exists(Path.Combine(root,"hang-npm"))) System.Threading.Thread.Sleep(10000);
   return File.Exists(Path.Combine(root,"fail-npm"))?1:0;
  }
  return File.Exists(Path.Combine(root,"fail-module"))?1:0;
 }
}
'@
Add-Type -TypeDefinition $code -OutputAssembly (Join-Path $nodeHome 'node.exe') -OutputType ConsoleApplication
[IO.File]::WriteAllText((Join-Path $nodeHome 'node_modules/npm/bin/npm-cli.js'),'fixture')
[IO.File]::WriteAllText((Join-Path $nodeHome 'version'),'v24.0.0')
New-Item -ItemType Directory -Force -Path (Join-Path $fixture 'mcp') | Out-Null
foreach($name in @('package.json','package-lock.json')){[IO.File]::WriteAllText((Join-Path $fixture ('mcp/'+$name)),'{}')}
$java=@'
$fixtureRoot=Split-Path -Parent $PSScriptRoot
function Test-KitAdministrator { $true }
function Request-KitMachineEnvironmentChoice { 'N' }
function Get-KitJavaSelection { @{gradle=@{home=$fixtureRoot;java='fixture-java'};java8=@{home=$fixtureRoot;javac='fixture-javac'}} }
function Get-KitJava25 { @{home=$fixtureRoot;java='fixture-java'} }
function Set-KitJavaProcess { param($Selection) }
function Set-KitMachineEnvironment { param($Selection,$Decision); @{changed=$false} }
function Invoke-KitWinget {
 param($PackageId,$Log)
 if($PackageId -ne 'OpenJS.NodeJS.LTS'){throw 'Wrong package'}
 [IO.File]::AppendAllText((Join-Path $fixtureRoot 'installs'),'node,')
 if(Test-Path (Join-Path $fixtureRoot 'fail-install')){throw 'synthetic Node installer failure'}
 [IO.File]::WriteAllText($Log,'fixture installer')
 if(Test-Path (Join-Path $fixtureRoot 'unverified')){return}
 [IO.File]::WriteAllText((Join-Path $fixtureRoot 'available'),'yes')
 [IO.File]::WriteAllText((Join-Path $fixtureRoot 'node home/version'),'v24.0.0')
}
'@
[IO.File]::WriteAllText((Join-Path $tools 'JavaEnvironment.ps1'),$java)
$node=[IO.File]::ReadAllText((Join-Path $root 'kit/tools/NodeEnvironment.ps1'))
$overrides=@'
function Get-KitNodeCandidates {
 if(Test-Path (Join-Path $fixtureRoot 'available')){Join-Path $fixtureRoot 'node home/node.exe'}
}
'@
[IO.File]::WriteAllText((Join-Path $tools 'NodeEnvironment.ps1'),$node+"`n"+$overrides)
$cases=@()
function Run-Case {
 param($Name,$Arguments,$Answer,$Expected,$State)
 $start=New-Object Diagnostics.ProcessStartInfo
 $start.FileName=(Get-Command powershell.exe).Source
 $start.Arguments='-NoProfile -File "'+(Join-Path $tools 'Setup.ps1')+'" '+$Arguments
 $start.UseShellExecute=$false;$start.CreateNoWindow=$true
 $start.RedirectStandardOutput=$true;$start.RedirectStandardError=$true;$start.RedirectStandardInput=$true
 $p=[Diagnostics.Process]::Start($start)
 try{
  $out=$p.StandardOutput.ReadToEndAsync();$err=$p.StandardError.ReadToEndAsync()
  if($null -ne $Answer){$p.StandardInput.WriteLine($Answer)}
  $p.StandardInput.Close()
  if(-not $p.WaitForExit(15000)){$p.Kill();throw 'Fixture timed out'}
  $text=$out.GetAwaiter().GetResult();$progress=$err.GetAwaiter().GetResult()
  [IO.File]::WriteAllText((Join-Path $fixture ($Name+'.log')),$progress+"`n"+$text)
  $result=$text | ConvertFrom-Json
  if($p.ExitCode -ne $Expected -or ($State -and $result.mcpState -ne $State)){throw ($Name+' unexpected outcome: '+$text)}
  return $result
 }finally{$p.Dispose()}
}
$r=Run-Case 'no-node' '-InstallMissing -PromptMachineEnvironment -PromptNode' 'n' 0 'NOT_REQUESTED'
if(Test-Path (Join-Path $fixture 'node-calls')){throw 'N invoked Node'}
$cases+='N performs no Node checks/install/npm'
$r=Run-Case 'eof' '-InstallMissing -PromptNode' $null 1 ''
if($r.message -notmatch 'No Y/N response' -or (Test-Path (Join-Path $fixture 'installs'))){throw 'EOF did not stop before install'}
$cases+='EOF stops before install'
$r=Run-Case 'install' '-InstallMissing -PromptNode' "invalid`ny" 0 'MODULE_LOAD_PASS'
if($r.installed.Count -ne 1 -or $r.nodeDecision -ne 'Y'){throw 'Y did not install'}
$cases+='invalid answer reprompts; Y installs then npm/module check'
$r=Run-Case 'repeat' '-InstallMissing -WithMcp' $null 0 'MODULE_LOAD_PASS'
if($r.installed.Count -ne 0 -or [IO.File]::ReadAllText((Join-Path $fixture 'installs')) -ne 'node,'){throw 'Compatible Node reinstalled'}
if(-not $r.dependenciesReused -or $r.dependencyLog){throw 'Matching dependencies were not reused'}
$cases+='compatible Node/npm reused'
$cases+='matching dependency identity and module readiness skip npm ci'
[IO.File]::AppendAllText((Join-Path $fixture 'mcp/package-lock.json'),"`n")
$r=Run-Case 'changed-lock' '-WithMcp' $null 0 'MODULE_LOAD_PASS'
if($r.dependenciesReused -or -not $r.dependencyLog){throw 'Changed lock reused dependencies'}
if([IO.File]::ReadAllText($r.dependencyLog) -notmatch 'npm stderr fixture'){throw 'npm stderr log missing'}
$cases+='changed lock reinstalls; stdout/stderr retained'
$npmFixture=Join-Path $nodeHome 'node_modules/npm/bin/npm-cli.js'
Remove-Item -LiteralPath $npmFixture
$r=Run-Case 'missing-npm' '-WithMcp' $null 1 ''
if($r.message -notmatch 'npm'){throw 'Missing npm accepted'}
[IO.File]::WriteAllText($npmFixture,'fixture')
$cases+='missing npm rejected'
[IO.File]::WriteAllText((Join-Path $nodeHome 'version'),'v20.0.0')
$r=Run-Case 'old-node' '-WithMcp' $null 1 ''
if($r.message -notmatch 'InstallMissing'){throw 'Missing remediation'}
$r=Run-Case 'replace-old' '-WithMcp -InstallMissing' $null 0 'MODULE_LOAD_PASS'
$cases+='old Node rejected and installer reverified'
foreach($flag in @('fail-npm','fail-module')){
 [IO.File]::WriteAllText((Join-Path $fixture $flag),'')
 $r=Run-Case $flag '-WithMcp -RepairMcp' $null 1 'CHECKING'
 Remove-Item -LiteralPath (Join-Path $fixture $flag)
 $cases+=$flag+' propagates nonzero'
}
[IO.File]::WriteAllText((Join-Path $fixture 'hang-npm'),'')
$r=Run-Case 'npm-timeout' '-WithMcp -RepairMcp -DependencyTimeoutSeconds 1' $null 1 'CHECKING'
if($r.message -notmatch 'timed out' -or -not (Test-Path -LiteralPath $r.dependencyLog)){throw 'Timeout remediation/log missing'}
Remove-Item -LiteralPath (Join-Path $fixture 'hang-npm')
$cases+='bounded npm timeout stops process and retains log'
Remove-Item -LiteralPath (Join-Path $fixture 'available')
foreach($flag in @('fail-install','unverified')){
 [IO.File]::WriteAllText((Join-Path $fixture $flag),'')
 $r=Run-Case $flag '-WithMcp -InstallMissing' $null 1 'CHECKING'
 if($r.message -notmatch 'installer|verified'){throw 'Installer failure remediation missing'}
 Remove-Item -LiteralPath (Join-Path $fixture $flag)
 $cases+=$flag+' stops setup'
}
# Exercise the real shared installer argument/log plumbing using a fake WinGet.
. (Join-Path $root 'kit/tools/JavaEnvironment.ps1')
function Get-Command {
 param($Name,$ErrorAction)
 if($Name -eq 'winget.exe'){return [pscustomobject]@{Source=(Join-Path $nodeHome 'node.exe')}}
 Microsoft.PowerShell.Core\Get-Command $Name -ErrorAction SilentlyContinue
}
Invoke-KitWinget -PackageId OpenJS.NodeJS.LTS -Log (Join-Path $fixture 'real-installer-plumbing.log')
if([IO.File]::ReadAllText((Join-Path $fixture 'node-calls')) -notmatch 'install --id OpenJS.NodeJS.LTS --exact --source winget --architecture x64'){throw 'Actual installer arguments incorrect'}
$cases+='real installer uses exact x64 Node LTS package and logs'
@{status='PASS';fixture=$fixture;cases=$cases;limits='Synthetic Node/npm/installer transitions; no actual MSI/UAC or app connection claim'} | ConvertTo-Json -Depth 4 -Compress
