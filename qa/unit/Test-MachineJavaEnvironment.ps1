# Exercise the real prompt/settings functions with in-memory registry substitutes only.
[CmdletBinding()]
param()
$ErrorActionPreference='Stop'
$root=Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
. (Join-Path $root 'kit/tools/JavaEnvironment.ps1')
$kitJavaWorkspace=Join-Path $root ('.work/machine-java/'+[Guid]::NewGuid().ToString('N'))
$selection=[pscustomobject]@{gradle=[pscustomobject]@{major=25;home='C:\JDK space\25'};java8=[pscustomobject]@{major=8;home='C:\JDK space\8'}}
$script:admin=$true
$script:writes=0
function Test-KitAdministrator {$script:admin}
function Read-KitEnvironmentAnswer {if($script:answers.Count -eq 0){return $null};$script:answers.Dequeue()}
function Read-KitPersistentEnvironment {param($Name,$Scope);$script:values[($Scope+':'+$Name)]}
function Write-KitPersistentEnvironment {
    param($Name,$Value,$Scope)
    $script:writes++
    if($script:failOnce -and $Name -eq 'Path'){$script:failOnce=$false;throw 'synthetic write failure'}
    $script:values[($Scope+':'+$Name)]=$Value
}
function Reset-Fixture {
    $script:values=@{'Machine:JAVA_HOME'='C:\old';'Machine:Path'='C:\Windows;C:\JDK space\8\bin;%SystemRoot%\System32;C:\tools;C:\JDK space\25\bin';'User:JAVA_HOME'='C:\user-old'}
    $script:answers=New-Object 'System.Collections.Generic.Queue[string]'
    $script:writes=0;$script:failOnce=$false;$script:admin=$true
}
Reset-Fixture
$script:answers.Enqueue('n')
$result=Set-KitMachineEnvironment $selection -Decision (Request-KitMachineEnvironmentChoice)
if($result.changed -or $script:writes -ne 0 -or (Test-Path $kitJavaWorkspace)){throw 'N changed settings or wrote a backup'}
Reset-Fixture
$script:answers.Enqueue('maybe');$script:answers.Enqueue(' Y ')
$result=Set-KitMachineEnvironment $selection -Decision (Request-KitMachineEnvironmentChoice)
$expected='C:\JDK space\25\bin;C:\JDK space\8\bin;C:\Windows;%SystemRoot%\System32;C:\tools'
if(-not $result.changed -or $script:values['Machine:JAVA_HOME'] -ne $selection.gradle.home -or $script:values['User:JAVA_HOME'] -ne $selection.gradle.home -or $script:values['Machine:Path'] -ne $expected){throw 'Y failed selection/order/preservation/user override'}
$backup=Get-Content -LiteralPath $result.backup -Raw | ConvertFrom-Json
if($backup.machineJavaHome -ne 'C:\old' -or $backup.userJavaHome -ne 'C:\user-old'){throw 'Previous values not backed up'}
$script:answers.Enqueue('y');$result=Set-KitMachineEnvironment $selection -Decision (Request-KitMachineEnvironmentChoice)
if($script:values['Machine:Path'] -ne $expected){throw 'Repeat duplicated PATH'}
Reset-Fixture
$script:values['User:JAVA_HOME']=$null;$script:answers.Enqueue('y')
$result=Set-KitMachineEnvironment $selection -Decision (Request-KitMachineEnvironmentChoice)
if($script:values['User:JAVA_HOME'] -or $script:writes -ne 2){throw 'Created an unnecessary user override'}
Reset-Fixture
$script:answers.Enqueue('y');$script:failOnce=$true
try{Set-KitMachineEnvironment $selection -Decision (Request-KitMachineEnvironmentChoice);throw 'Failed write passed'}catch{if($_.Exception.Message -notmatch 'failed and were restored'){throw}}
if($script:values['Machine:JAVA_HOME'] -ne 'C:\old' -or $script:values['Machine:Path'] -notmatch '^C:\\Windows;' -or $script:values['User:JAVA_HOME'] -ne 'C:\user-old'){throw 'Rollback failed'}
Reset-Fixture
$script:admin=$false;$script:answers.Enqueue('y')
try{Set-KitMachineEnvironment $selection -Decision (Request-KitMachineEnvironmentChoice);throw 'Nonadmin passed'}catch{if($_.Exception.Message -notmatch 'Administrator rights'){throw}}
if($script:writes){throw 'Nonadmin wrote settings'}
Reset-Fixture
try{Set-KitMachineEnvironment $selection -Decision (Request-KitMachineEnvironmentChoice);throw 'EOF passed'}catch{if($_.Exception.Message -notmatch 'No Y/N response'){throw}}
if($script:writes){throw 'EOF wrote settings'}
@{status='PASS';cases=@('N zero writes','invalid response then Y','25 home and ordered deduplicated PATH','unrelated PATH preserved','existing user override aligned','backup prior values','idempotent repeat','no new user override','write failure rollback','nonadmin rejected','EOF no writes');limits='In-memory registry substitutes; no host JAVA_HOME/PATH mutation or actual elevated launcher claim'} | ConvertTo-Json -Compress
