# Generates known-good content into a skeleton made by `agent.ps1 init`: Java declarations at
# // kit:<marker> lines, JSON models/blockstates/recipes, lang entries, placeholder textures and
# matching checks in src/gametest/.../GeneratedContentTest.java.
# Usage: agent.ps1 new <kind> <name> [options]   (kinds and options: docs/GENERATORS.md)
[CmdletBinding()]
param(
    [Parameter(Mandatory)][ValidateSet('item', 'block', 'ore', 'food', 'tools', 'armor', 'recipe', 'smelting', 'tile', 'test')][string]$Kind,
    [Parameter(Position = 0)][string]$Name,
    [string]$Display,
    [string]$Tab,
    [int]$StackSize,
    [ValidateSet('gem', 'ingot', 'raw', 'nugget', 'dust', 'rod')][string]$Look,
    [string]$Material = 'ROCK',
    [double]$Hardness = 3.0,
    [double]$Resistance = -1,
    [string]$Tool,
    [int]$Level = -1,
    [double]$Light = 0,
    [string]$Drops,
    [string]$Count,
    [string]$Xp,
    [int]$Veins = 8,
    [int]$VeinSize = 8,
    [string]$Height = '5-48',
    [int]$Dimension = 0,
    [int]$Hunger = 4,
    [double]$Saturation = 0.3,
    [switch]$Wolf,
    [switch]$AlwaysEdible,
    [string]$Effect,
    [string]$Like,
    [string]$Durability,
    [double]$Speed,
    [double]$Damage,
    [int]$Enchant,
    [string]$Protection,
    [double]$Toughness,
    [double]$AxeDamage,
    [double]$AxeSpeed,
    [string]$Repair,
    [string]$Only,
    [switch]$Recipes,
    [string]$Handle = 'minecraft:stick',
    [string]$Shape,
    [string]$Key,
    [string]$Ingredients,
    [string]$Result,
    [string]$From,
    [switch]$Ticking,
    [int]$Inventory = 0,
    [string]$Color,
    [switch]$Force,
    [switch]$List,
    [switch]$Json
)
$ErrorActionPreference = 'Stop'
[Console]::OutputEncoding = New-Object Text.UTF8Encoding($false)
$root = Split-Path -Parent $PSScriptRoot
$utf8 = New-Object Text.UTF8Encoding($false)
$created = New-Object 'System.Collections.Generic.List[string]'
$notes = New-Object 'System.Collections.Generic.List[string]'
$registered = New-Object 'System.Collections.Generic.List[string]'
$script:checkCount = 0
$script:recipeCount = 0
# Inside functions $PSBoundParameters is the function's own; keep the script's to tell "-Speed 0" from no -Speed.
$bound = $PSBoundParameters

# Options each kind reads; anything else is rejected so no option is silently ignored.
$commonOptions = @('Kind', 'Name', 'Display', 'Color', 'Force', 'List', 'Json')
$kindOptions = @{
    item     = @('Tab', 'StackSize', 'Look')
    block    = @('Material', 'Hardness', 'Resistance', 'Tool', 'Level', 'Light')
    ore      = @('Drops', 'Count', 'Xp', 'Level', 'Hardness', 'Veins', 'VeinSize', 'Height', 'Dimension')
    food     = @('Hunger', 'Saturation', 'Wolf', 'AlwaysEdible', 'Effect')
    tools    = @('Like', 'Level', 'Durability', 'Speed', 'Damage', 'Enchant', 'AxeDamage', 'AxeSpeed', 'Repair', 'Recipes', 'Handle', 'Only')
    armor    = @('Like', 'Durability', 'Protection', 'Toughness', 'Enchant', 'Repair', 'Recipes', 'Only')
    recipe   = @('Shape', 'Key', 'Ingredients', 'Result', 'Count')
    smelting = @('From', 'Result', 'Count', 'Xp')
    tile     = @('Ticking', 'Inventory')
    test     = @()
}

# Vanilla values (net.minecraft.item.Item.ToolMaterial, ItemArmor.ArmorMaterial, ItemAxe).
$toolPresets = [ordered]@{
    wood    = @{ level = 0; uses = 59;   speed = 2.0;  damage = 0.0; enchant = 15; axeDamage = 6.0; axeSpeed = -3.2 }
    stone   = @{ level = 1; uses = 131;  speed = 4.0;  damage = 1.0; enchant = 5;  axeDamage = 8.0; axeSpeed = -3.2 }
    iron    = @{ level = 2; uses = 250;  speed = 6.0;  damage = 2.0; enchant = 14; axeDamage = 8.0; axeSpeed = -3.1 }
    diamond = @{ level = 3; uses = 1561; speed = 8.0;  damage = 3.0; enchant = 10; axeDamage = 8.0; axeSpeed = -3.0 }
    gold    = @{ level = 0; uses = 32;   speed = 12.0; damage = 0.0; enchant = 22; axeDamage = 6.0; axeSpeed = -3.0 }
}
# protection = helmet,chestplate,leggings,boots
$armorPresets = [ordered]@{
    leather   = @{ durability = 5;  protection = '1,3,2,1'; enchant = 15; toughness = 0.0; sound = 'LEATHER' }
    chainmail = @{ durability = 15; protection = '2,5,4,1'; enchant = 12; toughness = 0.0; sound = 'CHAIN' }
    iron      = @{ durability = 15; protection = '2,6,5,2'; enchant = 9;  toughness = 0.0; sound = 'IRON' }
    gold      = @{ durability = 7;  protection = '2,5,3,1'; enchant = 25; toughness = 0.0; sound = 'GOLD' }
    diamond   = @{ durability = 33; protection = '3,8,6,3'; enchant = 10; toughness = 2.0; sound = 'DIAMOND' }
}
$presetAliases = @{ wooden = 'wood'; golden = 'gold'; chain = 'chainmail' }

# ---- project discovery -------------------------------------------------------------------

function Get-Project {
    $javaRoot = Join-Path $root 'src/main/java'
    $mainFile = $null
    if ([IO.Directory]::Exists($javaRoot)) {
        foreach ($file in [IO.Directory]::GetFiles($javaRoot, '*.java', [IO.SearchOption]::AllDirectories)) {
            if ([IO.File]::ReadAllText($file) -match '@Mod\s*\(') { $mainFile = $file; break }
        }
    }
    if (-not $mainFile) { throw 'No mod found. Run agent.ps1 init <modid> "<Name>" first.' }
    $text = [IO.File]::ReadAllText($mainFile)
    if ($text -notmatch 'MODID\s*=\s*"([a-z0-9_]+)"') { throw "Could not find MODID in $mainFile" }
    $modId = $Matches[1]
    $package = if ($text -match '(?m)^package\s+([\w.]+);') { $Matches[1] } else { throw 'No package declaration in the @Mod class.' }
    $dir = Split-Path -Parent $mainFile
    foreach ($required in @('ModItems.java', 'ModBlocks.java')) {
        if (-not [IO.File]::Exists((Join-Path $dir $required))) { throw "$required not found next to the @Mod class. Generators need the agent.ps1 init skeleton; add content by hand instead." }
    }
    return [ordered]@{
        modId = $modId; package = $package; dir = $dir; mainFile = $mainFile
        mainClass = [IO.Path]::GetFileNameWithoutExtension($mainFile)
        items = (Join-Path $dir 'ModItems.java'); blocks = (Join-Path $dir 'ModBlocks.java')
        assets = (Join-Path $root ('src/main/resources/assets/' + $modId))
        tests = (Join-Path $root ('src/gametest/java/' + $package.Replace('.', '/')))
    }
}

# ---- file editing ------------------------------------------------------------------------

function Get-Rel([string]$Path) { $Path.Substring($root.Length).TrimStart('\', '/').Replace('\', '/') }
function Note-File([string]$Path) { $rel = Get-Rel $Path; if (-not $created.Contains($rel)) { $created.Add($rel) } }

function Write-New([string]$Path, [string]$Text) {
    if ([IO.File]::Exists($Path)) { throw "$(Get-Rel $Path) already exists; refusing to overwrite." }
    New-Item -ItemType Directory -Force -Path (Split-Path -Parent $Path) | Out-Null
    [IO.File]::WriteAllText($Path, $Text.Replace("`r`n", "`n"), $utf8)
    Note-File $Path
}

function Add-AtMarker([string]$File, [string]$Marker, [string[]]$Lines) {
    $text = [IO.File]::ReadAllText($File)
    $match = [regex]::Match($text, '(?m)^([ \t]*)// kit:' + [regex]::Escape($Marker) + '\b.*$')
    if (-not $match.Success) { throw "Marker '// kit:$Marker' not found in $(Get-Rel $File); restore it or add the code by hand." }
    $indent = $match.Groups[1].Value
    $block = ($Lines | ForEach-Object { if ($_) { $indent + $_ } else { '' } }) -join "`n"
    [IO.File]::WriteAllText($File, $text.Insert($match.Index, $block + "`n"), $utf8)
    Note-File $File
}

function Add-Import([string]$File, [string[]]$Imports) {
    $text = [IO.File]::ReadAllText($File)
    foreach ($import in $Imports) {
        if ($text -match ('(?m)^import ' + [regex]::Escape($import) + ';')) { continue }
        $last = [regex]::Matches($text, '(?m)^import [^;]+;\r?\n')
        $at = if ($last.Count) { $last[$last.Count - 1].Index + $last[$last.Count - 1].Length } else { [regex]::Match($text, '(?m)^package [^;]+;\r?\n').Length }
        $text = $text.Insert($at, "import $import;`n")
    }
    [IO.File]::WriteAllText($File, $text, $utf8)
}

function Add-Lang([string]$LangKey, [string]$Value) {
    $path = Join-Path $project.assets 'lang/en_us.lang'
    $text = if ([IO.File]::Exists($path)) { [IO.File]::ReadAllText($path) } else { '' }
    if ($text -match ('(?m)^' + [regex]::Escape($LangKey) + '=')) { return }
    if ($text.Length -and -not $text.EndsWith("`n")) { $text += "`n" }
    New-Item -ItemType Directory -Force -Path (Split-Path -Parent $path) | Out-Null
    [IO.File]::WriteAllText($path, $text + "$LangKey=$Value`n", $utf8)
    Note-File $path
}

# Checks appended to one auto-generated game test, run by `agent.ps1 check game`.
function Add-Check([string[]]$Lines) {
    $file = Join-Path $project.tests 'GeneratedContentTest.java'
    if (-not [IO.File]::Exists($file)) {
        Write-New $file @"
package $($project.package);

import java.util.List;
import kit.gametest.GameTest;
import kit.gametest.Scenario;
import kit.gametest.TestContext;
import net.minecraft.item.ItemStack;

/** Written by agent.ps1 new ...: one check per generated feature. Edit freely; keep the marker line. */
@GameTest("generated-content")
public class GeneratedContentTest implements Scenario {
    @Override
    public void plan(TestContext t) {
        t.then("generated content", () -> {
            // kit:checks
        });
    }
}
"@
    }
    Add-AtMarker $file 'checks' $Lines
    $script:checkCount += @($Lines | Where-Object { $_ -match 't\.require\(' }).Count
}

function Assert-NewName([string]$RegistryName) {
    foreach ($file in @($project.items, $project.blocks)) {
        if ([IO.File]::ReadAllText($file) -match ('\("' + [regex]::Escape($RegistryName) + '"')) { throw "'$RegistryName' is already declared in $(Get-Rel $file)." }
    }
}

function ConvertTo-Constant([string]$Text) { $Text.ToUpperInvariant() }
function ConvertTo-Pascal([string]$Text) { ($Text.Split('_') | Where-Object { $_ } | ForEach-Object { $_.Substring(0, 1).ToUpperInvariant() + $_.Substring(1) }) -join '' }
function ConvertTo-Title([string]$Text) { ($Text.Split('_') | Where-Object { $_ } | ForEach-Object { $_.Substring(0, 1).ToUpperInvariant() + $_.Substring(1) }) -join ' ' }
function F([double]$Value) { $Value.ToString('0.0###', [Globalization.CultureInfo]::InvariantCulture) + 'F' }
function Q([string]$Text) { '"' + $Text.Replace('\', '\\').Replace('"', '\"') + '"' }

# ---- item references ---------------------------------------------------------------------

# "ruby" -> "<modid>:ruby"; "minecraft:diamond", "minecraft:dye@4" kept.
function ConvertTo-ItemId([string]$Ref) {
    if (-not $Ref) { return $null }
    $id = $Ref.Split('@')[0]
    if ($id -notmatch ':') { $id = $project.modId + ':' + $id }
    if ($id -notmatch '^[a-z0-9_.-]+:[a-z0-9_/.-]+$') { throw "Not an item id: '$Ref' (use name, modid:name or minecraft:name)" }
    return $id
}

# Vanilla registry names, read once from the pinned source (Item.registerItems / Block.registerBlocks).
function Get-VanillaIds {
    if ($script:vanillaIds) { return $script:vanillaIds }
    $cache = Join-Path $root '.kit/vanilla-ids.txt'
    if (-not [IO.File]::Exists($cache)) {
        $home2 = if ($env:GRADLE_USER_HOME) { $env:GRADLE_USER_HOME } else { Join-Path $env:USERPROFILE '.gradle' }
        $jar = Join-Path $home2 'caches/minecraftforge/forgegradle/mavenizer/caches/forge/net/minecraftforge/forge/1.12.2-14.23.5.2859/stable/39-1.12/remapped-javadoc.jar'
        if (-not [IO.File]::Exists($jar)) { return $null }
        Add-Type -AssemblyName System.IO.Compression.FileSystem
        $zip = [IO.Compression.ZipFile]::OpenRead($jar)
        $ids = New-Object 'System.Collections.Generic.SortedSet[string]'
        try {
            foreach ($entryName in @('net/minecraft/item/Item.java', 'net/minecraft/block/Block.java')) {
                $reader = New-Object IO.StreamReader($zip.GetEntry($entryName).Open())
                try { $source = $reader.ReadToEnd() } finally { $reader.Dispose() }
                foreach ($m in [regex]::Matches($source, 'register(?:Item|Block)\(\d+,\s*"([a-z0-9_]+)"')) { [void]$ids.Add($m.Groups[1].Value) }
            }
        } finally { $zip.Dispose() }
        New-Item -ItemType Directory -Force -Path (Split-Path -Parent $cache) | Out-Null
        [IO.File]::WriteAllLines($cache, [string[]]@($ids), $utf8)
    }
    $script:vanillaIds = New-Object 'System.Collections.Generic.HashSet[string]' (,[string[]][IO.File]::ReadAllLines($cache))
    return $script:vanillaIds
}

# Fails on references that cannot exist at runtime (unknown own-mod or vanilla names); -Force skips.
function Assert-ItemRef([string]$Ref, [string]$What) {
    if (-not $Ref -or $Ref -match '^ore:') { return }
    $id = ConvertTo-ItemId $Ref
    if ($Force) { return }
    $ns, $path = $id.Split(':', 2)
    if ($ns -eq $project.modId) {
        $known = $registered.Contains($path)
        if (-not $known) {
            foreach ($file in [IO.Directory]::GetFiles((Join-Path $root 'src/main/java'), '*.java', [IO.SearchOption]::AllDirectories)) {
                if ([IO.File]::ReadAllText($file) -match ('"' + [regex]::Escape($path) + '"')) { $known = $true; break }
            }
        }
        if (-not $known) { throw "$What '$id' does not exist yet. Create it first (agent.ps1 new item $path ...), fix the name, or pass -Force." }
    } elseif ($ns -eq 'minecraft') {
        $vanilla = Get-VanillaIds
        if ($vanilla -and -not $vanilla.Contains($path)) { throw "$What '$id' is not a Minecraft 1.12.2 item/block id (check spelling, e.g. minecraft:iron_ingot), or pass -Force." }
    } else {
        $notes.Add("$id belongs to another mod; it must be installed for the recipe to load")
    }
}

# Java expression for an item, evaluated when called (safe in lambdas and init).
function ConvertTo-ItemExpr([string]$Ref) {
    $id = ConvertTo-ItemId $Ref
    $constant = ConvertTo-Constant ($id.Split(':')[1])
    if ($id.StartsWith($project.modId + ':')) {
        if ([IO.File]::ReadAllText($project.items) -match ('public static final \w+ ' + $constant + ' =')) { return "ModItems.$constant" }
        if ([IO.File]::ReadAllText($project.blocks) -match ('public static final \w+ ' + $constant + ' =')) { return "Item.getItemFromBlock(ModBlocks.$constant)" }
    }
    return 'java.util.Objects.requireNonNull(Item.getByNameOrId("' + $id + '"), "unknown item ' + $id + '")'
}

# ---- placeholder textures ----------------------------------------------------------------

# 16x16 masks: . empty, o outline, d dark, x base, h highlight, s handle.
$masks = @{
    ingot = '................|................|................|................|................|................|.......oooooo...|.....oohhhhhxo..|...oohhxxxxxxdo.|..ohxxxxxxxxddo.|..oxxxxxxxxddoo.|..oddddddddoo...|...ooooooooo....|................|................|................'
    raw = '................|................|................|......oooo......|....oohhhxoo....|...ohhxxxxxdo...|..ohxxxhxxxxdo..|..oxxxxxxxxddo..|.ohxxxxxxxxxdo..|.oxxxhxxxxxddo..|.oxxxxxxxdxddo..|..odxxxxddddo...|...oodddddoo....|.....oooooo.....|................|................'
    nugget = '................|................|................|................|................|......oooo......|.....ohhxxo.....|....ohxxxxdo....|....oxxxxxdo....|....odxxxddo....|.....oddddo.....|......oooo......|................|................|................|................'
    dust = '................|................|................|................|................|................|................|.......oo.......|......ohxo......|.....ohxxxo.....|....ohxxxxdo....|...ohxxhxxxdo...|..ohxxxxxxxxdo..|..oddddddddddo..|...oooooooooo...|................'
    rod = '................|............oo..|...........ohxo.|..........ohxdo.|.........ohxdo..|........ohxdo...|.......ohxdo....|......ohxdo.....|.....ohxdo......|....ohxdo.......|...ohxdo........|..ohxdo.........|.ohxdo..........|.oxdo...........|..oo............|................'
    sword = '............ooo.|...........ohxo.|..........ohxo..|.........ohxo...|........ohxo....|.......ohxo.....|......ohxo......|..oo.ohxo.......|..odohxo........|...oddo.........|...osdo.........|..osoodo........|.oso..oo........|oso.............|oo..............|................'
    pickaxe = '................|...oooooooo.....|..ohhxxxxxxoo...|...ooooooxxxdo..|........osodxo..|.......oso.odxo.|......oso...odo.|.....oso....odo.|....oso......o..|...oso..........|..oso...........|.oso............|oso.............|oo..............|................|................'
    axe = '................|.......ooo......|......ohxxoo....|.....ohxxxxdo...|.....oxxxxsdo...|......odxsodo...|.......osodo....|......oso.oo....|.....oso........|....oso.........|...oso..........|..oso...........|.oso............|oso.............|oo..............|................'
    shovel = '................|...........ooo..|..........ohxxo.|.........ohxxxdo|.........oxxxdo.|........osxddo..|.......oso.oo...|......oso.......|.....oso........|....oso.........|...oso..........|..oso...........|.oso............|oso.............|oo..............|................'
    hoe = '................|.......oooo.....|......ohhxxoo...|.......oooxsdo..|.........oso.o..|........oso.....|.......oso......|......oso.......|.....oso........|....oso.........|...oso..........|..oso...........|.oso............|oso.............|oo..............|................'
    helmet = '................|................|................|....oooooooo....|...ohhhxxxxxo...|..ohxxxxxxxxdo..|..oxxxxxxxxxdo..|..oxxooooooxdo..|..oxo......oxo..|..oxo......odo..|..ooo......ooo..|................|................|................|................|................'
    chestplate = '................|..oooo....oooo..|.ohxxoo..ooxxdo.|.oxxxxooooxxxdo.|.oxxxxxhhxxxxdo.|.oooxxxxxxxxooo.|...oxxxxxxxdo...|...oxxxhxxxdo...|...oxxxxxxxdo...|...oxxxxxxxdo...|...oxxxxxxxdo...|...oddddddddo...|...oooooooooo...|................|................|................'
    leggings = '................|...oooooooooo...|...ohhxxxxxxo...|...oxxxxxxxdo...|...oxxxoxxxdo...|...oxxo.oxxdo...|...oxxo.oxxdo...|...oxxo.oxxdo...|...oxxo.oxxdo...|...oxxo.oxxdo...|...oxdo.oxddo...|...oooo.oooo....|................|................|................|................'
    boots = '................|................|................|................|................|...oooo..oooo...|...ohxo..ohxo...|...oxxo..oxxo...|...oxxo..oxxo...|..oxxdo..oxxdo..|.ohxxdo..ohxxdo.|.oddddo..oddddo.|.oooooo..oooooo.|................|................|................'
    food = '................|.......oo.......|........ss......|.....oooosoo....|....ohhxxxxdo...|...ohxxxxxxxdo..|...ohxxxxxxxdo..|...oxxxxxxxxdo..|...oxxxxxxxxdo..|...oxxxxxxxddo..|....oxxxxxddo...|....odxxxdddo...|.....odddddo....|......oooooo....|................|................'
}

Add-Type -AssemblyName System.Drawing
# gron_ingot, raw_gron, gron_ore and gron_block share the color of material "gron".
function Get-MaterialSeed([string]$Seed) {
    $root2 = $Seed -replace '^(raw|block_of|crushed|polished|cut)_', '' -replace '_(ingot|nugget|dust|gem|ore|block|shard|crystal|raw|plate|gear|rod|sword|pickaxe|axe|shovel|hoe|helmet|chestplate|leggings|boots|apple|layer_\d)$', ''
    if ($root2) { return $root2 } else { return $Seed }
}
function Get-BaseColor([string]$Seed) {
    $Seed = Get-MaterialSeed $Seed
    if ($Color) { return [Drawing.ColorTranslator]::FromHtml('#' + $Color.TrimStart('#')) }
    $hash = 0; foreach ($c in $Seed.ToCharArray()) { $hash = ($hash * 31 + [int]$c) % 360 }
    $h = $hash / 60.0; $s = 0.65; $v = 0.85; $i = [Math]::Floor($h); $f = $h - $i
    $p = $v * (1 - $s); $q = $v * (1 - $s * $f); $t = $v * (1 - $s * (1 - $f))
    $rgb = switch ([int]$i % 6) { 0 { $v, $t, $p } 1 { $q, $v, $p } 2 { $p, $v, $t } 3 { $p, $q, $v } 4 { $t, $p, $v } default { $v, $p, $q } }
    return [Drawing.Color]::FromArgb([int]($rgb[0] * 255), [int]($rgb[1] * 255), [int]($rgb[2] * 255))
}
function Get-Shade([Drawing.Color]$C, [double]$Factor) {
    $clamp = { param($x) [Math]::Max(0, [Math]::Min(255, [int]$x)) }
    [Drawing.Color]::FromArgb(255, (& $clamp ($C.R * $Factor)), (& $clamp ($C.G * $Factor)), (& $clamp ($C.B * $Factor)))
}
# Recognisable placeholders (shaped icons, noisy blocks/ores); replace with real art any time.
function New-Texture([string]$Path, [string]$Style, [string]$Seed) {
    if ([IO.File]::Exists($Path)) { return }
    $base = Get-BaseColor $Seed
    $rand = New-Object Random ($Seed.GetHashCode())
    $w = 16; $h = 16; if ($Style -eq 'armor') { $w = 64; $h = 32 }
    $bmp = New-Object Drawing.Bitmap $w, $h, ([Drawing.Imaging.PixelFormat]::Format32bppArgb)
    if ($masks.ContainsKey($Style)) {
        $rows = $masks[$Style].Split('|')
        $shades = @{ 'o' = 0.45; 'd' = 0.75; 'x' = 1.0; 'h' = 1.3 }
        for ($y = 0; $y -lt 16; $y++) {
            for ($x = 0; $x -lt 16; $x++) {
                $cell = [string]$rows[$y][$x]
                $pixel = switch ($cell) {
                    '.' { [Drawing.Color]::Transparent }
                    's' { [Drawing.Color]::FromArgb(255, 110, 80, 45) }
                    default { Get-Shade $base $shades[$cell] }
                }
                $bmp.SetPixel($x, $y, $pixel)
            }
        }
    } else {
        for ($x = 0; $x -lt $w; $x++) {
            for ($y = 0; $y -lt $h; $y++) {
                $pixel = switch ($Style) {
                    'block' { if ($x -in 0, 15 -or $y -in 0, 15) { Get-Shade $base 0.7 } else { Get-Shade $base (0.9 + $rand.NextDouble() * 0.2) } }
                    'ore' { $g = 110 + $rand.Next(30); [Drawing.Color]::FromArgb(255, $g, $g, $g) }
                    'armor' { Get-Shade $base (0.85 + $rand.NextDouble() * 0.2) }
                    default {   # gem
                        $d = [Math]::Abs($x - 7.5) + [Math]::Abs($y - 7.5)
                        if ($d -gt 6.5) { [Drawing.Color]::Transparent } elseif ($d -gt 5.5) { Get-Shade $base 0.6 } else { Get-Shade $base $(if ($x + $y -lt 13) { 1.15 } else { 0.85 }) }
                    }
                }
                $bmp.SetPixel($x, $y, $pixel)
            }
        }
        if ($Style -eq 'ore') {
            for ($k = 0; $k -lt 6; $k++) {
                $cx = 2 + $rand.Next(11); $cy = 2 + $rand.Next(11)
                foreach ($o in @(@(0, 0), @(1, 0), @(0, 1), @(1, 1))) { $bmp.SetPixel($cx + $o[0], $cy + $o[1], (Get-Shade $base (0.85 + $rand.NextDouble() * 0.3))) }
            }
        }
    }
    New-Item -ItemType Directory -Force -Path (Split-Path -Parent $Path) | Out-Null
    $bmp.Save($Path, [Drawing.Imaging.ImageFormat]::Png)
    $bmp.Dispose()
    Note-File $Path
}

# ---- shared writers ----------------------------------------------------------------------

function New-ItemAssets([string]$ItemName, [string]$Title, [string]$Style, [string]$Parent = 'item/generated', [string]$Seed = $ItemName) {
    Write-New (Join-Path $project.assets "models/item/$ItemName.json") ('{ "parent": "' + $Parent + '", "textures": { "layer0": "' + $project.modId + ':items/' + $ItemName + '" } }' + "`n")
    New-Texture (Join-Path $project.assets "textures/items/$ItemName.png") $Style $Seed
    Add-Lang "item.$($project.modId).$ItemName.name" $Title
    $registered.Add($ItemName)
}
function New-BlockAssets([string]$BlockName, [string]$Title, [string]$Style) {
    $id = $project.modId + ':' + $BlockName
    Write-New (Join-Path $project.assets "blockstates/$BlockName.json") ('{ "variants": { "normal": { "model": "' + $id + '" } } }' + "`n")
    Write-New (Join-Path $project.assets "models/block/$BlockName.json") ('{ "parent": "block/cube_all", "textures": { "all": "' + $project.modId + ':blocks/' + $BlockName + '" } }' + "`n")
    Write-New (Join-Path $project.assets "models/item/$BlockName.json") ('{ "parent": "' + $project.modId + ':block/' + $BlockName + '" }' + "`n")
    New-Texture (Join-Path $project.assets "textures/blocks/$BlockName.png") $Style $BlockName
    Add-Lang "tile.$($project.modId).$BlockName.name" $Title
    $registered.Add($BlockName)
}

# "ruby", "minecraft:dye@4", "ore:ingotIron" -> recipe ingredient JSON
function ConvertTo-Ingredient([string]$Ref) {
    if ($Ref -match '^ore:([A-Za-z0-9_]+)$') { return '{ "type": "forge:ore_dict", "ore": "' + $Matches[1] + '" }' }
    $id = ConvertTo-ItemId $Ref
    if ($Ref -match '@(\d+)$') { return '{ "item": "' + $id + '", "data": ' + $Matches[1] + ' }' }
    return '{ "item": "' + $id + '" }'
}
# Test-side spelling of an ingredient: own/vanilla ids with optional @meta, or ore:name.
function ConvertTo-TestRef([string]$Ref) {
    if ($Ref -match '^ore:') { return $Ref }
    $id = ConvertTo-ItemId $Ref
    if ($Ref -match '@(\d+)$') { return "$id@$($Matches[1])" }
    return $id
}

# Shaped recipe JSON plus a crafting check. $Map: symbol -> item reference.
function Write-ShapedRecipe([string]$FileName, [string[]]$Rows, $Map, [string]$ResultRef, [int]$ResultCount) {
    foreach ($symbol in @($Map.Keys)) { Assert-ItemRef $Map[$symbol] 'Ingredient' }
    Assert-ItemRef $ResultRef 'Result'
    $resultId = ConvertTo-ItemId $ResultRef
    $resultJson = '{ "item": "' + $resultId + '"' + $(if ($ResultRef -match '@(\d+)$') { ', "data": ' + $Matches[1] } else { '' }) + $(if ($ResultCount -gt 1) { ', "count": ' + $ResultCount } else { '' }) + ' }'
    $keyJson = ($Map.Keys | ForEach-Object { '    "' + $_ + '": ' + (ConvertTo-Ingredient $Map[$_]) }) -join ",`n"
    Write-New (Join-Path $project.assets "recipes/$FileName.json") ("{`n  `"type`": `"minecraft:crafting_shaped`",`n  `"pattern`": [ " + (($Rows | ForEach-Object { '"' + $_ + '"' }) -join ', ') + " ],`n  `"key`": {`n$keyJson`n  },`n  `"result`": $resultJson`n}`n")
    $script:recipeCount++
    # The pattern sits in the grid's top-left corner; every cell is spelled out for the test.
    $cells = @()
    for ($r = 0; $r -lt 3; $r++) { for ($c = 0; $c -lt 3; $c++) {
        $symbol = if ($r -lt $Rows.Count -and $c -lt $Rows[$r].Length) { [string]$Rows[$r][$c] } else { ' ' }
        $cells += $(if ($symbol -eq ' ') { '""' } else { Q (ConvertTo-TestRef $Map[$symbol]) })
    } }
    Add-Check @(
        "{ ItemStack r = t.craft($($cells -join ', '));",
        "  t.require(r.getItem() == t.item($(Q $resultId)) && r.getCount() == $ResultCount, $(Q "recipe $FileName gave ") + r); }")
}

function Get-Range([string]$Text, [int]$DefaultMin, [int]$DefaultMax, [string]$What) {
    if (-not $Text) { return @($DefaultMin, $DefaultMax) }
    if ($Text -match '^(\d+)$') { return @([int]$Matches[1], [int]$Matches[1]) }
    if ($Text -match '^(\d+)-(\d+)$' -and [int]$Matches[1] -le [int]$Matches[2]) { return @([int]$Matches[1], [int]$Matches[2]) }
    throw "$What must be N or MIN-MAX, got '$Text'"
}

# -Only "sword,axe" -> those pieces in set order; "all" -> the whole set. Required, so a request
# for one sword never gets a pickaxe, axe, shovel and hoe by default.
function Get-Pieces([string[]]$All) {
    if (-not $Only) { throw "new $Kind needs -Only with the pieces the user asked for: any of $($All -join ','), or all" }
    if ($Only.Trim() -eq 'all') { return $All }
    $wanted = @($Only.Split(',') | ForEach-Object { $_.Trim().ToLowerInvariant() } | Where-Object { $_ })
    foreach ($piece in $wanted) { if ($All -notcontains $piece) { throw "Unknown -Only piece '$piece'. Use any of: $($All -join ',')" } }
    if (-not $wanted.Count) { throw "-Only needs at least one of: $($All -join ',')" }
    return @($All | Where-Object { $wanted -contains $_ })
}

function Get-Preset($Table, [string]$PresetName, [string]$What) {
    $key = $PresetName.ToLowerInvariant()
    if ($presetAliases.ContainsKey($key)) { $key = $presetAliases[$key] }
    if (-not $Table.Contains($key)) { throw "Unknown $What '$PresetName'. Use one of: $(@($Table.Keys) -join ', ')" }
    return $Table[$key]
}

$sounds = @{ ROCK = 'STONE'; IRON = 'METAL'; WOOD = 'WOOD'; GROUND = 'GROUND'; GRASS = 'PLANT'; SAND = 'SAND'; GLASS = 'GLASS'; CLOTH = 'CLOTH'; PLANTS = 'PLANT'; LEAVES = 'PLANT'; ICE = 'GLASS'; PACKED_ICE = 'GLASS'; CLAY = 'GROUND'; SNOW = 'SNOW'; CRAFTED_SNOW = 'SNOW'; ANVIL = 'ANVIL'; REDSTONE_LIGHT = 'GLASS'; GOURD = 'WOOD'; CACTUS = 'CLOTH'; SPONGE = 'PLANT'; CORAL = 'STONE'; CIRCUITS = 'STONE'; CARPET = 'CLOTH' }
$harvestTools = @{ ROCK = 'pickaxe'; IRON = 'pickaxe'; ANVIL = 'pickaxe'; WOOD = 'axe'; GOURD = 'axe'; GROUND = 'shovel'; GRASS = 'shovel'; SAND = 'shovel'; CLAY = 'shovel'; SNOW = 'shovel'; CRAFTED_SNOW = 'shovel' }

# ---- kinds -------------------------------------------------------------------------------

function Build-Item {
    $const = ConvertTo-Constant $Name
    $tab = if ($Tab) { $Tab.ToUpperInvariant() } else { 'MATERIALS' }
    $expr = "item(`"$Name`", new Item()).setCreativeTab(CreativeTabs.$tab)"
    if ($StackSize) { $expr += ".setMaxStackSize($StackSize)" }
    Add-AtMarker $project.items 'items' @("public static final Item $const = $expr;")
    $style = if ($Look) { $Look } elseif ($Name -match 'ingot') { 'ingot' } elseif ($Name -match '^raw_|_raw$') { 'raw' } elseif ($Name -match 'nugget') { 'nugget' } elseif ($Name -match 'dust|powder') { 'dust' } elseif ($Name -match 'rod|stick') { 'rod' } else { 'gem' }
    New-ItemAssets $Name $title $style
    $id = "$($project.modId):$Name"
    if ($StackSize) { Add-Check @("t.require(t.item($(Q $id)).getItemStackLimit() == $StackSize, $(Q "$Name stack size"));") }
}

function Build-Block {
    $const = ConvertTo-Constant $Name
    $mat = $Material.ToUpperInvariant()
    if (-not $sounds.ContainsKey($mat)) { throw "Unknown -Material $Material. Use one of: $($sounds.Keys -join ', ')" }
    $res = if ($Resistance -ge 0) { $Resistance } else { $Hardness }
    $expr = "new BasicBlock(Material.$mat, SoundType.$($sounds[$mat]), $(F $Hardness), $(F $res))"
    $harvestTool = if ($Tool) { $Tool } else { $harvestTools[$mat] }
    $harvestLevel = [Math]::Max(0, $Level)
    if ($harvestTool) { $expr += ".harvest(`"$harvestTool`", $harvestLevel)" }
    if ($Light -gt 0) { $expr += ".light($(F $Light))" }
    Add-AtMarker $project.blocks 'blocks' @("public static final Block $const = block(`"$Name`", $expr);")
    New-BlockAssets $Name $title 'block'
    if ($harvestTool) {
        $id = "$($project.modId):$Name"
        Add-Check @("t.require($(Q $harvestTool).equals(t.block($(Q $id)).getHarvestTool(t.block($(Q $id)).getDefaultState())) && t.block($(Q $id)).getHarvestLevel(t.block($(Q $id)).getDefaultState()) == $harvestLevel, $(Q "$Name harvest tool/level"));")
    }
}

function Build-Ore {
    $const = ConvertTo-Constant $Name
    Assert-ItemRef $Drops 'Drop'
    $oreFile = Join-Path $project.dir 'OreBlock.java'
    if (-not [IO.File]::Exists($oreFile)) {
        Write-New $oreFile @"
package $($project.package);

import java.util.Random;
import java.util.function.Supplier;
import net.minecraft.block.SoundType;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.item.Item;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;

/** Ore that drops an item (count range, vanilla-style Fortune bonus, XP), or itself when drop is null. Silk Touch always gives the block. */
public class OreBlock extends BasicBlock {
    private final Supplier<Item> drop;
    private final int minCount, maxCount, minXp, maxXp;

    public OreBlock(float hardness, int harvestLevel, Supplier<Item> drop, int minCount, int maxCount, int minXp, int maxXp) {
        super(Material.ROCK, SoundType.STONE, hardness, 5.0F);
        harvest("pickaxe", harvestLevel);
        this.drop = drop;
        this.minCount = minCount; this.maxCount = maxCount;
        this.minXp = minXp; this.maxXp = maxXp;
    }

    @Override
    public Item getItemDropped(IBlockState state, Random random, int fortune) {
        return drop == null ? Item.getItemFromBlock(this) : drop.get();
    }

    @Override
    public int quantityDropped(Random random) {
        return drop == null ? 1 : minCount + random.nextInt(maxCount - minCount + 1);
    }

    @Override
    public int quantityDroppedWithBonus(int fortune, Random random) {
        if (drop == null || fortune <= 0) return quantityDropped(random);
        int bonus = Math.max(0, random.nextInt(fortune + 2) - 1);
        return quantityDropped(random) * (bonus + 1);
    }

    @Override
    public int getExpDrop(IBlockState state, IBlockAccess world, BlockPos pos, int fortune) {
        if (drop == null) return 0;
        Random random = world instanceof World ? ((World) world).rand : new Random();
        return MathHelper.getInt(random, minXp, maxXp);
    }
}
"@
    }
    $countRange = Get-Range $Count 1 1 '-Count'
    $xpRange = if ($Drops) { Get-Range $Xp 3 7 '-Xp' } else { @(0, 0) }
    $dropExpr = if ($Drops) { '() -> ' + (ConvertTo-ItemExpr $Drops) } else { 'null' }
    $lvl = if ($Level -ge 0) { $Level } else { 1 }
    Add-AtMarker $project.blocks 'blocks' @("public static final Block $const = block(`"$Name`", new OreBlock($(F $Hardness), $lvl, $dropExpr, $($countRange[0]), $($countRange[1]), $($xpRange[0]), $($xpRange[1])));")
    New-BlockAssets $Name $title 'ore'

    $genFile = Join-Path $project.dir 'ModWorldGen.java'
    if (-not [IO.File]::Exists($genFile)) {
        Write-New $genFile @"
package $($project.package);

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import net.minecraft.block.Block;
import net.minecraft.block.state.pattern.BlockMatcher;
import net.minecraft.init.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.chunk.IChunkProvider;
import net.minecraft.world.gen.IChunkGenerator;
import net.minecraft.world.gen.feature.WorldGenMinable;
import net.minecraftforge.fml.common.IWorldGenerator;

/** Ore veins in newly generated chunks. Registered in preInit with GameRegistry.registerWorldGenerator. */
public class ModWorldGen implements IWorldGenerator {
    private static final class Ore {
        final Block block; final int dimension, veins, size, minY, maxY;
        Ore(Block block, int dimension, int veins, int size, int minY, int maxY) {
            this.block = block; this.dimension = dimension; this.veins = veins; this.size = size; this.minY = minY; this.maxY = maxY;
        }
    }
    private final List<Ore> ores = new ArrayList<>();

    public ModWorldGen() {
        // ore(block, dimension 0/-1/1, veins per chunk, blocks per vein, minY, maxY)
        // kit:ores
    }

    private void ore(Block block, int dimension, int veins, int size, int minY, int maxY) {
        ores.add(new Ore(block, dimension, veins, size, minY, maxY));
    }

    @Override
    public void generate(Random random, int chunkX, int chunkZ, World world, IChunkGenerator generator, IChunkProvider provider) {
        int dimension = world.provider.getDimension();
        for (Ore ore : ores) {
            if (ore.dimension != dimension) continue;
            Block host = dimension == -1 ? Blocks.NETHERRACK : dimension == 1 ? Blocks.END_STONE : Blocks.STONE;
            WorldGenMinable vein = new WorldGenMinable(ore.block.getDefaultState(), ore.size, BlockMatcher.forBlock(host));
            for (int i = 0; i < ore.veins; i++) {
                // +8 keeps veins inside already-generated neighbours (avoids cascading chunk generation).
                BlockPos pos = new BlockPos(chunkX * 16 + 8 + random.nextInt(16), ore.minY + random.nextInt(ore.maxY - ore.minY + 1), chunkZ * 16 + 8 + random.nextInt(16));
                vein.generate(world, random, pos);
            }
        }
    }
}
"@
        Add-AtMarker $project.mainFile 'preinit' @('GameRegistry.registerWorldGenerator(new ModWorldGen(), 0);')
        Add-Import $project.mainFile @('net.minecraftforge.fml.common.registry.GameRegistry')
    }
    $heights = Get-Range $Height 5 48 '-Height'
    if ($heights[1] -gt 255) { throw '-Height max is 255' }
    Add-AtMarker $genFile 'ores' @("ore(ModBlocks.$const, $Dimension, $Veins, $VeinSize, $($heights[0]), $($heights[1]));")
    $notes.Add("world generation: $Veins veins of $VeinSize in dimension $Dimension, y $($heights[0])-$($heights[1]); only new chunks get ore")
    $oreId = "$($project.modId):$Name"
    $dropId = if ($Drops) { ConvertTo-ItemId $Drops } else { $oreId }
    $min = if ($Drops) { $countRange[0] } else { 1 }; $max = if ($Drops) { $countRange[1] } else { 1 }
    Add-Check @(
        "{ List<ItemStack> d = t.drops($(Q $oreId), `"minecraft:diamond_pickaxe`"); int n = t.count(d, $(Q $dropId));",
        "  t.require(n >= $min && n <= $max, $(Q "$Name drops ") + d); }")
}

function Build-Food {
    $const = ConvertTo-Constant $Name
    $expr = "item(`"$Name`", new ItemFood($Hunger, $(F $Saturation), $(if ($Wolf) { 'true' } else { 'false' })))"
    if ($AlwaysEdible) { $expr += '.setAlwaysEdible()' }
    if ($Effect) {
        if ($Effect -notmatch '^([a-z_]+):(\d+)(?::(\d+))?(?::([0-9.]+))?$') { throw "-Effect must be name:ticks[:amplifier[:chance]], e.g. regeneration:100:1 (amplifier 0 = level I)" }
        $potion = $Matches[1].ToUpperInvariant(); $ticks = $Matches[2]; $amp = if ($Matches[3]) { $Matches[3] } else { '0' }; $chance = if ($Matches[4]) { [double]$Matches[4] } else { 1.0 }
        $expr += ".setPotionEffect(new PotionEffect(MobEffects.$potion, $ticks, $amp), $(F $chance))"
        Add-Import $project.items @('net.minecraft.init.MobEffects', 'net.minecraft.potion.PotionEffect')
    }
    Add-Import $project.items @('net.minecraft.item.ItemFood')
    Add-AtMarker $project.items 'items' @("public static final Item $const = $expr;")
    New-ItemAssets $Name $title 'food'
    $id = "$($project.modId):$Name"
    Add-Check @("t.require(((net.minecraft.item.ItemFood) t.item($(Q $id))).getHealAmount(t.stack($(Q $id), 1)) == $Hunger, $(Q "$Name hunger"));")
}

function Build-Tools {
    $const = ConvertTo-Constant $Name
    $pieces = Get-Pieces @('sword', 'pickaxe', 'axe', 'shovel', 'hoe')
    if ($Recipes -and -not $Repair) { throw '-Recipes needs -Repair <material item> (the ingot/gem the tools are made of).' }
    Assert-ItemRef $Repair 'Repair item'
    if ($Recipes) { Assert-ItemRef $Handle 'Handle' }
    $toolFile = Join-Path $project.dir 'ToolItems.java'
    if (($pieces -contains 'pickaxe' -or $pieces -contains 'axe') -and -not [IO.File]::Exists($toolFile)) {
        Write-New $toolFile @"
package $($project.package);

import net.minecraft.item.ItemAxe;
import net.minecraft.item.ItemPickaxe;

/** ItemPickaxe and ItemAxe constructors are protected in 1.12; these public subclasses expose them. */
public final class ToolItems {
    public static class Pickaxe extends ItemPickaxe {
        public Pickaxe(ToolMaterial material) { super(material); }
    }

    /** Axes need explicit damage/speed: the one-argument constructor only works for vanilla materials. */
    public static class Axe extends ItemAxe {
        public Axe(ToolMaterial material, float damage, float speed) { super(material, damage, speed); }
    }

    private ToolItems() {}
}
"@
    }
    # Start from -Like (default iron); -Durability may name another material; explicit numbers win.
    $base = Get-Preset $toolPresets $(if ($Like) { $Like } else { 'iron' }) 'tool material'
    $lvl = if ($Level -ge 0) { $Level } else { $base.level }
    $uses = $base.uses
    if ($Durability) { $uses = if ($Durability -match '^\d+$') { [int]$Durability } else { (Get-Preset $toolPresets $Durability 'tool material').uses } }
    $spd = if ($bound.ContainsKey('Speed')) { $Speed } else { $base.speed }
    $dmg = if ($bound.ContainsKey('Damage')) { $Damage } else { $base.damage }
    $ench = if ($bound.ContainsKey('Enchant')) { $Enchant } else { $base.enchant }
    # Vanilla axes by harvest level when no -Like: 0 -> 6/-3.2, 1 -> 8/-3.2, 2 -> 8/-3.1, 3+ -> 8/-3.0.
    $axeBase = if ($Like) { $base } else { $toolPresets[@('wood', 'stone', 'iron', 'diamond')[[Math]::Min(3, $lvl)]] }
    $axeDmg = if ($bound.ContainsKey('AxeDamage')) { $AxeDamage } else { $axeBase.axeDamage }
    $axeSpd = if ($bound.ContainsKey('AxeSpeed')) { $AxeSpeed } else { $axeBase.axeSpeed }
    if ($pieces -notcontains 'axe' -and ($bound.ContainsKey('AxeDamage') -or $bound.ContainsKey('AxeSpeed'))) { throw '-AxeDamage/-AxeSpeed need an axe; add axe to -Only.' }
    $mat = "${const}_TOOLS"
    $classes = [ordered]@{ sword = 'net.minecraft.item.ItemSword'; shovel = 'net.minecraft.item.ItemSpade'; hoe = 'net.minecraft.item.ItemHoe' }
    Add-Import $project.items (@($classes.Keys | Where-Object { $pieces -contains $_ } | ForEach-Object { $classes[$_] }) + 'net.minecraftforge.common.util.EnumHelper')
    $makers = @{
        sword = "new ItemSword($mat)"; pickaxe = "new ToolItems.Pickaxe($mat)"; axe = "new ToolItems.Axe($mat, $(F $axeDmg), $(F $axeSpd))"
        shovel = "new ItemSpade($mat)"; hoe = "new ItemHoe($mat)"
    }
    $axeDoc = if ($pieces -contains 'axe') { "; axe $axeDmg damage, $axeSpd speed" } else { '' }
    Add-AtMarker $project.items 'items' (@(
        "/** Tool material: harvest level $lvl, $uses uses, speed $spd, damage bonus $dmg, enchantability $ench$axeDoc. */",
        "public static final Item.ToolMaterial $mat = EnumHelper.addToolMaterial(`"${Name}`", $lvl, $uses, $(F $spd), $(F $dmg), $ench);") +
        @($pieces | ForEach-Object { "public static final Item ${const}_$(ConvertTo-Constant $_) = item(`"${Name}_$_`", $($makers[$_]));" }))
    foreach ($piece in $pieces) { New-ItemAssets "${Name}_$piece" ("$title " + (ConvertTo-Title $piece)) $piece 'item/handheld' $Name }
    if ($Repair) {
        Add-Import $project.mainFile @('net.minecraft.item.Item', 'net.minecraft.item.ItemStack')
        Add-AtMarker $project.mainFile 'init' @("ModItems.$mat.setRepairItem(new ItemStack($(ConvertTo-ItemExpr $Repair)));")
    }
    $ns = $project.modId
    $first = "${ns}:${Name}_$($pieces[0])"
    $checks = @("t.require(t.item($(Q $first)).getMaxDamage() == $uses, $(Q "${Name} tool durability"));")
    $digger = @($pieces | Where-Object { $_ -in @('pickaxe', 'axe', 'shovel') }) | Select-Object -First 1
    if ($digger) { $checks += "t.require(t.item($(Q "${ns}:${Name}_$digger")).getHarvestLevel(t.stack($(Q "${ns}:${Name}_$digger"), 1), $(Q $digger), null, null) == $lvl, $(Q "${Name} $digger level"));" }
    if ($pieces -contains 'sword') { $checks += "t.require(Math.abs(((net.minecraft.item.ItemSword) t.item($(Q "${ns}:${Name}_sword"))).getAttackDamage() - $(F $dmg)) < 0.001F, $(Q "${Name} sword damage bonus"));" }
    Add-Check $checks
    # Echo the values actually written so a wrong number is visible without opening ModItems.java.
    $stats = "stats: level $lvl, $uses uses, mining speed $spd, damage bonus $dmg, enchantability $ench"
    if ($pieces -contains 'sword') { $stats += "; sword tooltip $(4 + $dmg) attack damage" }
    if ($pieces -contains 'axe') { $stats += "; axe tooltip $(1 + $axeDmg) attack damage, speed $axeSpd" }
    $notes.Add($stats)
    if ($Recipes) {
        $shapes = [ordered]@{ sword = @('X', 'X', '#'); pickaxe = @('XXX', ' # ', ' # '); axe = @('XX', 'X#', ' #'); shovel = @('X', '#', '#'); hoe = @('XX', ' #', ' #') }
        foreach ($piece in $pieces) { Write-ShapedRecipe "${Name}_$piece" $shapes[$piece] ([ordered]@{ X = $Repair; '#' = $Handle }) "${Name}_$piece" 1 }
    } else { $notes.Add('no crafting recipes generated; add -Recipes (vanilla tool shapes) or use agent.ps1 new recipe') }
}

function Build-Armor {
    $const = ConvertTo-Constant $Name
    $pieces = Get-Pieces @('helmet', 'chestplate', 'leggings', 'boots')
    $base = Get-Preset $armorPresets $(if ($Like) { $Like } else { 'iron' }) 'armor material'
    $factor = $base.durability
    if ($Durability) { $factor = if ($Durability -match '^\d+$') { [int]$Durability } else { (Get-Preset $armorPresets $Durability 'armor material').durability } }
    $prot = if ($Protection) { $Protection } else { $base.protection }
    $values = @($prot.Split(',') | ForEach-Object { [int]$_.Trim() })
    if ($values.Count -ne 4) { throw '-Protection takes helmet,chestplate,leggings,boots, e.g. 3,8,6,3 (diamond)' }
    $ench = if ($bound.ContainsKey('Enchant')) { $Enchant } else { $base.enchant }
    $tough = if ($bound.ContainsKey('Toughness')) { $Toughness } else { $base.toughness }
    if ($Recipes -and -not $Repair) { throw '-Recipes needs -Repair <material item> (the ingot/gem the armor is made of).' }
    Assert-ItemRef $Repair 'Repair item'
    $mat = "${const}_ARMOR"
    Add-Import $project.items @('net.minecraft.init.SoundEvents', 'net.minecraft.inventory.EntityEquipmentSlot', 'net.minecraft.item.ItemArmor', 'net.minecraftforge.common.util.EnumHelper')
    $slots = @{ helmet = 'HEAD'; chestplate = 'CHEST'; leggings = 'LEGS'; boots = 'FEET' }
    # ArmorMaterial reductions are indexed boots, leggings, chestplate, helmet.
    Add-AtMarker $project.items 'items' (@(
        "/** Armor material: durability factor $factor, protection helmet/chest/legs/boots $($values -join ','), toughness $tough, enchantability $ench. Texture: textures/models/armor/${Name}_layer_1/2.png */",
        "public static final ItemArmor.ArmorMaterial $mat = EnumHelper.addArmorMaterial(`"$Name`", $($project.mainClass).MODID + `":$Name`", $factor, new int[] {$($values[3]), $($values[2]), $($values[1]), $($values[0])}, $ench, SoundEvents.ITEM_ARMOR_EQUIP_$($base.sound), $(F $tough));") +
        @($pieces | ForEach-Object { "public static final Item ${const}_$(ConvertTo-Constant $_) = item(`"${Name}_$_`", new ItemArmor($mat, 0, EntityEquipmentSlot.$($slots[$_])));" }))
    foreach ($piece in $pieces) { New-ItemAssets "${Name}_$piece" ("$title " + (ConvertTo-Title $piece)) $piece 'item/generated' $Name }
    # Layer 1: helmet, chestplate, boots; layer 2: leggings.
    if (@($pieces | Where-Object { $_ -ne 'leggings' }).Count) { New-Texture (Join-Path $project.assets "textures/models/armor/${Name}_layer_1.png") 'armor' $Name }
    if ($pieces -contains 'leggings') { New-Texture (Join-Path $project.assets "textures/models/armor/${Name}_layer_2.png") 'armor' $Name }
    if ($Repair) {
        Add-Import $project.mainFile @('net.minecraft.item.Item', 'net.minecraft.item.ItemStack')
        Add-AtMarker $project.mainFile 'init' @("ModItems.$mat.setRepairItem(new ItemStack($(ConvertTo-ItemExpr $Repair)));")
    }
    $ns = $project.modId
    # Durability = factor x vanilla base (helmet 11, chestplate 16, leggings 15, boots 13).
    $info = @{ helmet = @($values[0], 11); chestplate = @($values[1], 16); leggings = @($values[2], 15); boots = @($values[3], 13) }
    $checks = foreach ($p in $pieces) {
        "t.require(((net.minecraft.item.ItemArmor) t.item($(Q "${ns}:${Name}_$p"))).damageReduceAmount == $($info[$p][0]) && t.item($(Q "${ns}:${Name}_$p")).getMaxDamage() == $($info[$p][1] * $factor), $(Q "${Name}_$p protection/durability"));"
    }
    Add-Check @($checks)
    $total = 0; foreach ($p in $pieces) { $total += $info[$p][0] }
    $stats = "stats: " + (@($pieces | ForEach-Object { "$_ $($info[$_][0]) armor/$($info[$_][1] * $factor) uses" }) -join ', ') + "; toughness $tough, enchantability $ench"
    if ($total -gt 20) { $stats += "; total armor $total, but damage reduction stops counting at 20 (full diamond = 20)" }
    $notes.Add($stats)
    if ($Recipes) {
        $shapes = [ordered]@{ helmet = @('XXX', 'X X'); chestplate = @('X X', 'XXX', 'XXX'); leggings = @('XXX', 'X X', 'X X'); boots = @('X X', 'X X') }
        foreach ($piece in $pieces) { Write-ShapedRecipe "${Name}_$piece" $shapes[$piece] ([ordered]@{ X = $Repair }) "${Name}_$piece" 1 }
    } else { $notes.Add('no crafting recipes generated; add -Recipes (vanilla armor shapes) or use agent.ps1 new recipe') }
}

function Build-Recipe {
    if (-not $Result) { throw '-Result <item> is required' }
    $countRange = Get-Range $Count 1 1 '-Count'
    if ($Shape) {
        $rows = @($Shape.Split(','))
        if ($rows.Count -gt 3 -or @($rows | Where-Object { $_.Length -gt 3 -or $_.Length -eq 0 }).Count) { throw '-Shape is up to 3 comma-separated rows of up to 3 characters, e.g. "###,#X#,###" (space = empty)' }
        $map = [ordered]@{}
        foreach ($part in @($Key -split ',')) { if (-not $part) { continue }; if ($part -notmatch '^(.)=(.+)$') { throw "-Key entries look like X=item, got '$part'" }; $map[$Matches[1]] = $Matches[2] }
        $symbols = @(($rows -join '').ToCharArray() | Where-Object { $_ -ne ' ' } | ForEach-Object { [string]$_ } | Select-Object -Unique)
        foreach ($s in $symbols) { if (-not $map.Contains($s)) { throw "Shape symbol '$s' has no -Key $s=<item>" } }
        foreach ($k in @($map.Keys)) { if ($k -notin $symbols) { throw "-Key '$k' is not used in -Shape" } }
        Write-ShapedRecipe $Name $rows $map $Result $countRange[0]
    } elseif ($Ingredients) {
        $items = @($Ingredients -split ',' | Where-Object { $_ })
        if ($items.Count -lt 1 -or $items.Count -gt 9) { throw '-Ingredients takes 1 to 9 items' }
        foreach ($i in $items) { Assert-ItemRef $i 'Ingredient' }
        Assert-ItemRef $Result 'Result'
        $resultId = ConvertTo-ItemId $Result
        $resultJson = '{ "item": "' + $resultId + '"' + $(if ($Result -match '@(\d+)$') { ', "data": ' + $Matches[1] } else { '' }) + $(if ($countRange[0] -gt 1) { ', "count": ' + $countRange[0] } else { '' }) + ' }'
        Write-New (Join-Path $project.assets "recipes/$Name.json") ("{`n  `"type`": `"minecraft:crafting_shapeless`",`n  `"ingredients`": [ " + (($items | ForEach-Object { ConvertTo-Ingredient $_ }) -join ', ') + " ],`n  `"result`": $resultJson`n}`n")
        $script:recipeCount++
        Add-Check @(
            "{ ItemStack r = t.craft($((($items | ForEach-Object { Q (ConvertTo-TestRef $_) }) -join ', ')));",
            "  t.require(r.getItem() == t.item($(Q $resultId)) && r.getCount() == $($countRange[0]), $(Q "recipe $Name gave ") + r); }")
    } else { throw 'Give -Shape with -Key (shaped) or -Ingredients (shapeless).' }
}

function Build-Smelting {
    if (-not $From -or -not $Result) { throw 'Usage: agent.ps1 new smelting <name> -From <item> -Result <item> [-Count n] [-Xp 0.7]' }
    Assert-ItemRef $From 'Input'; Assert-ItemRef $Result 'Result'
    $countRange = Get-Range $Count 1 1 '-Count'
    $xpValue = if ($Xp) { [double]$Xp } else { 0.7 }
    Add-Import $project.mainFile @('net.minecraft.item.Item', 'net.minecraft.item.ItemStack', 'net.minecraftforge.fml.common.registry.GameRegistry')
    Add-AtMarker $project.mainFile 'init' @("GameRegistry.addSmelting(new ItemStack($(ConvertTo-ItemExpr $From)), new ItemStack($(ConvertTo-ItemExpr $Result), $($countRange[0])), $(F $xpValue)); // smelting: $Name")
    Add-Check @(
        "{ ItemStack r = t.smelt($(Q (ConvertTo-TestRef $From)));",
        "  t.require(r.getItem() == t.item($(Q (ConvertTo-ItemId $Result))) && r.getCount() == $($countRange[0]), $(Q "smelting $Name gave ") + r); }")
}

function Build-Tile {
    $const = ConvertTo-Constant $Name
    $pascal = ConvertTo-Pascal $Name
    $hasInv = $Inventory -gt 0
    $tickImports = if ($Ticking) { "import net.minecraft.util.ITickable;`n" } else { '' }
    $invImports = if ($hasInv) { "import net.minecraft.util.EnumFacing;`nimport net.minecraftforge.common.capabilities.Capability;`nimport net.minecraftforge.items.CapabilityItemHandler;`nimport net.minecraftforge.items.ItemStackHandler;`n" } else { '' }
    $implements = if ($Ticking) { ' implements ITickable' } else { '' }
    $invField = if ($hasInv) { @"

    /** $Inventory slots, exposed to hoppers/pipes on every side. */
    public final ItemStackHandler inventory = new ItemStackHandler($Inventory) {
        @Override protected void onContentsChanged(int slot) { markDirty(); }
    };
"@ } else { '' }
    $invNbt = if ($hasInv) { '        tag.setTag("inventory", inventory.serializeNBT());' } else { '' }
    $invRead = if ($hasInv) { '        inventory.deserializeNBT(tag.getCompoundTag("inventory"));' } else { '' }
    $invCaps = if ($hasInv) { @"

    @Override
    public boolean hasCapability(Capability<?> capability, EnumFacing facing) {
        return capability == CapabilityItemHandler.ITEM_HANDLER_CAPABILITY || super.hasCapability(capability, facing);
    }

    @Override
    public <T> T getCapability(Capability<T> capability, EnumFacing facing) {
        if (capability == CapabilityItemHandler.ITEM_HANDLER_CAPABILITY) return CapabilityItemHandler.ITEM_HANDLER_CAPABILITY.cast(inventory);
        return super.getCapability(capability, facing);
    }
"@ } else { '' }
    $tick = if ($Ticking) { @"

    /** Runs every tick on both sides; game logic belongs on the server. */
    @Override
    public void update() {
        if (world.isRemote) return;
        ticks++;
        // TODO behaviour; call markDirty() after changing saved state, sync() if clients must see it.
    }
"@ } else { '' }
    Write-New (Join-Path $project.dir "${pascal}Tile.java") @"
package $($project.package);

import javax.annotation.Nullable;
import net.minecraft.block.state.IBlockState;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.play.server.SPacketUpdateTileEntity;
import net.minecraft.tileentity.TileEntity;
$tickImports$invImports
/** Block entity for $Name. Saved state: writeToNBT/readFromNBT. Client copy: getUpdateTag/onDataPacket + sync(). */
public class ${pascal}Tile extends TileEntity$implements {
    public int ticks;
$invField
    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound tag) {
        super.writeToNBT(tag);
        tag.setInteger("ticks", ticks);
$invNbt
        return tag;
    }

    @Override
    public void readFromNBT(NBTTagCompound tag) {
        super.readFromNBT(tag);
        ticks = tag.getInteger("ticks");
$invRead
    }
$tick$invCaps
    /** Sends this tile's NBT to clients watching it. */
    public void sync() {
        if (world != null && !world.isRemote) {
            IBlockState state = world.getBlockState(pos);
            world.notifyBlockUpdate(pos, state, state, 3);
        }
    }

    @Override
    public NBTTagCompound getUpdateTag() { return writeToNBT(new NBTTagCompound()); }

    @Nullable
    @Override
    public SPacketUpdateTileEntity getUpdatePacket() { return new SPacketUpdateTileEntity(pos, 0, getUpdateTag()); }

    @Override
    public void onDataPacket(NetworkManager net, SPacketUpdateTileEntity packet) { readFromNBT(packet.getNbtCompound()); }
}
"@
    $dropCode = if ($hasInv) { @"

    /** Drops the inventory when the block is broken. */
    @Override
    public void breakBlock(World world, BlockPos pos, IBlockState state) {
        TileEntity tile = world.getTileEntity(pos);
        if (tile instanceof ${pascal}Tile) {
            ItemStackHandler inventory = ((${pascal}Tile) tile).inventory;
            for (int slot = 0; slot < inventory.getSlots(); slot++) InventoryHelper.spawnItemStack(world, pos.getX(), pos.getY(), pos.getZ(), inventory.getStackInSlot(slot));
        }
        super.breakBlock(world, pos, state);
    }
"@ } else { '' }
    $dropImports = if ($hasInv) { "import net.minecraft.inventory.InventoryHelper;`nimport net.minecraftforge.items.ItemStackHandler;`n" } else { '' }
    Write-New (Join-Path $project.dir "${pascal}Block.java") @"
package $($project.package);

import net.minecraft.block.SoundType;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.world.World;
$dropImports
public class ${pascal}Block extends BasicBlock {
    public ${pascal}Block() {
        super(Material.IRON, SoundType.METAL, 3.0F, 5.0F);
        harvest("pickaxe", 0);
    }

    @Override
    public boolean hasTileEntity(IBlockState state) { return true; }

    @Override
    public TileEntity createTileEntity(World world, IBlockState state) { return new ${pascal}Tile(); }

    /** Right-click. Runs on both sides: return true on both, act on the server only. */
    @Override
    public boolean onBlockActivated(World world, BlockPos pos, IBlockState state, EntityPlayer player, EnumHand hand, EnumFacing facing, float hitX, float hitY, float hitZ) {
        if (!world.isRemote) {
            TileEntity tile = world.getTileEntity(pos);
            if (tile instanceof ${pascal}Tile) player.sendStatusMessage(new TextComponentString("ticks: " + ((${pascal}Tile) tile).ticks), true);
        }
        return true;
    }
$dropCode}
"@
    Add-AtMarker $project.blocks 'blocks' @("public static final Block $const = block(`"$Name`", new ${pascal}Block());")
    Add-AtMarker $project.blocks 'tiles' @("GameRegistry.registerTileEntity(${pascal}Tile.class, new ResourceLocation($($project.mainClass).MODID, `"$Name`"));")
    New-BlockAssets $Name $title 'block'
}

function Build-Test {
    if ($Name -notmatch '^[a-z][a-z0-9-]*$') { throw 'Test ids are lowercase with dashes, e.g. ruby-ore-drops' }
    $pascal = (($Name.Split('-') | ForEach-Object { $_.Substring(0, 1).ToUpperInvariant() + $_.Substring(1) }) -join '') + 'Test'
    Write-New (Join-Path $project.tests "$pascal.java") @"
package $($project.package);

import kit.gametest.GameTest;
import kit.gametest.Scenario;
import kit.gametest.TestContext;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.common.util.FakePlayer;

/** Run: agent.ps1 check game -Test $Name   (API: docs/TESTING.md) */
@GameTest("$Name")
public class $pascal implements Scenario {
    @Override
    public void plan(TestContext t) {
        BlockPos pos = t.origin().add(2, 0, 2);
        t.then("arrange", () -> {
            // t.place(pos, "$($project.modId):some_block");
            // FakePlayer player = t.player("tester");
            // player.setHeldItem(EnumHand.MAIN_HAND, t.stack("$($project.modId):some_item", 1));
        });
        t.waitTicks(1);
        // KIT-SCAFFOLD: replace this placeholder with the real expected outcome. It fails on purpose,
        // so an unfinished test can never report PASS.
        t.check("expected-outcome", () -> false, () -> "KIT-SCAFFOLD placeholder: write the real steps and checks in $pascal.java");
    }
}
"@
}

# ---- main --------------------------------------------------------------------------------

try {
    if (-not $Name) { throw "Usage: agent.ps1 new $Kind <name> [options]; see docs/GENERATORS.md" }
    if ($Kind -ne 'test' -and $Name -notmatch '^[a-z][a-z0-9_]*$') { throw "Names are lowercase registry paths like ruby_ore, got '$Name'" }
    $unused = @($bound.Keys | Where-Object { $commonOptions -notcontains $_ -and $kindOptions[$Kind] -notcontains $_ })
    if ($unused.Count) {
        $others = @($kindOptions.Keys | Where-Object { $kindOptions[$_] -contains $unused[0] } | Sort-Object)
        $where = if ($others.Count) { " (used by new $($others -join '/'))" } else { '' }
        throw "-$($unused -join ', -') does not apply to new $Kind$where. Options for ${Kind}: $(@($kindOptions[$Kind] | ForEach-Object { '-' + $_ }) -join ' ')"
    }
    $project = Get-Project
    $title = if ($Display) { $Display } else { ConvertTo-Title $Name }
    if ($Kind -in @('item', 'block', 'ore', 'food', 'tile')) { Assert-NewName $Name }
    if ($Kind -eq 'tools') { foreach ($piece in (Get-Pieces @('sword', 'pickaxe', 'axe', 'shovel', 'hoe'))) { Assert-NewName "${Name}_$piece" } }
    if ($Kind -eq 'armor') { foreach ($piece in (Get-Pieces @('helmet', 'chestplate', 'leggings', 'boots'))) { Assert-NewName "${Name}_$piece" } }
    switch ($Kind) {
        'item' { Build-Item }
        'block' { Build-Block }
        'ore' { Build-Ore }
        'food' { Build-Food }
        'tools' { Build-Tools }
        'armor' { Build-Armor }
        'recipe' { Build-Recipe }
        'smelting' { Build-Smelting }
        'tile' { Build-Tile }
        'test' { Build-Test }
    }
    if ($Json) { [ordered]@{ schemaVersion = 1; status = 'PASS'; kind = $Kind; name = $Name; registered = $registered; files = $created; notes = $notes } | ConvertTo-Json -Compress }
    else {
        # One summary line; -List shows every file.
        $java = @($created | Where-Object { $_ -like '*.java' -and $_ -notlike 'src/gametest/*' } | ForEach-Object { [IO.Path]::GetFileNameWithoutExtension($_) })
        $parts = @()
        if ($registered.Count) { $parts += ($registered -join ', ') }
        if ($java.Count) { $parts += ($java -join ', ') }
        $models = @($created | Where-Object { $_ -like '*/models/*.json' }).Count; $textures = @($created | Where-Object { $_ -like '*.png' }).Count
        $assets = @(); if ($models) { $assets += "$models model(s)" }; if ($textures) { $assets += "$textures texture(s)" }
        if (@($created | Where-Object { $_ -like '*/lang/*' }).Count) { $assets += 'lang' }
        if ($assets.Count) { $parts += ($assets -join ', ') }
        if ($script:recipeCount) { $parts += "$($script:recipeCount) recipe(s)" }
        if ($script:checkCount) { $parts += "$($script:checkCount) check(s) in GeneratedContentTest" }
        if ($Kind -eq 'test') { $parts += ($created -join ', ') }
        "created $Kind ${Name}: " + ($parts -join ' | ')
        if ($List) { foreach ($file in $created) { '  ' + $file } }
        foreach ($note in $notes) { 'note: ' + $note }
    }
} catch {
    if ($Json) { [ordered]@{ schemaVersion = 1; status = 'FAIL'; message = $_.Exception.Message; files = $created } | ConvertTo-Json -Compress }
    else { 'FAIL: ' + $_.Exception.Message; if ($created.Count) { 'partially written: ' + ($created -join ', ') } }
    exit 1
}
