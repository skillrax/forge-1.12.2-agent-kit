# Exercise the real native launcher with a fake executable, never a real installer.
[CmdletBinding()]
param()
$ErrorActionPreference='Stop'
$root=Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$fixture=Join-Path $root ('.work/winget launcher space/'+[Guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Force -Path $fixture | Out-Null
$stub=Join-Path $fixture 'winget.exe'
$code=@'
using System; using System.IO;
public class KitWingetProbe {
 public static int Main(string[] args) {
  string folder=Path.GetDirectoryName(System.Reflection.Assembly.GetExecutingAssembly().Location);
  File.WriteAllLines(Path.Combine(folder,"arguments.txt"),args);
  Console.WriteLine("synthetic installer stdout"); Console.Error.WriteLine("synthetic installer stderr");
  return int.Parse(File.ReadAllText(Path.Combine(folder,"exit.txt")));
 }
}
'@
Add-Type -TypeDefinition $code -OutputAssembly $stub -OutputType ConsoleApplication
. (Join-Path $root 'kit/tools/JavaEnvironment.ps1')
function Get-Command { [CmdletBinding()]param([string]$Name) if($Name -eq 'winget.exe'){[pscustomobject]@{Source=$stub}} }
[IO.File]::WriteAllText((Join-Path $fixture 'exit.txt'),'0')
Invoke-KitWinget -Major 8 -Log (Join-Path $fixture 'success.log')
$args=[IO.File]::ReadAllLines((Join-Path $fixture 'arguments.txt'))
if(($args -join ' ') -ne 'install --id EclipseAdoptium.Temurin.8.JDK --exact --source winget --architecture x64 --custom ADDLOCAL=FeatureMain'){throw 'Wrong installer arguments'}
$log=[IO.File]::ReadAllText((Join-Path $fixture 'success.log'))
if($log -notmatch 'stdout' -or $log -notmatch 'stderr'){throw 'Installer stream lost'}
[IO.File]::WriteAllText((Join-Path $fixture 'exit.txt'),'5')
$failed=$false
try{Invoke-KitWinget -Major 25 -Log (Join-Path $fixture 'failed.log')}catch{$failed=$_.Exception.Message -match 'returned 5'}
if(-not $failed){throw 'Installer failure not propagated'}
function Get-Command { [CmdletBinding()]param([string]$Name) return $null }
$missing=$false
try{Invoke-KitWinget -Major 25 -Log (Join-Path $fixture 'missing.log')}catch{$missing=$_.Exception.Message -match 'App Installer'}
if(-not $missing){throw 'Missing WinGet remediation absent'}
@{status='PASS';fixture=$fixture;cases=@('exact x64 JDK ID/FeatureMain arguments','both native streams retained','nonzero installer propagated','missing WinGet remediation');scope='real launcher, fake executable; no MSI/UAC/global-variable installation claim'} | ConvertTo-Json -Compress
