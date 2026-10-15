# Initialize only an empty foundation; never overwrite an existing mod.
[CmdletBinding()]
param(
    [Parameter(Mandatory)][ValidatePattern('^[a-z][a-z0-9_]{0,63}$')][string]$ModId,
    [string]$Name,
    [ValidatePattern('^[A-Za-z_][A-Za-z0-9_]*(\.[A-Za-z_][A-Za-z0-9_]*)+$')][string]$Package,
    [ValidatePattern('^\d+\.\d+\.\d+(-[A-Za-z0-9.-]+)?$')][string]$Version='1.0.0'
)
$ErrorActionPreference='Stop'
[Console]::OutputEncoding=New-Object Text.UTF8Encoding($false)
$root=Split-Path -Parent $PSScriptRoot
try{
    if(-not $Name){$Name=$ModId}
    if($Name -match '[\x00-\x1F]'){throw 'Display name must be a single printable line.'}
    if(-not $Package){$Package='com.example.'+$ModId}
    $reserved='abstract assert boolean break byte case catch char class const continue default do double else enum extends final finally float for goto if implements import instanceof int interface long native new package private protected public return short static strictfp super switch synchronized this throw throws transient try void volatile while true false null'.Split(' ')
    if(@($Package.Split('.') | Where-Object {$_ -in $reserved}).Count){throw 'Package contains a reserved Java word.'}
    $main=Join-Path $root 'src/main'
    $existing=@(Get-ChildItem -LiteralPath $main -Recurse -File -Force -ErrorAction SilentlyContinue | Where-Object {$_.Name -ne '.gitkeep'})
    if($existing.Count){throw 'src/main already contains content. Adapt the existing mod instead of initializing again.'}
    $buildPath=Join-Path $root 'build.gradle'
    $build=[IO.File]::ReadAllText($buildPath)
    if([regex]::Matches($build,"(?m)^version = '[^']*'\r?$").Count -ne 1 -or [regex]::Matches($build,"(?m)^group = '[^']*'\r?$").Count -ne 1 -or $build -match 'archivesName'){throw 'Build differs from the empty kit foundation. Configure the existing build explicitly.'}
    $build=[regex]::Replace($build,"(?m)^version = '[^']*'\r?$",("version = '"+$Version+"'"))
    $build=[regex]::Replace($build,"(?m)^group = '[^']*'\r?$",("group = '"+$Package+"'"))
    $build+="`nbase { archivesName = '"+$ModId+"' }`n"
    $javaDir=Join-Path $main ('java/'+$Package.Replace('.','/'))
    $resources=Join-Path $main 'resources'
    New-Item -ItemType Directory -Force -Path $javaDir,$resources | Out-Null
    $javaName=$Name | ConvertTo-Json -Compress
    $java="package $Package;`n`nimport net.minecraftforge.fml.common.Mod;`n`n@Mod(modid = `"$ModId`", name = $javaName, version = `"$Version`")`npublic class MainMod {`n}`n"
    $metadata=ConvertTo-Json -InputObject @([ordered]@{modid=$ModId;name=$Name;version=$Version;mcversion='1.12.2';description='';authorList=@()}) -Depth 4
    $utf8=New-Object Text.UTF8Encoding($false)
    [IO.File]::WriteAllText((Join-Path $javaDir 'MainMod.java'),$java,$utf8)
    [IO.File]::WriteAllText((Join-Path $resources 'mcmod.info'),$metadata,$utf8)
    [IO.File]::WriteAllText((Join-Path $resources 'pack.mcmeta'),'{"pack":{"pack_format":3,"description":"Mod resources"}}',$utf8)
    [IO.File]::WriteAllText($buildPath,$build,$utf8)
    [IO.File]::WriteAllText((Join-Path $root 'TASK.md'),"# Active task`nObjective: Implement the requested behavior for $ModId ($Version).`nResult: Empty Forge entry point and matching build/resource metadata initialized; no validation or gameplay claim.`nNext action: Record acceptance, read the relevant skill reference and implement the requested feature; run agent check -Profile mod.`nBlocker: Behavior/acceptance not yet recorded.`n",$utf8)
    [ordered]@{schemaVersion=1;status='PASS';projectRoot=$root;modId=$ModId;modVersion=$Version;package=$Package;entryPoint=(Join-Path $javaDir 'MainMod.java');nextAction='Record acceptance in TASK.md, implement the feature, then run agent.ps1 -Action check -Profile mod.'} | ConvertTo-Json -Compress
}catch{
    [ordered]@{schemaVersion=1;status='FAIL';message=$_.Exception.Message} | ConvertTo-Json -Compress
    exit 1
}
