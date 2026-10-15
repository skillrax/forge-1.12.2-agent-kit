# Create a project-owned scenario, never a production source file or a default passing test.
[CmdletBinding()]
param([Parameter(Mandatory)][ValidatePattern('^[a-z][a-z0-9-]{0,63}$')][string]$Name,[ValidatePattern('^[a-zA-Z_]\w*(\.[a-zA-Z_]\w*)+$')][string]$Package='example.installedtests')
$ErrorActionPreference='Stop'
$root=Split-Path -Parent $PSScriptRoot
. (Join-Path $root 'testing/InstalledRuntime.ps1')
try {
    if($Package -match '^(java|javax|dev\.agentic\.installedtest)(\.|$)'){throw 'Choose a project package outside reserved/core namespaces.'}
    $class=(($Name -split '-') | ForEach-Object {if($_){$_.Substring(0,1).ToUpperInvariant()+$_.Substring(1)}}) -join ''
    $directory=Join-Path $root ('src/installedTest/java/'+$Package.Replace('.','/'))
    $path=Join-Path $directory ($class+'.java')
    if(Test-Path -LiteralPath $path){throw 'Scenario source already exists; edit it instead.'}
    New-Item -ItemType Directory -Path $directory -Force | Out-Null
    $source=@"
package $Package;

import dev.agentic.installedtest.*;

@InstalledTest("$Name")
public final class $class implements InstalledScenario {
    @Override public void plan(TestContext t) {
        t.then("arrange", () -> {
            // Place this feature's inputs using t.world(), t.place(), t.item(), etc.
            throw new UnsupportedOperationException("Define feature setup and an observable outcome");
        });
        t.waitUntil("expected-outcome", 100,
            () -> false, // Replace with the actual observable result.
            () -> "Describe the expected result and current state");
    }
}
"@
    [IO.File]::WriteAllText($path,$source,(New-Object Text.UTF8Encoding($false)))
    @{schemaVersion=1;status='PASS';scenario=$Name;path=$path;nextAction='Define real behavior checks, build the separate driver, and select this scenario in an installed-test manifest.'} | ConvertTo-Json -Compress
    exit 0
}catch{@{schemaVersion=1;status='FAIL';message=$_.Exception.Message} | ConvertTo-Json -Compress;exit 1}
