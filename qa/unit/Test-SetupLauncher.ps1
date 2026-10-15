# Real CMD launcher with a fake PowerShell executable; no elevation or host settings writes.
[CmdletBinding()]
param()
$ErrorActionPreference='Stop'
$root=Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$fixture=Join-Path $root ('.work/setup launcher space/'+[Guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Force -Path $fixture | Out-Null
Copy-Item -LiteralPath (Join-Path $root 'kit/Setup.cmd') -Destination $fixture
$code=@'
using System; using System.IO;
public class SetupLauncherProbe {
 public static int Main(string[] args) {
  string dir=Path.GetDirectoryName(System.Reflection.Assembly.GetExecutingAssembly().Location);
  string joined=string.Join(" ",args); bool setup=joined.Contains("Setup.ps1");
  File.AppendAllText(Path.Combine(dir,"calls.txt"),(setup?"setup":"check")+Environment.NewLine);
  if(setup && (!joined.Contains("-InstallMissing") || !joined.Contains("-PromptMachineEnvironment") || !joined.Contains("-PromptNode"))) return 9;
  if(!setup && !joined.Contains("-Profile environment")) return 9;
  Console.WriteLine("synthetic "+(setup?"setup":"check"));
  return int.Parse(File.ReadAllText(Path.Combine(dir,setup?"setup-exit.txt":"check-exit.txt")));
 }
}
'@
Add-Type -TypeDefinition $code -OutputAssembly (Join-Path $fixture 'powershell.exe') -OutputType ConsoleApplication
$cases=@()
foreach($spec in @(
    @{name='agent-success';args='--no-pause';setup=0;check=0;expected=0;calls="setup`r`ncheck`r`n";pause=$false},
    @{name='agent-setup-failure';args='--no-pause';setup=1;check=0;expected=1;calls="setup`r`n";pause=$false},
    @{name='agent-check-failure';args='--no-pause';setup=0;check=1;expected=1;calls="setup`r`ncheck`r`n";pause=$false},
    @{name='manual-success';args='';setup=0;check=0;expected=0;calls="setup`r`ncheck`r`n";pause=$true},
    @{name='manual-failure';args='';setup=1;check=0;expected=1;calls="setup`r`n";pause=$true}
)){
    [IO.File]::WriteAllText((Join-Path $fixture 'setup-exit.txt'),[string]$spec.setup)
    [IO.File]::WriteAllText((Join-Path $fixture 'check-exit.txt'),[string]$spec.check)
    [IO.File]::WriteAllText((Join-Path $fixture 'calls.txt'),'')
    $start=New-Object Diagnostics.ProcessStartInfo
    $start.FileName=$env:ComSpec;$start.Arguments='/d /c ""'+(Join-Path $fixture 'Setup.cmd')+'" '+$spec.args+'"'
    $start.WorkingDirectory=$fixture;$start.EnvironmentVariables['PATH']=$fixture+';'+(Join-Path $env:SystemRoot 'System32')
    $start.UseShellExecute=$false;$start.CreateNoWindow=$true
    $start.RedirectStandardOutput=$true;$start.RedirectStandardError=$true;$start.RedirectStandardInput=$true
    $p=[Diagnostics.Process]::Start($start)
    try{
        $out=$p.StandardOutput.ReadToEndAsync();$err=$p.StandardError.ReadToEndAsync()
        $p.StandardInput.WriteLine('');$p.StandardInput.Close()
        if(-not $p.WaitForExit(10000)){$p.Kill();throw 'Launcher did not finish'}
        $text=$out.GetAwaiter().GetResult()+$err.GetAwaiter().GetResult()
        [IO.File]::WriteAllText((Join-Path $fixture ($spec.name+'.log')),$text)
        if($p.ExitCode -ne $spec.expected -or [IO.File]::ReadAllText((Join-Path $fixture 'calls.txt')) -ne $spec.calls){throw ('Wrong launcher outcome: '+$spec.name+' '+$text)}
        if(($text -match 'Press any key') -ne $spec.pause){throw ('Wrong final pause: '+$spec.name)}
        $cases+=$spec.name
    }finally{$p.Dispose()}
}
@{status='PASS';fixture=$fixture;cases=$cases;limits='Real CMD, English pause output, fake PowerShell; no actual UAC/settings mutation'} | ConvertTo-Json -Compress
