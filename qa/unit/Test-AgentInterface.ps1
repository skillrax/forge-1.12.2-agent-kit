# agent.ps1 contract without Minecraft: status, init, generators, version, compact check output, hints, export gate.
[CmdletBinding()]
param()
$ErrorActionPreference = 'Stop'
[Console]::OutputEncoding = New-Object Text.UTF8Encoding($false)
$authoringRoot = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$kit = Join-Path $authoringRoot 'kit'
$fixture = Join-Path $authoringRoot ('.work/agent-fixtures/' + [Guid]::NewGuid().ToString('N'))
$shell = (Get-Process -Id $PID).Path
$passed = New-Object 'System.Collections.Generic.List[string]'

function Invoke-Agent([string[]]$Arguments, [int]$ExpectedExit = 0) {
    $ErrorActionPreference = 'Continue'
    $output = & $shell -NoProfile -File (Join-Path $fixture 'agent.ps1') @Arguments 2>$null
    $ErrorActionPreference = 'Stop'
    if ($LASTEXITCODE -ne $ExpectedExit) { throw "agent.ps1 $($Arguments -join ' '): exit $LASTEXITCODE, expected $ExpectedExit`n$($output -join "`n")" }
    return ($output -join "`n")
}
function Assert([bool]$Condition, [string]$Message) { if (-not $Condition) { throw $Message } }
function Read-Text([string]$Relative) { [IO.File]::ReadAllText((Join-Path $fixture $Relative)) }

try {
    New-Item -ItemType Directory -Force -Path (Join-Path $fixture 'testing'), (Join-Path $fixture 'tools'), (Join-Path $fixture 'src/main') | Out-Null
    foreach ($file in @('agent.ps1', 'build.gradle', 'TASK.md')) { Copy-Item -LiteralPath (Join-Path $kit $file) -Destination $fixture }
    foreach ($file in @('Diagnostics.ps1', 'Test-DevEnvironment.ps1')) { Copy-Item -LiteralPath (Join-Path $kit "testing/$file") -Destination (Join-Path $fixture 'testing') }
    foreach ($file in @('Initialize-Mod.ps1', 'New-Content.ps1', 'Export-Release.ps1')) { Copy-Item -LiteralPath (Join-Path $kit "tools/$file") -Destination (Join-Path $fixture 'tools') }
    # Fake Gradle: FAKE_MODE=compile-error prints javac output (with the duplicate copy Gradle produces).
    Set-Content -LiteralPath (Join-Path $fixture 'gradlew.bat') -Encoding ASCII -Value "@echo off`r`npowershell -NoProfile -File `"%~dp0fake.ps1`" %*`r`nexit /b %errorlevel%"
    Set-Content -LiteralPath (Join-Path $fixture 'fake.ps1') -Encoding UTF8 -Value @'
$root = Split-Path -Parent $MyInvocation.MyCommand.Path
if ($env:FAKE_MODE -eq 'compile-error') {
    $file = Join-Path $root 'src\main\java\com\example\demo\DemoMod.java'
    foreach ($copy in 1..2) {
        "${file}:8: error: cannot find symbol"
        '    static Item X = new Item().setUnlocalizedName("x");'
        '                              ^'
        '  symbol:   method setUnlocalizedName(String)'
        '  location: class Item'
        '  extract[exceptions]'
    }
    '1 error'
    '> Task :compileJava FAILED'
    exit 1
}
"MOD_VERSION: 1.2.3"
exit 0
'@

    $status = Invoke-Agent @('status')
    Assert ($status -match 'mod: none' -and $status -match 'next: .*init') "fresh status: $status"
    $passed.Add('fresh status')

    $init = Invoke-Agent @('init', 'demo', 'Demo Mod')
    Assert ($init -match 'created demo 1.0.0') "init: $init"
    Assert ((Read-Text 'build.gradle') -match "version = '1.0.0'" -and (Read-Text 'build.gradle') -match "archivesName = 'demo'") 'init build.gradle'
    Assert ((Invoke-Agent @('status')) -match 'mod: demo 1.0.0') 'status after init'
    Invoke-Agent @('init', 'other') 1 | Out-Null
    $passed.Add('init and refusal on existing mod')

    $java = 'src/main/java/com/example/demo'
    $assets = 'src/main/resources/assets/demo'
    Invoke-Agent @('new', 'item', 'gem', '-StackSize', '16') | Out-Null
    Invoke-Agent @('new', 'block', 'gem_block', '-Material', 'IRON', '-Hardness', '5', '-Level', '2') | Out-Null
    Invoke-Agent @('new', 'ore', 'gem_ore', '-Drops', 'gem', '-Count', '1-3', '-Height', '5-30') | Out-Null
    Invoke-Agent @('new', 'food', 'gem_apple', '-Hunger', '6', '-Effect', 'speed:200:1') | Out-Null
    Invoke-Agent @('new', 'tools', 'gem', '-Repair', 'gem') | Out-Null
    Invoke-Agent @('new', 'armor', 'gem', '-Protection', '3,8,6,3') | Out-Null
    Invoke-Agent @('new', 'recipe', 'gem_block', '-Shape', '###,###,###', '-Key', '#=gem', '-Result', 'gem_block') | Out-Null
    Invoke-Agent @('new', 'smelting', 'gem', '-From', 'gem_ore', '-Result', 'gem') | Out-Null
    Invoke-Agent @('new', 'tile', 'gem_box', '-Ticking', '-Inventory', '9') | Out-Null
    Invoke-Agent @('new', 'test', 'gem-ore-drops') | Out-Null
    $items = Read-Text "$java/ModItems.java"; $blocks = Read-Text "$java/ModBlocks.java"; $main = Read-Text "$java/DemoMod.java"
    Assert ($items -match 'GEM = item\("gem", new Item\(\)\)\.setCreativeTab\(CreativeTabs\.MATERIALS\)\.setMaxStackSize\(16\);') 'item declaration'
    Assert ($items -match 'new ItemFood\(6, 0\.3F, false\)\)\.setPotionEffect\(new PotionEffect\(MobEffects\.SPEED, 200, 1\), 1\.0F\)') 'food declaration'
    Assert ($items -match 'new int\[\] \{3, 6, 8, 3\}') 'armor order boots,legs,chest,helmet'
    Assert ($blocks -match 'new OreBlock\(3\.0F, 1, \(\) -> ModItems\.GEM, 1, 3, 3, 7\)') "ore declaration: $blocks"
    Assert ($blocks -match 'GameRegistry\.registerTileEntity\(GemBoxTile\.class') 'tile registration'
    Assert ($main -match 'registerWorldGenerator' -and $main -match 'GameRegistry\.addSmelting\(new ItemStack\(Item\.getItemFromBlock\(ModBlocks\.GEM_ORE\)\)' -and $main -match 'GEM_TOOLS\.setRepairItem') 'main class insertions'
    Assert ((Read-Text "$java/ModWorldGen.java") -match 'ore\(ModBlocks\.GEM_ORE, 0, 8, 8, 5, 30\);') 'worldgen entry'
    Assert ((Read-Text "$assets/recipes/gem_block.json") -match '"#": \{ "item": "demo:gem" \}') 'recipe json'
    $lang = Read-Text "$assets/lang/en_us.lang"
    foreach ($key in @('item.demo.gem.name=Gem', 'tile.demo.gem_ore.name=Gem Ore', 'item.demo.gem_pickaxe.name=Gem Pickaxe', 'item.demo.gem_chestplate.name=Gem Chestplate', 'tile.demo.gem_box.name=Gem Box')) { Assert ($lang.Contains($key)) "lang $key" }
    foreach ($file in @("$assets/textures/items/gem.png", "$assets/textures/blocks/gem_ore.png", "$assets/textures/models/armor/gem_layer_1.png", "$assets/blockstates/gem_block.json", 'src/gametest/java/com/example/demo/GemOreDropsTest.java')) { Assert (Test-Path -LiteralPath (Join-Path $fixture $file)) "missing $file" }
    $dup = Invoke-Agent @('new', 'item', 'gem') 1
    Assert ($dup -match 'already declared') 'duplicate rejected'
    $passed.Add('generators: item block ore food tools armor recipe smelting tile test, duplicate refusal')

    $version = Invoke-Agent @('version', '1.2.3')
    Assert ((Read-Text 'build.gradle') -match "version = '1.2.3'" -and (Read-Text "$java/DemoMod.java") -match 'VERSION = "1.2.3"' -and (Read-Text 'src/main/resources/mcmod.info') -match '"version": "1.2.3"') "version: $version"
    $passed.Add('version sync')

    $env:FAKE_MODE = 'ok'
    $check = Invoke-Agent @('check')
    Assert ($check -match '^PASS build') "check: $check"
    $json = (Invoke-Agent @('check', '-Json')) | ConvertFrom-Json
    Assert ($json.status -eq 'PASS' -and $json.runId -and $json.projectRoot -eq $fixture) 'json check'
    $passed.Add('compact and JSON check output')

    $env:FAKE_MODE = 'compile-error'
    $failure = Invoke-Agent @('check') 1
    $lines = $failure -split "`n"
    Assert ($lines[0] -match '^FAIL build') "first line: $($lines[0])"
    Assert (@($lines | Where-Object { $_ -match 'DemoMod\.java:8: cannot find symbol' }).Count -eq 1) "deduplicated error: $failure"
    Assert ($failure -match 'hint: 1\.12\.2 uses setTranslationKey') 'rename hint'
    Assert ($failure -match 'static Item X = new Item' -and $failure -notmatch 'extract\[exceptions\]') 'source line kept, noise dropped'
    Assert ($failure -match 'src/main/java/com/example/demo/DemoMod\.java') 'relative path'
    Assert ((Invoke-Agent @('errors') 0) -match 'hint: 1\.12\.2 uses setTranslationKey') 'errors reprint'
    Assert ((Invoke-Agent @('status')) -match 'next: Fix the last failure') 'status after failure'
    $passed.Add('compile diagnostics, hints, errors, status')

    $export = Invoke-Agent @('export') 1
    Assert ($export -match 'check server') "export gate: $export"
    $passed.Add('export requires server check')

    "PASS: $($passed.Count) agent interface groups ($($passed -join '; '))"
    exit 0
} catch {
    "FAIL: $($_.Exception.Message)`npassed: $($passed -join '; ')`nfixture: $fixture"
    exit 1
} finally { Remove-Item Env:FAKE_MODE -ErrorAction SilentlyContinue }
