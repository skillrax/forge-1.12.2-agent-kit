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
    # The API index drives compiler hints and the client-only check.
    # It is generated (agent.ps1 check environment in kit/); without it those groups are skipped and say so.
    $index = Join-Path $kit '.kit/api-index.tsv'
    $hasIndex = [IO.File]::Exists($index)
    if ($hasIndex) { New-Item -ItemType Directory -Force -Path (Join-Path $fixture '.kit') | Out-Null; Copy-Item -LiteralPath $index -Destination (Join-Path $fixture '.kit') }
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
if ($env:FAKE_MODE -eq 'block-item') {
    $file = Join-Path $root 'src\main\java\com\example\demo\DemoMod.java'
    "${file}:9: error: cannot find symbol"
    '    static Item Y = Items.STONEBRICK;'
    '                         ^'
    '  symbol:   variable STONEBRICK'
    '  location: class Items'
    '1 error'
    exit 1
}
if ($env:FAKE_MODE -eq 'test-fail') {
    # A game run whose test b fails at the step named in FAKE_PHASE.
    $out = ($args | Where-Object { $_ -like '-PkitCheckOut=*' } | Select-Object -First 1) -replace '^-PkitCheckOut=', ''
    'Done (1.0s)! For help, type "help"'
    $status = if ($env:FAKE_PHASE -eq 'pass') { 'PASS' } else { 'FAIL' }
    $report = @{ registered = @{ items = 1 }; lint = @(); tests = @(@{ id = 'b'; status = $status; phase = $env:FAKE_PHASE; message = 'm' }) }
    New-Item -ItemType Directory -Force -Path (Split-Path -Parent $out) | Out-Null
    [IO.File]::WriteAllText($out, ($report | ConvertTo-Json -Depth 5))
    'KIT_HARNESS_DONE'
    exit 0
}
if ($env:FAKE_MODE -eq 'network-once' -and -not (Test-Path (Join-Path $root 'network-failed-once'))) {
    New-Item -ItemType File (Join-Path $root 'network-failed-once') | Out-Null
    'Exception in thread "main" java.net.http.HttpTimeoutException: request timed out'
    exit 1
}
"MOD_VERSION: 1.2.3"
exit 0
'@

    $status = Invoke-Agent @('status')
    Assert ($status -match 'mod: none' -and $status -match 'next: .*init') "fresh status: $status"
    $passed.Add('fresh status')

    $task = "# Task`nGoal: a demo item`n"
    [IO.File]::WriteAllText((Join-Path $fixture 'TASK.md'), $task)
    $init = Invoke-Agent @('init', 'demo', 'Demo Mod')
    Assert ($init -match 'created demo 1.0.0' -and $init -match 'kept your TASK.md') "init: $init"
    Assert ((Read-Text 'TASK.md') -eq $task) 'init keeps a TASK.md the agent wrote'
    Assert ((Read-Text 'build.gradle') -match "version = '1.0.0'" -and (Read-Text 'build.gradle') -match "archivesName = 'demo'") 'init build.gradle'
    Assert ((Invoke-Agent @('status')) -match 'mod: demo 1.0.0') 'status after init'
    Invoke-Agent @('init', 'other') 1 | Out-Null
    # The shipped placeholder is replaced, and status says so until the request is written down.
    Copy-Item -LiteralPath (Join-Path $kit 'TASK.md') -Destination $fixture -Force
    Assert ((Invoke-Agent @('status')) -match 'task: TASK.md is still the placeholder') 'status placeholder reminder'
    [IO.File]::WriteAllText((Join-Path $fixture 'TASK.md'), $task)
    $passed.Add('init keeps TASK.md, placeholder reminder, refusal on existing mod')

    $java = 'src/main/java/com/example/demo'
    $assets = 'src/main/resources/assets/demo'
    Invoke-Agent @('new', 'item', 'gem', '-StackSize', '16') | Out-Null
    Invoke-Agent @('new', 'block', 'gem_block', '-Material', 'IRON', '-Hardness', '5', '-Level', '2') | Out-Null
    Invoke-Agent @('new', 'ore', 'gem_ore', '-Drops', 'gem', '-Count', '1-3', '-Height', '5-30') | Out-Null
    Invoke-Agent @('new', 'food', 'gem_apple', '-Hunger', '6', '-Effect', 'speed:200:1') | Out-Null
    Invoke-Agent @('new', 'tools', 'gem', '-Only', 'all', '-Repair', 'gem') | Out-Null
    Invoke-Agent @('new', 'armor', 'gem', '-Only', 'all', '-Protection', '3,8,6,3') | Out-Null
    Invoke-Agent @('new', 'recipe', 'gem_block', '-Shape', '###,###,###', '-Key', '#=gem', '-Result', 'gem_block') | Out-Null
    Invoke-Agent @('new', 'smelting', 'gem', '-From', 'gem_ore', '-Result', 'gem') | Out-Null
    Invoke-Agent @('new', 'tile', 'gem_box', '-Ticking', '-Inventory', '9') | Out-Null
    $mob = Invoke-Agent @('new', 'mob', 'gem_wisp', '-Like', 'blaze', '-Health', '30', '-Drops', 'gem', '-Count', '0-2', '-BiomeTypes', 'nether')
    Assert ($mob -match 'stats: blaze base, health 30\.0 = 15\.0 hearts' -and $mob -match 'spawns MONSTER weight 10 group 1-3 in type:NETHER') "mob stats line: $mob"
    Assert ((Read-Text "$java/GemWisp.java") -match 'extends EntityBlaze' -and (Read-Text "$java/ModEntities.java") -match 'biomes\("type:NETHER"\)' -and (Read-Text "$java/ClientEvents.java") -match 'GemWisp\.class, RenderBlaze::new') 'mob files'
    Assert ((Invoke-Agent @('new', 'mob', 'gem_cow', '-Like', 'cow', '-Attack', '3') 1) -match '-Attack does not apply to a cow') 'mob attack on animal rejected'
    Assert ((Invoke-Agent @('new', 'mob', 'gem_cow', '-Like', 'zombie', '-BiomeTypes', 'netherr') 1) -match "Unknown -BiomeTypes 'NETHERR'") 'mob biome type typo rejected'
    Invoke-Agent @('new', 'test', 'gem-ore-drops') | Out-Null
    $items = Read-Text "$java/ModItems.java"; $blocks = Read-Text "$java/ModBlocks.java"; $main = Read-Text "$java/DemoMod.java"
    Assert ($items -match 'GEM = item\("gem", new Item\(\)\)\.setCreativeTab\(CreativeTabs\.MATERIALS\)\.setMaxStackSize\(16\);') 'item declaration'
    Assert ($items -match 'new ItemFood\(6, 0\.3F, false\)\)\.setPotionEffect\(new PotionEffect\(MobEffects\.SPEED, 200, 1\), 1\.0F\)') 'food declaration'
    Assert ($items -match 'new int\[\] \{3, 6, 8, 3\}') 'armor order boots,legs,chest,helmet'
    Assert ($blocks -match 'new OreBlock\(3\.0F, 1, \(\) -> ModItems\.GEM, 1, 3, 3, 7\)') "ore declaration: $blocks"
    Assert ($blocks -match 'GameRegistry\.registerTileEntity\(GemBoxTile\.class') 'tile registration'
    Invoke-Agent @('new', 'tile', 'gem_marker') | Out-Null
    $marker = Read-Text "$java/GemMarkerTile.java"
    Assert ($marker -match 'shouldRefresh' -and $marker -notmatch 'ticks' -and (Read-Text "$java/GemMarkerBlock.java") -notmatch 'sendStatusMessage') "plain tile: no sample counter, keeps itself on state change: $marker"
    Assert ((Read-Text "$java/GemBoxTile.java") -match 'public int ticks;.*kit sample') 'ticking tile keeps the sample counter'
    Assert ($main -match 'registerWorldGenerator' -and $main -match 'GameRegistry\.addSmelting\(new ItemStack\(Item\.getItemFromBlock\(ModBlocks\.GEM_ORE\)\)' -and $main -match 'GEM_TOOLS\.setRepairItem') 'main class insertions'
    Assert ((Read-Text "$java/ModWorldGen.java") -match 'ore\(ModBlocks\.GEM_ORE, 0, 8, 8, 5, 30\);') 'worldgen entry'
    Assert ((Read-Text "$assets/recipes/gem_block.json") -match '"#": \{ "item": "demo:gem" \}') 'recipe json'
    $lang = Read-Text "$assets/lang/en_us.lang"
    foreach ($key in @('item.demo.gem.name=Gem', 'tile.demo.gem_ore.name=Gem Ore', 'item.demo.gem_pickaxe.name=Gem Pickaxe', 'item.demo.gem_chestplate.name=Gem Chestplate', 'tile.demo.gem_box.name=Gem Box')) { Assert ($lang.Contains($key)) "lang $key" }
    foreach ($file in @("$assets/textures/items/gem.png", "$assets/textures/blocks/gem_ore.png", "$assets/textures/models/armor/gem_layer_1.png", "$assets/blockstates/gem_block.json", 'src/gametest/java/com/example/demo/GemOreDropsTest.java')) { Assert (Test-Path -LiteralPath (Join-Path $fixture $file)) "missing $file" }
    $own = if ($hasIndex) { Invoke-Agent @('api', 'GemBoxTile') 1 } else { "'GemBoxTile' is your mod's class: read src/main/java/com/example/demo/GemBoxTile.java" }
    Assert ($own -match "'GemBoxTile' is your mod's class: read src/main/java/com/example/demo/GemBoxTile\.java") "api on own class: $own"
    $dup = Invoke-Agent @('new', 'item', 'gem') 1
    Assert ($dup -match 'already declared') 'duplicate rejected'
    $passed.Add('generators: item block ore food tools armor recipe smelting tile mob test, duplicate refusal')

    # Agents issue tool calls in parallel: concurrent generators must wait for each other, not interleave edits.
    $parallel = foreach ($name in @('pearl', 'shard', 'sliver')) { Start-Process -FilePath $shell -ArgumentList @('-NoProfile', '-File', (Join-Path $fixture 'agent.ps1'), 'new', 'item', $name) -WindowStyle Hidden -PassThru }
    $parallel | ForEach-Object { $_.WaitForExit(); Assert ($_.ExitCode -eq 0) "parallel new item exit $($_.ExitCode)" }
    $items = Read-Text "$java/ModItems.java"; $lang = Read-Text "$assets/lang/en_us.lang"
    foreach ($name in @('pearl', 'shard', 'sliver')) { Assert ($items -match "item\(`"$name`"" -and $lang.Contains("item.demo.$name.name=")) "parallel generators lost $name" }
    $passed.Add('parallel generators serialized')

    # Presets, recipe sets, generated checks, reference validation.
    $tools = Invoke-Agent @('new', 'tools', 'opal', '-Only', 'all', '-Like', 'iron', '-Durability', 'diamond', '-Repair', 'gem', '-Recipes')
    Assert ($tools -match '^created tools opal: opal_sword' -and $tools -match '5 recipe\(s\)') "tools summary: $tools"
    $items = Read-Text "$java/ModItems.java"
    Assert ($items -match 'addToolMaterial\("opal", 2, 1561, 6\.0F, 2\.0F, 14\)' -and $items -match 'new ToolItems\.Axe\(OPAL_TOOLS, 8\.0F, -3\.1F\)') 'iron preset with diamond durability, vanilla iron axe'
    Assert ((Read-Text "$assets/recipes/opal_pickaxe.json") -match '"pattern": \[ "XXX", " # ", " # " \]') 'vanilla pickaxe shape'
    $generated = Read-Text 'src/gametest/java/com/example/demo/GeneratedContentTest.java'
    Assert ($generated -match 't\.craft\("demo:gem", "demo:gem", "demo:gem", "", "minecraft:stick"' -and $generated -match 't\.drops\("demo:gem_ore"' -and $generated -match 'getMaxDamage\(\) == 1561') 'generated checks'
    $variant = Invoke-Agent @('new', 'recipe', 'plank_gem', '-Shape', '##', '-Key', '#=minecraft:planks', '-Result', 'gem') 1
    Assert ($variant -match "Ingredient 'minecraft:planks' has variants.*minecraft:planks@0.*minecraft:planks@32767.*ore:plankWood" -and -not (Test-Path (Join-Path $fixture "$assets/recipes/plank_gem.json"))) "variant item without data: $variant"
    Invoke-Agent @('new', 'recipe', 'plank_gem', '-Shape', '##', '-Key', '#=minecraft:planks@32767', '-Result', 'gem') | Out-Null
    Assert ((Read-Text "$assets/recipes/plank_gem.json") -match '"item": "minecraft:planks", "data": 32767') 'variant item with data'
    $bad = Invoke-Agent @('new', 'recipe', 'bad', '-Ingredients', 'minecraft:iron_ingt', '-Result', 'gem') 1
    Assert ($bad -match 'not a Minecraft 1\.12\.2 item/block id') "vanilla typo: $bad"
    $missing = Invoke-Agent @('new', 'smelting', 'x', '-From', 'nothing_here', '-Result', 'gem') 1
    Assert ($missing -match 'does not exist yet') "unknown own item: $missing"
    Assert (-not (Test-Path (Join-Path $fixture "$assets/recipes/bad.json"))) 'rejected recipe left no file'
    # PowerShell turns an unquoted 3,8,6,3 into an array; it must still work.
    Push-Location $fixture
    try { $arrayOut = & (Join-Path $fixture 'agent.ps1') new armor jade -Only all -Protection 3,8,6,3 -Like diamond 2>$null } finally { Pop-Location }
    Assert (($arrayOut -join "`n") -match '^created armor jade' -and (Read-Text "$java/ModItems.java") -match 'new int\[\] \{3, 6, 8, 3\}, 10, SoundEvents\.ITEM_ARMOR_EQUIP_DIAMOND, 2\.0F') "unquoted list: $arrayOut"
    $passed.Add('presets, recipe sets, generated checks, reference validation, unquoted lists')

    # Explicit numbers beat -Like (field test: -Speed/-Damage/-Toughness were ignored); -Only limits the set; stats are echoed.
    $dense = Invoke-Agent @('new', 'tools', 'dense', '-Like', 'diamond', '-Durability', '4683', '-Speed', '24', '-Damage', '17', '-Enchant', '0', '-Only', 'sword', '-Repair', 'minecraft:diamond_block', '-Recipes')
    $items = Read-Text "$java/ModItems.java"
    Assert ($items -match 'addToolMaterial\("dense", 3, 4683, 24\.0F, 17\.0F, 0\)' -and $items -match 'DENSE_SWORD = item' -and $items -notmatch 'dense_pickaxe|dense_axe|dense_hoe') "explicit tool numbers, sword only: $dense"
    Assert ($dense -match 'created tools dense: dense_sword \|' -and $dense -match '1 recipe\(s\)' -and $dense -match 'stats: level 3, 4683 uses, mining speed 24, damage bonus 17, enchantability 0; sword tooltip 21') "tools stats line: $dense"
    Assert (-not (Test-Path (Join-Path $fixture "$assets/recipes/dense_pickaxe.json"))) 'no recipes for pieces not asked for'
    $axe = Invoke-Agent @('new', 'tools', 'heavy', '-Like', 'iron', '-AxeDamage', '24', '-AxeSpeed', '-2.5', '-Only', 'axe')
    Assert ((Read-Text "$java/ModItems.java") -match 'new ToolItems\.Axe\(HEAVY_TOOLS, 24\.0F, -2\.5F\)' -and $axe -match 'axe tooltip 25') "explicit axe numbers: $axe"
    $denseArmor = Invoke-Agent @('new', 'armor', 'dense', '-Like', 'diamond', '-Durability', '99', '-Protection', '9,24,18,9', '-Toughness', '6', '-Enchant', '3', '-Only', 'chestplate,boots')
    Assert ((Read-Text "$java/ModItems.java") -match 'new int\[\] \{9, 18, 24, 9\}, 3, SoundEvents\.ITEM_ARMOR_EQUIP_DIAMOND, 6\.0F' -and (Read-Text "$java/ModItems.java") -notmatch 'dense_helmet') "explicit armor numbers, two pieces: $denseArmor"
    Assert ($denseArmor -match 'stats: chestplate 24 armor/1584 uses, boots 9 armor/1287 uses; toughness 6, enchantability 3; total armor 33' -and $denseArmor -match 'stops counting at 20') "armor stats line: $denseArmor"
    $wrongKind = Invoke-Agent @('new', 'tools', 'nope', '-Only', 'all', '-Toughness', '6') 1
    Assert ($wrongKind -match '-Toughness does not apply to new tools \(used by new armor\)' -and (Read-Text "$java/ModItems.java") -notmatch 'NOPE_') "option for another kind rejected: $wrongKind"
    $badPiece = Invoke-Agent @('new', 'tools', 'nope', '-Only', 'sword,spear') 1
    Assert ($badPiece -match "Unknown -Only piece 'spear'") "bad -Only piece: $badPiece"
    $noPieces = Invoke-Agent @('new', 'armor', 'nope', '-Like', 'iron') 1
    Assert ($noPieces -match 'new armor needs -Only' -and (Read-Text "$java/ModItems.java") -notmatch 'NOPE_') "missing -Only rejected: $noPieces"
    $passed.Add('explicit tool/armor numbers win over -Like, -Only required, stats line, options for other kinds rejected')

    # Batch stops at the first failing line.
    [IO.File]::WriteAllText((Join-Path $fixture 'plan.txt'), "# comment`nnew item amber`nnew recipe broken -Ingredients `"no_such_item`" -Result amber`nnew item never_made`n")
    Push-Location $fixture
    try { $batch = Invoke-Agent @('batch', 'plan.txt') 1 } finally { Pop-Location }
    Assert ($batch -match 'created item amber' -and $batch -match 'stopped at line 3' -and (Read-Text "$java/ModItems.java") -notmatch 'never_made') "batch: $batch"
    $passed.Add('batch stops at first failure')

    $version = Invoke-Agent @('version', '1.2.3')
    Assert ((Invoke-Agent @('version', '1.0.9') 1) -match 'lower than the current 1\.2\.3') 'version downgrade refused'
    Assert ((Read-Text 'build.gradle') -match "version = '1.2.3'" -and (Read-Text "$java/DemoMod.java") -match 'VERSION = "1.2.3"' -and (Read-Text 'src/main/resources/mcmod.info') -match '"version": "1.2.3"') "version: $version"
    $passed.Add('version sync')

    $env:FAKE_MODE = 'ok'
    # A byte-order mark (PowerShell Set-Content) is stripped before building.
    $bomFile = Join-Path $fixture "$java/BomProbe.java"
    [IO.File]::WriteAllText($bomFile, 'class BomProbe {}', (New-Object Text.UTF8Encoding($true)))
    $bomCheck = Invoke-Agent @('check')
    Assert ($bomCheck -match 'removed a UTF-8 byte-order mark from src/main/java/com/example/demo/BomProbe\.java' -and [IO.File]::ReadAllBytes($bomFile)[0] -eq [byte][char]'c') "BOM strip: $bomCheck"
    Remove-Item -LiteralPath $bomFile
    Assert ((Invoke-Agent @('status')) -match 'placeholder check not filled in: GemOreDropsTest\.java') 'scaffold reminder'
    $check = Invoke-Agent @('check')
    Assert ($check -match '^PASS build') "check: $check"
    $json = (Invoke-Agent @('check', '-Json')) | ConvertFrom-Json
    Assert ($json.status -eq 'PASS' -and $json.runId -and $json.projectRoot -eq $fixture) 'json check'
    # One network failure (slow Forge/Gradle download) is retried automatically.
    $env:FAKE_MODE = 'network-once'
    $retried = Invoke-Agent @('check')
    Assert ($retried -match '^PASS build' -and (Test-Path (Join-Path $fixture 'network-failed-once'))) "network retry: $retried"
    $env:FAKE_MODE = 'ok'
    $passed.Add('compact and JSON check output, network retry')

    # Client-only members compile but throw NoSuchMethodError on a server: an ERROR in game tests, a WARN in common code,
    # nothing inside @SideOnly(CLIENT) members or onDataPacket (only the client calls it).
    if ($hasIndex) {
    $testFile = Join-Path $fixture 'src/gametest/java/com/example/demo/ColorTest.java'
    New-Item -ItemType Directory -Force -Path (Split-Path -Parent $testFile) | Out-Null
    [IO.File]::WriteAllText($testFile, "package com.example.demo;`nclass ColorTest { int c = net.minecraft.item.EnumDyeColor.RED.getColorValue(); }`n")
    $clientTest = Invoke-Agent @('check') 1
    Assert ($clientTest -match '(?m)^ERROR src/gametest/java/com/example/demo/ColorTest\.java:2: EnumDyeColor\.getColorValue\(\) is client-only') "client-only call in a test: $clientTest"
    Remove-Item -LiteralPath $testFile
    $tileFile = Join-Path $fixture "$java/SyncTile.java"
    [IO.File]::WriteAllText($tileFile, (@(
        'package com.example.demo;', 'import net.minecraftforge.fml.relauncher.Side;', 'import net.minecraftforge.fml.relauncher.SideOnly;',
        'public class SyncTile extends net.minecraft.tileentity.TileEntity {', '    @Override',
        '    public void onDataPacket(net.minecraft.network.NetworkManager net, net.minecraft.network.play.server.SPacketUpdateTileEntity pkt) {',
        '        readFromNBT(pkt.getNbtCompound());', '    }', '    @SideOnly(Side.CLIENT)',
        '    public int tint() { return net.minecraft.item.EnumDyeColor.RED.getColorValue(); }',
        '    public int serverTint() {', '        return net.minecraft.item.EnumDyeColor.RED.getColorValue();', '    }', '}') -join "`n"))
    $shotFile = Join-Path $fixture "$java/ShotItem.java"
    [IO.File]::WriteAllText($shotFile, (@(
        'package com.example.demo;', 'import net.minecraft.entity.EntityLivingBase;', 'import net.minecraft.entity.projectile.EntitySnowball;',
        'import net.minecraft.item.ItemStack;', 'import net.minecraft.world.World;',
        'public class ShotItem extends net.minecraft.item.Item {', '    @Override',
        '    public void onPlayerStoppedUsing(ItemStack stack, World world, EntityLivingBase user, int timeLeft) {',
        '        world.spawnEntity(new EntitySnowball(world, user));', '    }', '    @Override',
        '    public void onUsingTick(ItemStack stack, EntityLivingBase user, int count) {',
        '        if (!user.world.isRemote) user.world.spawnEntity(new EntitySnowball(user.world, user));', '    }',
        '    private void helper(World world, EntityLivingBase user) { world.spawnEntity(new EntitySnowball(world, user)); }', '}') -join "`n"))
    $clientMain = Invoke-Agent @('check')
    $warnings = @($clientMain -split "`n" | Where-Object { $_ -match 'client-only' })
    Assert ($warnings.Count -eq 1 -and $warnings[0] -match "^WARN $([regex]::Escape($java))/SyncTile\.java:12: EnumDyeColor\.getColorValue\(\)") "client-only call in common code: $clientMain"
    $ghosts = @($clientMain -split "`n" | Where-Object { $_ -match 'ghost copy' })
    Assert ($ghosts.Count -eq 1 -and $ghosts[0] -match "^WARN $([regex]::Escape($java))/ShotItem\.java:9: spawnEntity in onPlayerStoppedUsing") "ghost spawn: $clientMain"
    Remove-Item -LiteralPath $tileFile, $shotFile
    $passed.Add('client-only calls: test ERROR, common WARN, client regions skipped; unguarded spawn in a both-sides hook')
    } else { $passed.Add('SKIPPED client-only calls and block-item hint (no kit/.kit/api-index.tsv: run kit/agent.ps1 check environment)') }

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
    if ($hasIndex) {
        $env:FAKE_MODE = 'block-item'
        $blockItem = Invoke-Agent @('check') 1
        Assert ($blockItem -match 'hint: Items has no block items in 1\.12\.2: use Item\.getItemFromBlock\(Blocks\.STONEBRICK\)') "block item hint: $blockItem"
    }
    $env:FAKE_MODE = 'compile-error'
    Invoke-Agent @('check') 1 | Out-Null
    $passed.Add('compile diagnostics, hints, errors, status')

    # Same failure three checks in a row (errors reprints do not count): STUCK; a PASS resets the streak.
    Assert ($failure -notmatch 'STUCK') 'no STUCK on first failure'
    Invoke-Agent @('check') 1 | Out-Null
    $stuck = Invoke-Agent @('check') 1
    Assert ($stuck -match 'STUCK: the same problem 3 checks in a row: src/main/java/com/example/demo/DemoMod\.java: cannot find symbol') "circuit breaker: $stuck"
    $env:FAKE_MODE = 'ok'
    Invoke-Agent @('check') | Out-Null
    Assert (-not (Test-Path (Join-Path $fixture 'reports/testing-tools/streak.json'))) 'streak reset on PASS'
    # One test failing at a different step each time: STUCK at the fifth check in a row.
    $env:FAKE_MODE = 'test-fail'
    foreach ($n in 1..5) { $env:FAKE_PHASE = "setup $n"; $game = Invoke-Agent @('check', 'game') 1; if ($n -eq 4) { Assert ($game -notmatch 'STUCK') "early STUCK: $game" } }
    Assert ($game -match 'STUCK: test b failed 5 checks in a row') "test circuit breaker: $game"
    # Fail at a step, pass, fail at the same step again: FLAKY (a PASS resets STUCK, not this).
    $env:FAKE_PHASE = 'pass'; Invoke-Agent @('check', 'game') | Out-Null
    $env:FAKE_PHASE = 'setup 5'; $flaky = Invoke-Agent @('check', 'game') 1
    Assert ($flaky -match 'FLAKY: test b at setup 5 failed there before' -and $flaky -notmatch 'STUCK') "flaky signal: $flaky"
    $env:FAKE_PHASE = 'setup 6'; Assert ((Invoke-Agent @('check', 'game') 1) -notmatch 'FLAKY') 'no FLAKY at a new step'
    $env:FAKE_MODE = 'ok'
    Invoke-Agent @('check') | Out-Null
    $passed.Add('circuit breaker after 3 identical failures or 5 failures of one test; FLAKY after fail-pass-fail at one step')

    $export = Invoke-Agent @('export') 1
    Assert ($export -match 'check server') "export gate: $export"
    $passed.Add('export requires server check')

    "PASS: $($passed.Count) agent interface groups ($($passed -join '; '))"
    exit 0
} catch {
    "FAIL: $($_.Exception.Message)`npassed: $($passed -join '; ')`nfixture: $fixture"
    exit 1
} finally { Remove-Item Env:FAKE_MODE, Env:FAKE_PHASE -ErrorAction SilentlyContinue }
