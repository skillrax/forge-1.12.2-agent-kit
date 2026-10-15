# Synthetic executable probes/installer transitions; no host installation or global environment writes.
[CmdletBinding()]
param()
$ErrorActionPreference='Stop'
$root=Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$fixture=Join-Path $root ('.work/java setup space/'+[Guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Force -Path $fixture | Out-Null
$stub=Join-Path $fixture 'probe.exe'
$code=@'
using System;
using System.IO;
public class KitProbe {
 public static int Main(string[] args) {
  string exe=System.Reflection.Assembly.GetExecutingAssembly().Location;
  string[] data=File.ReadAllText(Path.Combine(Path.GetDirectoryName(exe),"../config")).Split(',');
  string version=data[0]=="8"?"1.8":data[0];
  if(Path.GetFileName(exe)=="javac.exe") Console.WriteLine("javac "+version+".0_test");
  else {Console.Error.WriteLine("java.specification.version = "+version);Console.Error.WriteLine("sun.arch.data.model = "+data[1]);Console.Error.WriteLine("openjdk version \""+version+".0_test\"");}
  return 0;
 }
}
'@
Add-Type -TypeDefinition $code -OutputAssembly $stub -OutputType ConsoleApplication
foreach($spec in @(@{name='jdk17';major=17;bits=64},@{name='jdk25';major=25;bits=64},@{name='jdk8';major=8;bits=64},@{name='jre8';major=8;bits=32},@{name='jdk8x86';major=8;bits=32})){
    $folder=Join-Path $fixture $spec.name;New-Item -ItemType Directory -Force -Path (Join-Path $folder 'bin') | Out-Null
    [IO.File]::WriteAllText((Join-Path $folder 'config'),($spec.major.ToString()+','+$spec.bits))
    Copy-Item -LiteralPath $stub -Destination (Join-Path $folder 'bin/java.exe')
    if($spec.name -ne 'jre8'){Copy-Item -LiteralPath $stub -Destination (Join-Path $folder 'bin/javac.exe')}
}
. (Join-Path $root 'kit/tools/JavaEnvironment.ps1')
if(Read-KitJava (Join-Path $fixture 'jre8')){throw 'JRE accepted'}
if(Read-KitJava (Join-Path $fixture 'jdk8x86')){throw '32-bit JDK accepted'}
foreach($major in @(8,25)){if((Read-KitJava (Join-Path $fixture ('jdk'+$major))).major -ne $major){throw 'Valid probe rejected'}}
$savedJava=$env:JAVA_HOME
function Invoke-SetupFixture {
    param([string]$Extra,[string]$Name)
    $start=New-Object Diagnostics.ProcessStartInfo
    $start.FileName=(Get-Command powershell.exe).Source;$start.Arguments='-NoProfile -File "'+(Join-Path $tools 'Setup.ps1')+'" '+$Extra
    $start.UseShellExecute=$false;$start.CreateNoWindow=$true;$start.RedirectStandardOutput=$true;$start.RedirectStandardError=$true
    $p=[Diagnostics.Process]::Start($start)
    try{
        $stdout=$p.StandardOutput.ReadToEndAsync();$stderr=$p.StandardError.ReadToEndAsync();$p.WaitForExit()
        $text=$stdout.GetAwaiter().GetResult();[IO.File]::WriteAllText((Join-Path $fixture ($Name+'-progress.log')),$stderr.GetAwaiter().GetResult())
        if([IO.File]::ReadAllText((Join-Path $fixture 'reports/setup/setup-latest.json')) -ne $text.Trim()){throw 'Saved setup outcome differs from stdout'}
        [pscustomobject]@{code=$p.ExitCode;result=($text | ConvertFrom-Json)}
    }finally{$p.Dispose()}
}
try{
    $env:JAVA_HOME=Join-Path $fixture 'jre8'
    function Get-KitJavaCandidates { @((Join-Path $fixture 'jre8'),(Join-Path $fixture 'jdk8x86'),(Join-Path $fixture 'jdk25'),(Join-Path $fixture 'jdk8')) }
    $selection=Get-KitJavaSelection
    if($selection.gradle.major -ne 25 -or $selection.java8.major -ne 8){throw 'Wrong JDK selection'}
    Set-KitJavaProcess $selection
    if($env:JAVA_HOME -ne $selection.gradle.home -or [Environment]::GetEnvironmentVariable('ORG_GRADLE_PROJECT_org.gradle.java.installations.paths') -ne $selection.java8.home){throw 'Process selection not applied'}
}finally{$env:JAVA_HOME=$savedJava}
# Exercise full setup script with a fake installer and changing discovery, outside the host.
$tools=Join-Path $fixture 'tools';New-Item -ItemType Directory -Force -Path $tools | Out-Null
Copy-Item -LiteralPath (Join-Path $root 'kit/tools/Setup.ps1') -Destination $tools
$helper=[IO.File]::ReadAllText((Join-Path $root 'kit/tools/JavaEnvironment.ps1'))
$overrides=@'
function Get-KitJavaCandidates {
    if(Test-Path (Join-Path $kitJavaWorkspace 'installed17')){Join-Path $kitJavaWorkspace 'jdk17'}
    foreach($major in @(8,25)){if(Test-Path (Join-Path $kitJavaWorkspace ('installed'+$major))){Join-Path $kitJavaWorkspace ('jdk'+$major)}}
    if(Test-Path (Join-Path $kitJavaWorkspace 'broken25')){Join-Path $kitJavaWorkspace 'jdk-25-broken'}
}
function Invoke-KitWinget {
    param([int]$Major,[string]$Log)
    [IO.File]::AppendAllText((Join-Path $kitJavaWorkspace 'events'),('install'+$Major+','))
    if(Test-Path (Join-Path $kitJavaWorkspace 'fail-install')){throw 'synthetic installer failure; inspect installer state before retrying'}
    if(Test-Path (Join-Path $kitJavaWorkspace 'broken25')){return 'already-installed'}
    [IO.File]::AppendAllText((Join-Path $kitJavaWorkspace 'install-order'),($Major.ToString()+','))
    [IO.File]::WriteAllText($Log,'synthetic installer transition')
    if(Test-Path (Join-Path $kitJavaWorkspace 'unverified-install')){return}
    [IO.File]::WriteAllText((Join-Path $kitJavaWorkspace ('installed'+$Major)),'installed')
}
function Test-KitAdministrator { -not (Test-Path (Join-Path $kitJavaWorkspace 'not-admin')) }
function Read-KitEnvironmentAnswer {
    [IO.File]::AppendAllText((Join-Path $kitJavaWorkspace 'events'),'prompt,')
    if(Test-Path (Join-Path $kitJavaWorkspace 'eof')){return $null}
    if(Test-Path (Join-Path $kitJavaWorkspace 'answer-y')){return 'y'}
    return 'n'
}
function Read-KitPersistentEnvironment { param($Name,$Scope);return $null }
function Write-KitPersistentEnvironment {
    param($Name,$Value,$Scope)
    if(-not (Test-Path (Join-Path $kitJavaWorkspace 'answer-y'))){throw 'N must never write persistent settings'}
    [IO.File]::AppendAllText((Join-Path $kitJavaWorkspace 'events'),'write,')
}
'@
[IO.File]::WriteAllText((Join-Path $tools 'JavaEnvironment.ps1'),$helper+"`n"+$overrides)
$savedJava=$env:JAVA_HOME
try{
    $env:JAVA_HOME=Join-Path $fixture 'jre8'
    $out=Invoke-SetupFixture '' 'missing'
    if($out.code -ne 1 -or $out.result.message -notmatch 'InstallMissing'){throw 'Missing-JDK remediation absent'}
    $out=Invoke-SetupFixture '-InstallMissing' 'install'
    if($out.code -ne 0 -or $out.result.installed.Count -ne 2 -or [IO.File]::ReadAllText((Join-Path $fixture 'install-order')) -ne '8,25,'){throw 'Bootstrap failed'}
    $out=Invoke-SetupFixture '-InstallMissing' 'repeat'
    if($out.code -ne 0 -or $out.result.installed.Count -ne 0){throw 'Bootstrap reinstalled valid JDKs'}
    if($env:JAVA_HOME -ne (Join-Path $fixture 'jre8')){throw 'Child setup mutated caller environment'}
    Remove-Item -LiteralPath (Join-Path $fixture 'installed25')
    [IO.File]::WriteAllText((Join-Path $fixture 'installed17'),'installed')
    $env:JAVA_HOME=Join-Path $fixture 'jdk17'
    [IO.File]::WriteAllText((Join-Path $fixture 'events'),'')
    $out=Invoke-SetupFixture '-InstallMissing -PromptMachineEnvironment' 'prompt-no'
    if($out.code -ne 0 -or $out.result.gradleJavaHome -ne (Join-Path $fixture 'jdk25') -or $out.result.machineEnvironment.changed -ne $false -or $out.result.installed.Count -ne 1){throw 'Interactive launcher did not require25 or respect N'}
    if([IO.File]::ReadAllText((Join-Path $fixture 'events')) -ne 'prompt,install25,'){throw 'Choice was not before installation or N wrote settings'}
    Remove-Item -LiteralPath (Join-Path $fixture 'installed25')
    [IO.File]::WriteAllText((Join-Path $fixture 'events'),'')
    [IO.File]::WriteAllText((Join-Path $fixture 'answer-y'),'')
    $out=Invoke-SetupFixture '-InstallMissing -PromptMachineEnvironment' 'prompt-yes'
    if($out.code -ne 0 -or -not $out.result.machineEnvironment.changed -or $out.result.machineEnvironment.javaHome -ne (Join-Path $fixture 'jdk25') -or [IO.File]::ReadAllText((Join-Path $fixture 'events')) -ne 'prompt,install25,write,write,'){throw 'Y applied before verified installation or prompted twice'}
    Remove-Item -LiteralPath (Join-Path $fixture 'installed25')
    [IO.File]::WriteAllText((Join-Path $fixture 'events'),'')
    [IO.File]::WriteAllText((Join-Path $fixture 'fail-install'),'')
    $out=Invoke-SetupFixture '-InstallMissing -PromptMachineEnvironment' 'prompt-yes-install-failure'
    if($out.code -ne 1 -or $out.result.message -notmatch 'synthetic installer failure' -or [IO.File]::ReadAllText((Join-Path $fixture 'events')) -ne 'prompt,install25,'){throw 'Failed installation wrote persistent settings'}
    Remove-Item -LiteralPath (Join-Path $fixture 'fail-install')
    [IO.File]::WriteAllText((Join-Path $fixture 'events'),'')
    [IO.File]::WriteAllText((Join-Path $fixture 'unverified-install'),'')
    $out=Invoke-SetupFixture '-InstallMissing -PromptMachineEnvironment' 'prompt-yes-verification-failure'
    if($out.code -ne 1 -or $out.result.message -notmatch 'could not be verified' -or [IO.File]::ReadAllText((Join-Path $fixture 'events')) -ne 'prompt,install25,'){throw 'Unverified installer success wrote persistent settings'}
    Remove-Item -LiteralPath (Join-Path $fixture 'unverified-install')
    # WinGet says the JDK is installed (no applicable upgrade) but its folder is damaged: name the folder and the repair.
    New-Item -ItemType Directory -Force -Path (Join-Path $fixture 'jdk-25-broken/bin') | Out-Null
    Copy-Item -LiteralPath $stub -Destination (Join-Path $fixture 'jdk-25-broken/bin/java.exe')
    [IO.File]::WriteAllText((Join-Path $fixture 'broken25'),'')
    $out=Invoke-SetupFixture '-InstallMissing -PromptMachineEnvironment' 'already-installed-broken'
    if($out.code -ne 1 -or $out.result.message -notmatch 'WinGet says Temurin JDK25 is installed' -or $out.result.message -notmatch 'jdk-25-broken: bin\\java\.exe or bin\\javac\.exe missing' -or $out.result.message -notmatch 'winget uninstall --id EclipseAdoptium\.Temurin\.25\.JDK'){throw "Already-installed broken JDK not explained: $($out.result.message)"}
    Remove-Item -LiteralPath (Join-Path $fixture 'broken25')
    [IO.File]::WriteAllText((Join-Path $fixture 'events'),'')
    [IO.File]::WriteAllText((Join-Path $fixture 'eof'),'')
    $out=Invoke-SetupFixture '-InstallMissing -PromptMachineEnvironment' 'prompt-eof'
    if($out.code -ne 1 -or $out.result.message -notmatch 'No Y/N response' -or [IO.File]::ReadAllText((Join-Path $fixture 'events')) -ne 'prompt,'){throw 'EOF downloaded or wrote settings'}
    [IO.File]::WriteAllText((Join-Path $fixture 'not-admin'),'')
    [IO.File]::WriteAllText((Join-Path $fixture 'events'),'')
    $out=Invoke-SetupFixture '-InstallMissing -PromptMachineEnvironment' 'non-admin'
    if($out.code -ne 1 -or $out.result.message -notmatch 'Run as administrator' -or [IO.File]::ReadAllText((Join-Path $fixture 'events'))){throw 'Launcher elevation remediation absent or prompted before elevation'}
}finally{$env:JAVA_HOME=$savedJava}
@{status='PASS';fixture=$fixture;cases=@('JRE rejected','32-bit JDK rejected','64-bit8/25 probes','bad JAVA_HOME discovery','process-local selection','missing remediation','8 then25 simulated install','idempotent setup','caller environment unchanged','interactive requires25 even with17','early N preserves settings','early Y applied after verified install without second prompt','Y installer failure no writes','unverified installer success no writes','WinGet already-installed but broken JDK explained','EOF before downloads/writes','launcher nonadmin before prompt');limits='Installer/elevation/registry are simulated; no actual MSI/UAC/global-variable installation claim'} | ConvertTo-Json -Compress
