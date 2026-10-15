# Copy source/config only; destination must be new. Does not modify the source kit.
[CmdletBinding()]
param([Parameter(Mandatory)][string]$Destination)
$ErrorActionPreference = 'Stop'
[Console]::OutputEncoding = New-Object Text.UTF8Encoding($false)
$OutputEncoding = [Console]::OutputEncoding
$root = Split-Path -Parent $PSScriptRoot
function Copy-SourceEntry {
    param([string]$Source, [string]$Target)
    $item = Get-Item -LiteralPath $Source -Force
    if ($item.Name -in @('build','run','runs','reports','.gradle','node_modules','.git','logs','.kit','releases')) { return }
    if ($item.Attributes -band [IO.FileAttributes]::ReparsePoint) { throw "Source contains a junction/symlink: $Source. Copy an ordinary source checkout." }
    if ($item.PSIsContainer) {
        New-Item -ItemType Directory -Path $Target | Out-Null
        foreach ($child in (Get-ChildItem -LiteralPath $Source -Force)) { Copy-SourceEntry $child.FullName (Join-Path $Target $child.Name) }
    } else { Copy-Item -LiteralPath $Source -Destination $Target }
}
try {
    $target = [IO.Path]::GetFullPath($Destination)
    $sourceRoot=[IO.Path]::GetFullPath($root).TrimEnd('\','/')
    if($target.TrimEnd('\','/') -eq $sourceRoot -or $target.StartsWith($sourceRoot+[IO.Path]::DirectorySeparatorChar,[StringComparison]::OrdinalIgnoreCase)){throw 'Destination must be outside the source kit.'}
    if (Test-Path -LiteralPath $target) { throw 'Destination already exists; choose a new directory.' }
    $ancestor=Split-Path -Parent $target
    while($ancestor){
        if(Test-Path -LiteralPath $ancestor){$entry=Get-Item -LiteralPath $ancestor -Force;if($entry.Attributes -band [IO.FileAttributes]::ReparsePoint){throw 'Destination ancestors must not be junctions/symlinks.'}}
        $parent=Split-Path -Parent $ancestor;if($parent -eq $ancestor){break};$ancestor=$parent
    }
    $required=@('AGENTS.md','agent.ps1','build.gradle','settings.gradle','gradlew','gradlew.bat','README.md','START.md','Setup.cmd','THIRD-PARTY-NOTICES.md','licenses','kit.json','TASK.md','.gitignore','gradle','src','testing','tools','.agents','docs','mcp')
    foreach($name in $required){if(-not (Test-Path -LiteralPath (Join-Path $root $name))){throw ('Required source entry missing: '+$name)}}
    New-Item -ItemType Directory -Path $target | Out-Null
    foreach ($name in @('AGENTS.md','agent.ps1','build.gradle','settings.gradle','gradlew','gradlew.bat','README.md','START.md','Setup.cmd','THIRD-PARTY-NOTICES.md','licenses','kit.json','TASK.md','.gitignore','gradle','src','testing','tools','.agents','docs')) {
        Copy-SourceEntry (Join-Path $root $name) (Join-Path $target $name)
    }
    foreach ($optional in @('CLAUDE.md','integrations')) {
        if(Test-Path -LiteralPath (Join-Path $root $optional)){Copy-SourceEntry (Join-Path $root $optional) (Join-Path $target $optional)}
    }
    New-Item -ItemType Directory -Path (Join-Path $target 'mcp') | Out-Null
    Get-ChildItem -LiteralPath (Join-Path $root 'mcp') -File | Copy-Item -Destination (Join-Path $target 'mcp')
    $name = [IO.Path]::GetFileName($target).Replace('\','\\').Replace("'", "\'")
    [IO.File]::WriteAllText((Join-Path $target 'settings.gradle'), "rootProject.name = '$name'`n")
    [IO.File]::WriteAllText((Join-Path $target 'TASK.md'), "# Active task`nObjective: Fresh source-only checkout; set feature acceptance before implementation.`nCurrent step: Run tools/Setup.ps1 then agent status/environment from this directory.`nLatest validation: None; copied evidence is not current.`nRemaining: Setup and feature-specific validation.`nBlockers: None known.`n")
    [ordered]@{ status='PASS'; projectRoot=$target; nextAction='Run tools/Setup.ps1 in the destination; open/trust it and verify MCP projectRoot.' } | ConvertTo-Json -Compress
    exit 0
} catch {
    [ordered]@{ status='FAIL'; message=$_.Exception.Message } | ConvertTo-Json -Compress
    exit 1
}
