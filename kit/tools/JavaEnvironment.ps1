# Process-local Java discovery shared by setup and the shell agent. No installation on import.
$kitJavaWorkspace=Split-Path -Parent $PSScriptRoot
function Get-KitJavaCandidates {
    $paths=@($env:JAVA_HOME)
    $saved=Join-Path $kitJavaWorkspace 'reports/setup/java-homes.json'
    if([IO.File]::Exists($saved)){
        $homes=Get-Content -Encoding UTF8 -LiteralPath $saved -Raw | ConvertFrom-Json
        $paths=@($homes.gradle,$homes.java8)+$paths
    }
    $java=Get-Command java.exe -ErrorAction SilentlyContinue | Select-Object -First 1
    if($java){$paths+=Split-Path -Parent (Split-Path -Parent $java.Source)}
    $programRoot=if($env:ProgramW6432){$env:ProgramW6432}else{$env:ProgramFiles}
    foreach($base in @((Join-Path $programRoot 'Eclipse Adoptium'),(Join-Path $env:USERPROFILE '.jdks'),(Join-Path $env:USERPROFILE '.gradle/jdks'))){
        if([IO.Directory]::Exists($base)){
            foreach($folder in (Get-ChildItem -LiteralPath $base -Directory | Sort-Object Name -Descending)){
                $paths+=$folder.FullName
                # Gradle-provisioned toolchains may have one package directory below the cache entry.
                if(-not [IO.File]::Exists((Join-Path $folder.FullName 'release'))){$paths+=@(Get-ChildItem -LiteralPath $folder.FullName -Directory | ForEach-Object {$_.FullName})}
            }
        }
    }
    @($paths | Where-Object {$_} | Select-Object -Unique)
}
# Why each candidate was rejected, for setup messages (home -> reason).
$script:KitJavaRejects=@{}
function Read-KitJava {
    param([string]$JdkHome)
    if(-not $JdkHome){return $null}
    if(-not [IO.File]::Exists((Join-Path $JdkHome 'bin/java.exe')) -or -not [IO.File]::Exists((Join-Path $JdkHome 'bin/javac.exe'))){$script:KitJavaRejects[$JdkHome]='bin\java.exe or bin\javac.exe missing';return $null}
    $start=New-Object Diagnostics.ProcessStartInfo
    $start.FileName=Join-Path $JdkHome 'bin/java.exe';$start.Arguments='-XshowSettings:properties -version'
    $start.UseShellExecute=$false;$start.CreateNoWindow=$true;$start.RedirectStandardOutput=$true;$start.RedirectStandardError=$true
    $p=[Diagnostics.Process]::Start($start)
    try{
        $out=$p.StandardOutput.ReadToEndAsync();$err=$p.StandardError.ReadToEndAsync()
        # The first start after an install or reboot can be slow while antivirus scans the JDK.
        if(-not $p.WaitForExit(60000)){$p.Kill();$script:KitJavaRejects[$JdkHome]='java -version did not finish within 60 s';return $null}
        $detail=$out.GetAwaiter().GetResult()+$err.GetAwaiter().GetResult()
        if($p.ExitCode -ne 0 -or $detail -notmatch 'java.specification.version\s*=\s*(?:1\.)?(\d+)'){$script:KitJavaRejects[$JdkHome]='java -version failed (exit '+$p.ExitCode+'): '+(($detail -split '\r?\n' | Where-Object {$_.Trim()} | Select-Object -First 2) -join ' ');return $null}
        $major=[int]$Matches[1]
        if($detail -notmatch 'sun.arch.data.model\s*=\s*64'){$script:KitJavaRejects[$JdkHome]='not a 64-bit JDK';return $null}
    }finally{$p.Dispose()}
    $start.FileName=Join-Path $JdkHome 'bin/javac.exe';$start.Arguments='-version'
    $p=[Diagnostics.Process]::Start($start)
    try{
        $out=$p.StandardOutput.ReadToEndAsync();$err=$p.StandardError.ReadToEndAsync()
        if(-not $p.WaitForExit(60000)){$p.Kill();$script:KitJavaRejects[$JdkHome]='javac -version did not finish within 60 s';return $null}
        $compiler=$out.GetAwaiter().GetResult()+$err.GetAwaiter().GetResult()
        if($p.ExitCode -ne 0 -or $compiler -notmatch 'javac\s+(?:1\.)?(\d+)' -or [int]$Matches[1] -ne $major){$script:KitJavaRejects[$JdkHome]='javac -version failed or reports another version: '+$compiler.Trim();return $null}
    }finally{$p.Dispose()}
    $versionLine=([regex]::Match($detail,'(?m)^(?:openjdk|java) version .*$')).Value.Trim()
    [pscustomobject]@{home=[IO.Path]::GetFullPath($JdkHome);major=$major;bits=64;java=$versionLine;javac=$compiler.Trim()}
}
function Get-KitJavaSelection {
    param([string]$GradleJavaHome,[string]$Java8Home)
    $candidates=@($GradleJavaHome,$Java8Home)+@(Get-KitJavaCandidates)
    $valid=@(foreach($path in ($candidates | Where-Object {$_} | Select-Object -Unique)){
        try{$jdk=Read-KitJava $path;if($jdk){$jdk}}catch{# An unrelated broken installation must not prevent other candidates.
        }
    })
    $modern=$null
    foreach($preferred in @($GradleJavaHome,$env:JAVA_HOME)){
        $modern=$valid | Where-Object {$_.home -eq $preferred -and $_.major -ge 17} | Select-Object -First 1
        if($modern){break}
    }
    if(-not $modern){$modern=$valid | Where-Object {$_.major -eq 25} | Select-Object -First 1}
    if(-not $modern){$modern=$valid | Where-Object {$_.major -ge 17} | Sort-Object major -Descending | Select-Object -First 1}
    $legacy=$valid | Where-Object {$_.major -eq 8} | Select-Object -First 1
    if($GradleJavaHome -and (-not $modern -or $modern.home -ne [IO.Path]::GetFullPath($GradleJavaHome))){throw 'Explicit GradleJavaHome is not a working 64-bit JDK17+.'}
    if($Java8Home){$legacy=$valid | Where-Object {$_.major -eq 8 -and $_.home -eq [IO.Path]::GetFullPath($Java8Home)} | Select-Object -First 1;if(-not $legacy){throw 'Explicit Java8Home is not a working 64-bit Java8 JDK.'}}
    [pscustomobject]@{gradle=$modern;java8=$legacy}
}
function Set-KitJavaProcess {
    param($Selection)
    if($Selection.gradle){$env:JAVA_HOME=$Selection.gradle.home}
    # Gradle reads this project property as an explicit toolchain search path, without rewriting PATH.
    if($Selection.java8){[Environment]::SetEnvironmentVariable('ORG_GRADLE_PROJECT_org.gradle.java.installations.paths',$Selection.java8.home,'Process')}
}
function Test-KitAdministrator {
    $identity=[Security.Principal.WindowsIdentity]::GetCurrent()
    try{(New-Object Security.Principal.WindowsPrincipal($identity)).IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)}finally{$identity.Dispose()}
}
# Rejected JDKs in the standard install folder that look like the requested major version.
function Get-KitJavaRejectSummary([int]$Major){
    $pattern=if($Major -eq 8){'jdk-?8|jdk1\.8'}else{'jdk-?'+$Major}
    @($script:KitJavaRejects.Keys | Where-Object {(Split-Path -Leaf $_) -match $pattern} | ForEach-Object {$_+': '+$script:KitJavaRejects[$_]}) -join '; '
}
function Get-KitJava25 {
    foreach($path in (Get-KitJavaCandidates)){
        try{$jdk=Read-KitJava $path;if($jdk -and $jdk.major -eq 25){return $jdk}}catch{}
    }
    return $null
}
function Read-KitPersistentEnvironment {
    param([string]$Name,[string]$Scope)
    if($Name -eq 'Path' -and $Scope -eq 'Machine'){
        $key=[Microsoft.Win32.Registry]::LocalMachine.OpenSubKey('SYSTEM\CurrentControlSet\Control\Session Manager\Environment')
        try{return $key.GetValue('Path',$null,[Microsoft.Win32.RegistryValueOptions]::DoNotExpandEnvironmentNames)}finally{$key.Dispose()}
    }
    [Environment]::GetEnvironmentVariable($Name,$Scope)
}
function Write-KitPersistentEnvironment {
    param([string]$Name,[AllowNull()][string]$Value,[string]$Scope)
    if($Name -eq 'Path' -and $Scope -eq 'Machine'){
        $key=[Microsoft.Win32.Registry]::LocalMachine.OpenSubKey('SYSTEM\CurrentControlSet\Control\Session Manager\Environment',$true)
        try{
            if($null -eq $Value -or $Value -eq ''){$key.DeleteValue('Path',$false)}else{$key.SetValue('Path',$Value,[Microsoft.Win32.RegistryValueKind]::ExpandString)}
        }finally{$key.Dispose()}
        return
    }
    [Environment]::SetEnvironmentVariable($Name,$Value,$Scope)
}
function Read-KitEnvironmentAnswer {
    [Console]::Error.Write('Set Windows JAVA_HOME to JDK25 and add JDK25/JDK8 to system PATH? [Y/N]: ')
    [Console]::ReadLine()
}
function Request-KitMachineEnvironmentChoice {
    [Console]::Error.WriteLine('Y changes settings for this PC. JDK25 will be the default java command; JDK8 remains available to the kit.')
    [Console]::Error.WriteLine('Choose now, before downloads. Y is applied only after JDK25 and JDK8 are verified; their actual paths will be displayed.')
    [Console]::Error.WriteLine('N leaves persistent JAVA_HOME and PATH unchanged; the kit can still use JDKs locally.')
    [Console]::Error.WriteLine('Y also updates an existing JAVA_HOME override for the current Windows user to JDK25.')
    [Console]::Error.WriteLine('Installer/UAC prompts may still need your response during downloads.')
    do{
        $answer=Read-KitEnvironmentAnswer
        if($null -eq $answer){throw 'No Y/N response received; Windows settings were not changed.'}
        $answer=$answer.Trim()
        if($answer -notmatch '^[yn]$'){[Console]::Error.WriteLine('Enter Y or N.')}
    }while($answer -notmatch '^[yn]$')
    return $answer.ToUpperInvariant()
}
function Set-KitMachineEnvironment {
    param($Selection,[Parameter(Mandatory)][ValidateSet('Y','N')][string]$Decision)
    if(-not $Selection.gradle -or $Selection.gradle.major -ne 25 -or -not $Selection.java8 -or $Selection.java8.major -ne 8){throw 'Windows Java settings require verified 64-bit JDK25 and JDK8.'}
    if($Decision -eq 'N'){return [pscustomobject]@{changed=$false;scope='Machine';decision=$Decision}}
    if(-not (Test-KitAdministrator)){throw 'Administrator rights are required to set Windows Java settings.'}
    [Console]::Error.WriteLine('Applying your earlier Y choice using verified JDK homes. JAVA_HOME: '+$Selection.gradle.home)
    $bins=@((Join-Path $Selection.gradle.home 'bin'),(Join-Path $Selection.java8.home 'bin'))
    [Console]::Error.WriteLine('System PATH starts with: '+($bins -join ';'))
    $oldPath=Read-KitPersistentEnvironment 'Path' 'Machine'
    $oldHome=Read-KitPersistentEnvironment 'JAVA_HOME' 'Machine'
    $userHome=Read-KitPersistentEnvironment 'JAVA_HOME' 'User'
    # Preserve unrelated entries and their order. Never write the combined process PATH to the registry.
    $rest=@($oldPath -split ';' | Where-Object {
        $entry=[Environment]::ExpandEnvironmentVariables($_.Trim().Trim('"')).TrimEnd('\')
        $bins -notcontains $entry
    })
    $newPath=(@($bins)+$rest) -join ';'
    $dir=Join-Path $kitJavaWorkspace 'reports/setup';New-Item -ItemType Directory -Force -Path $dir | Out-Null
    $backup=Join-Path $dir ('environment-before-'+[DateTime]::UtcNow.ToString('yyyyMMddTHHmmssfff')+'.json')
    [ordered]@{machineJavaHome=$oldHome;machinePath=$oldPath;userJavaHome=$userHome} | ConvertTo-Json | Set-Content -Encoding UTF8 -LiteralPath $backup
    try{
        # JAVA_HOME's .NET setter notifies Windows after the raw expandable PATH write.
        Write-KitPersistentEnvironment 'Path' $newPath 'Machine'
        Write-KitPersistentEnvironment 'JAVA_HOME' $Selection.gradle.home 'Machine'
        # An existing per-user JAVA_HOME overrides the system value for this user.
        if($userHome){Write-KitPersistentEnvironment 'JAVA_HOME' $Selection.gradle.home 'User'}
    }catch{
        $failure=$_.Exception.Message
        try{
            Write-KitPersistentEnvironment 'Path' $oldPath 'Machine'
            Write-KitPersistentEnvironment 'JAVA_HOME' $oldHome 'Machine'
            if($userHome){Write-KitPersistentEnvironment 'JAVA_HOME' $userHome 'User'}
        }catch{throw ('Could not finish or restore Windows Java settings. Backup: '+$backup+'. '+$failure+'; restore error: '+$_.Exception.Message)}
        throw ('Windows Java settings failed and were restored. Backup: '+$backup+'. '+$failure)
    }
    [Console]::Error.WriteLine('Windows Java settings saved. Restart terminals and your agent client to pick them up. Previous values: '+$backup)
    [pscustomobject]@{changed=$true;scope='Machine';decision=$Decision;javaHome=$Selection.gradle.home;pathEntries=$bins;existingUserHomeUpdated=[bool]$userHome;backup=$backup}
}
function Invoke-KitWinget {
    param([ValidateSet(8,25)][int]$Major,[string]$Log)
    $label='Temurin JDK'+$Major
    $winget=Get-Command winget.exe -ErrorAction SilentlyContinue | Select-Object -First 1
    if(-not $winget){throw ('WinGet is unavailable. Install/repair Microsoft App Installer, or install '+$label+' manually, then rerun setup.')}
    $start=New-Object Diagnostics.ProcessStartInfo
    $start.FileName=$winget.Source
    $start.Arguments='install --id EclipseAdoptium.Temurin.'+$Major+'.JDK --exact --source winget --architecture x64 --custom ADDLOCAL=FeatureMain'
    $start.UseShellExecute=$false;$start.CreateNoWindow=$true;$start.RedirectStandardOutput=$true;$start.RedirectStandardError=$true
    [Console]::Error.WriteLine('Installing missing '+$label+'; installer/UAC prompts may require your response. Log: '+$Log)
    $p=[Diagnostics.Process]::Start($start)
    $writer=New-Object IO.StreamWriter($Log,$false,(New-Object Text.UTF8Encoding($false)))
    $installWatch=[Diagnostics.Stopwatch]::StartNew();$lastProgress=0
    try{
        $streams=@($p.StandardOutput,$p.StandardError);$reads=@($streams[0].ReadLineAsync(),$streams[1].ReadLineAsync());$ended=@($false,$false)
        while(-not ($p.HasExited -and $ended[0] -and $ended[1])){
            for($i=0;$i -lt 2;$i++){
                for($n=0;$n -lt 32 -and -not $ended[$i] -and $reads[$i].IsCompleted;$n++){
                    $line=$reads[$i].GetAwaiter().GetResult();if($null -eq $line){$ended[$i]=$true;continue}
                    $writer.WriteLine($line);$writer.Flush();[Console]::Error.WriteLine($line);$reads[$i]=$streams[$i].ReadLineAsync()
                }
            }
            if($installWatch.Elapsed.TotalSeconds -ge ($lastProgress+5)){
                [Console]::Error.WriteLine(('[{0}s] WinGet {1} still running; check installer/UAC prompts. Full log: {2}' -f [int]$installWatch.Elapsed.TotalSeconds,$label,$Log))
                $lastProgress=$installWatch.Elapsed.TotalSeconds
            }
            if($installWatch.Elapsed.TotalSeconds -ge 900){
                $p.Kill()
                throw ('WinGet exceeded 15 minutes; no automatic retry. Check installer state and '+$Log+' before rerunning setup.')
            }
            Start-Sleep -Milliseconds 50
        }
        # -1978335189 no applicable upgrade, -1978335135 already installed: the package is registered, so re-check it.
        if($p.ExitCode -in @(-1978335189,-1978335135)){[Console]::Error.WriteLine('WinGet reports '+$label+' is already installed; checking it again.');return 'already-installed'}
        if($p.ExitCode -ne 0){throw ('WinGet '+$label+' installation returned '+$p.ExitCode+'. Inspect '+$Log+'; resolve installer/reboot/UAC requirements before retrying.')}
        return 'installed'
    }finally{$writer.Dispose();$p.Dispose()}
}
