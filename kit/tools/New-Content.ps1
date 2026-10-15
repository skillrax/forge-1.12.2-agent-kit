# Generates known-good content into a skeleton made by `agent.ps1 init`: Java declarations at
# // kit:<marker> lines, JSON models/blockstates/recipes, lang entries and placeholder textures.
# Usage: agent.ps1 new <kind> <name> [options]   (kinds and options: docs/GENERATORS.md)
[CmdletBinding()]
param(
    [Parameter(Mandatory)][ValidateSet('item', 'block', 'ore', 'food', 'tools', 'armor', 'recipe', 'smelting', 'tile', 'test')][string]$Kind,
    [Parameter(Position = 0)][string]$Name,
    [string]$Display,
    [string]$Tab,
    [int]$StackSize,
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
    [int]$Durability = -1,
    [double]$Speed = 6.0,
    [double]$Damage = 2.0,
    [int]$Enchant = 14,
    [string]$Protection = '3,8,6,3',
    [double]$Toughness = 0,
    [string]$Repair,
    [string]$Shape,
    [string[]]$Key,
    [string[]]$Ingredients,
    [string]$Result,
    [string]$From,
    [switch]$Ticking,
    [int]$Inventory = 0,
    [string]$Color,
    [switch]$Json
)
$ErrorActionPreference = 'Stop'
[Console]::OutputEncoding = New-Object Text.UTF8Encoding($false)
$root = Split-Path -Parent $PSScriptRoot
$utf8 = New-Object Text.UTF8Encoding($false)
$created = New-Object 'System.Collections.Generic.List[string]'
$notes = New-Object 'System.Collections.Generic.List[string]'

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
    }
}

# ---- file editing ------------------------------------------------------------------------

function Write-New([string]$Path, [string]$Text) {
    if ([IO.File]::Exists($Path)) { throw "$(Get-Rel $Path) already exists; refusing to overwrite." }
    New-Item -ItemType Directory -Force -Path (Split-Path -Parent $Path) | Out-Null
    [IO.File]::WriteAllText($Path, $Text.Replace("`r`n", "`n"), $utf8)
    $created.Add((Get-Rel $Path))
}
function Get-Rel([string]$Path) { $Path.Substring($root.Length).TrimStart('\', '/').Replace('\', '/') }

function Add-AtMarker([string]$File, [string]$Marker, [string[]]$Lines) {
    $text = [IO.File]::ReadAllText($File)
    $match = [regex]::Match($text, '(?m)^([ \t]*)// kit:' + [regex]::Escape($Marker) + '\b.*$')
    if (-not $match.Success) { throw "Marker '// kit:$Marker' not found in $(Get-Rel $File); restore it or add the code by hand." }
    $indent = $match.Groups[1].Value
    $block = ($Lines | ForEach-Object { if ($_) { $indent + $_ } else { '' } }) -join "`n"
    $text = $text.Insert($match.Index, $block + "`n")
    [IO.File]::WriteAllText($File, $text, $utf8)
    if (-not $created.Contains((Get-Rel $File))) { $created.Add((Get-Rel $File)) }
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

function Add-Lang([string]$Key, [string]$Value) {
    $path = Join-Path $project.assets 'lang/en_us.lang'
    $text = if ([IO.File]::Exists($path)) { [IO.File]::ReadAllText($path) } else { '' }
    if ($text -match ('(?m)^' + [regex]::Escape($Key) + '=')) { return }
    if ($text.Length -and -not $text.EndsWith("`n")) { $text += "`n" }
    [IO.File]::WriteAllText($path, $text + "$Key=$Value`n", $utf8)
    if (-not $created.Contains((Get-Rel $path))) { $created.Add((Get-Rel $path)) }
}

function Assert-NewName([string]$RegistryName) {
    foreach ($file in @($project.items, $project.blocks)) {
        if ([IO.File]::ReadAllText($file) -match ('\("' + [regex]::Escape($RegistryName) + '"')) { throw "'$RegistryName' is already declared in $(Get-Rel $file)." }
    }
}

function ConvertTo-Constant([string]$Text) { $Text.ToUpperInvariant() }
function ConvertTo-Pascal([string]$Text) { ($Text.Split('_') | Where-Object { $_ } | ForEach-Object { $_.Substring(0, 1).ToUpperInvariant() + $_.Substring(1) }) -join '' }
function ConvertTo-Title([string]$Text) { ($Text.Split('_') | Where-Object { $_ } | ForEach-Object { $_.Substring(0, 1).ToUpperInvariant() + $_.Substring(1) }) -join ' ' }
function ConvertTo-JavaString([string]$Text) { '"' + $Text.Replace('\', '\\').Replace('"', '\"') + '"' }

# "ruby" -> "<modid>:ruby"; "minecraft:diamond", "minecraft:dye@4" kept.
function ConvertTo-ItemId([string]$Ref) {
    if (-not $Ref) { return $null }
    $id = $Ref.Split('@')[0]
    if ($id -notmatch ':') { $id = $project.modId + ':' + $id }
    if ($id -notmatch '^[a-z0-9_.-]+:[a-z0-9_/.-]+$') { throw "Not an item id: '$Ref' (use name, modid:name or minecraft:name)" }
    return $id
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

Add-Type -AssemblyName System.Drawing
function Get-BaseColor([string]$Seed) {
    if ($Color) { return [Drawing.ColorTranslator]::FromHtml('#' + $Color.TrimStart('#')) }
    $hash = 0; foreach ($c in $Seed.ToCharArray()) { $hash = ($hash * 31 + [int]$c) % 360 }
    # HSV(hash, 0.65, 0.85) -> RGB
    $h = $hash / 60.0; $s = 0.65; $v = 0.85; $i = [Math]::Floor($h); $f = $h - $i
    $p = $v * (1 - $s); $q = $v * (1 - $s * $f); $t = $v * (1 - $s * (1 - $f))
    $rgb = switch ([int]$i % 6) { 0 { $v, $t, $p } 1 { $q, $v, $p } 2 { $p, $v, $t } 3 { $p, $q, $v } 4 { $t, $p, $v } default { $v, $p, $q } }
    return [Drawing.Color]::FromArgb([int]($rgb[0] * 255), [int]($rgb[1] * 255), [int]($rgb[2] * 255))
}
function Get-Shade([Drawing.Color]$C, [double]$Factor, [int]$Alpha = 255) {
    $clamp = { param($x) [Math]::Max(0, [Math]::Min(255, [int]$x)) }
    [Drawing.Color]::FromArgb($Alpha, (& $clamp ($C.R * $Factor)), (& $clamp ($C.G * $Factor)), (& $clamp ($C.B * $Factor)))
}
# Simple, recognisable 16x16 (or armor 64x32) placeholders; replace with real art any time.
function New-Texture([string]$Path, [string]$Style, [string]$Seed) {
    if ([IO.File]::Exists($Path)) { return }
    $base = Get-BaseColor $Seed
    $rand = New-Object Random ($Seed.GetHashCode())
    $w = 16; $h = 16; if ($Style -eq 'armor') { $w = 64; $h = 32 }
    $bmp = New-Object Drawing.Bitmap $w, $h, ([Drawing.Imaging.PixelFormat]::Format32bppArgb)
    for ($x = 0; $x -lt $w; $x++) {
        for ($y = 0; $y -lt $h; $y++) {
            $pixel = [Drawing.Color]::Transparent
            switch ($Style) {
                'block' { $pixel = Get-Shade $base (0.9 + $rand.NextDouble() * 0.2); if ($x -in 0, 15 -or $y -in 0, 15) { $pixel = Get-Shade $base 0.7 } }
                'ore' { $g = 110 + $rand.Next(30); $pixel = [Drawing.Color]::FromArgb(255, $g, $g, $g) }
                'armor' { $pixel = Get-Shade $base (0.85 + $rand.NextDouble() * 0.2) }
                'item' { $d = [Math]::Abs($x - 7.5) + [Math]::Abs($y - 7.5); if ($d -le 6.5) { $pixel = Get-Shade $base $(if ($x + $y -lt 13) { 1.15 } else { 0.85 }) }; if ($d -gt 5.5 -and $d -le 6.5) { $pixel = Get-Shade $base 0.6 } }
                'food' { $d = [Math]::Sqrt([Math]::Pow($x - 7.5, 2) + [Math]::Pow($y - 8.5, 2)); if ($d -le 6) { $pixel = Get-Shade $base $(if ($d -gt 5) { 0.65 } elseif ($x -lt 7 -and $y -lt 8) { 1.2 } else { 1.0 }) }; if ($x -eq 8 -and $y -in 1, 2) { $pixel = [Drawing.Color]::FromArgb(255, 90, 60, 30) } }
                'tool' {
                    if ($x + $y -eq 15 -and $x -le 7) { $pixel = [Drawing.Color]::FromArgb(255, 110, 80, 45) }
                    if ($x + $y -in 15, 16 -and $x -gt 7) { $pixel = Get-Shade $base 1.0 }
                    if ($x -ge 9 -and $y -le 6 -and [Math]::Abs(($x - 9) - (6 - $y)) -le 1) { $pixel = Get-Shade $base 1.15 }
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
    New-Item -ItemType Directory -Force -Path (Split-Path -Parent $Path) | Out-Null
    $bmp.Save($Path, [Drawing.Imaging.ImageFormat]::Png)
    $bmp.Dispose()
    $created.Add((Get-Rel $Path))
}

# ---- shared asset writers ----------------------------------------------------------------

function New-ItemAssets([string]$ItemName, [string]$Title, [string]$Style, [string]$Parent = 'item/generated', [string]$Seed = $ItemName) {
    Write-New (Join-Path $project.assets "models/item/$ItemName.json") ('{ "parent": "' + $Parent + '", "textures": { "layer0": "' + $project.modId + ':items/' + $ItemName + '" } }' + "`n")
    New-Texture (Join-Path $project.assets "textures/items/$ItemName.png") $Style $Seed
    Add-Lang "item.$($project.modId).$ItemName.name" $Title
}
function New-BlockAssets([string]$BlockName, [string]$Title, [string]$Style) {
    $id = $project.modId + ':' + $BlockName
    Write-New (Join-Path $project.assets "blockstates/$BlockName.json") ('{ "variants": { "normal": { "model": "' + $id + '" } } }' + "`n")
    Write-New (Join-Path $project.assets "models/block/$BlockName.json") ('{ "parent": "block/cube_all", "textures": { "all": "' + $project.modId + ':blocks/' + $BlockName + '" } }' + "`n")
    Write-New (Join-Path $project.assets "models/item/$BlockName.json") ('{ "parent": "' + $project.modId + ':block/' + $BlockName + '" }' + "`n")
    New-Texture (Join-Path $project.assets "textures/blocks/$BlockName.png") $Style $BlockName
    Add-Lang "tile.$($project.modId).$BlockName.name" $Title
}

function Get-Range([string]$Text, [int]$DefaultMin, [int]$DefaultMax, [string]$What) {
    if (-not $Text) { return @($DefaultMin, $DefaultMax) }
    if ($Text -match '^(\d+)$') { return @([int]$Matches[1], [int]$Matches[1]) }
    if ($Text -match '^(\d+)-(\d+)$' -and [int]$Matches[1] -le [int]$Matches[2]) { return @([int]$Matches[1], [int]$Matches[2]) }
    throw "$What must be N or MIN-MAX, got '$Text'"
}
function F([double]$Value) { $Value.ToString('0.0###', [Globalization.CultureInfo]::InvariantCulture) + 'F' }

$sounds = @{ ROCK = 'STONE'; IRON = 'METAL'; WOOD = 'WOOD'; GROUND = 'GROUND'; GRASS = 'PLANT'; SAND = 'SAND'; GLASS = 'GLASS'; CLOTH = 'CLOTH'; PLANTS = 'PLANT'; LEAVES = 'PLANT'; ICE = 'GLASS'; PACKED_ICE = 'GLASS'; CLAY = 'GROUND'; SNOW = 'SNOW'; CRAFTED_SNOW = 'SNOW'; ANVIL = 'ANVIL'; REDSTONE_LIGHT = 'GLASS'; GOURD = 'WOOD'; CACTUS = 'CLOTH'; SPONGE = 'PLANT'; CORAL = 'STONE'; CIRCUITS = 'STONE'; CARPET = 'CLOTH' }
$tools = @{ ROCK = 'pickaxe'; IRON = 'pickaxe'; ANVIL = 'pickaxe'; WOOD = 'axe'; GOURD = 'axe'; GROUND = 'shovel'; GRASS = 'shovel'; SAND = 'shovel'; CLAY = 'shovel'; SNOW = 'shovel'; CRAFTED_SNOW = 'shovel' }

# ---- kinds -------------------------------------------------------------------------------

function Build-Item {
    $const = ConvertTo-Constant $Name
    $tab = if ($Tab) { $Tab.ToUpperInvariant() } else { 'MATERIALS' }
    $expr = "item(`"$Name`", new Item()).setCreativeTab(CreativeTabs.$tab)"
    if ($StackSize) { $expr += ".setMaxStackSize($StackSize)" }
    Add-AtMarker $project.items 'items' @("public static final Item $const = $expr;")
    New-ItemAssets $Name $title 'item'
}

function Build-Block {
    $const = ConvertTo-Constant $Name
    $mat = $Material.ToUpperInvariant()
    if (-not $sounds.ContainsKey($mat)) { throw "Unknown -Material $Material. Use one of: $($sounds.Keys -join ', ')" }
    $res = if ($Resistance -ge 0) { $Resistance } else { $Hardness }
    $expr = "new BasicBlock(Material.$mat, SoundType.$($sounds[$mat]), $(F $Hardness), $(F $res))"
    $harvestTool = if ($Tool) { $Tool } else { $tools[$mat] }
    if ($harvestTool) { $expr += ".harvest(`"$harvestTool`", $([Math]::Max(0, $Level)))" }
    if ($Light -gt 0) { $expr += ".light($(F $Light))" }
    Add-AtMarker $project.blocks 'blocks' @("public static final Block $const = block(`"$Name`", $expr);")
    New-BlockAssets $Name $title 'block'
}

function Build-Ore {
    $const = ConvertTo-Constant $Name
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
    $count = Get-Range $Count 1 1 '-Count'
    $xp = if ($Drops) { Get-Range $Xp 3 7 '-Xp' } else { @(0, 0) }
    $dropExpr = if ($Drops) { '() -> ' + (ConvertTo-ItemExpr $Drops) } else { 'null' }
    $lvl = if ($Level -ge 0) { $Level } else { 1 }
    Add-AtMarker $project.blocks 'blocks' @("public static final Block $const = block(`"$Name`", new OreBlock($(F $Hardness), $lvl, $dropExpr, $($count[0]), $($count[1]), $($xp[0]), $($xp[1])));")
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
}

function Build-Tools {
    $const = ConvertTo-Constant $Name
    $toolFile = Join-Path $project.dir 'ToolItems.java'
    if (-not [IO.File]::Exists($toolFile)) {
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
    $lvl = if ($Level -ge 0) { $Level } else { 2 }
    $uses = if ($Durability -ge 0) { $Durability } else { 500 }
    $axeDamage = [Math]::Max(6.0, 5.0 + $Damage)
    $mat = "${const}_TOOLS"
    Add-Import $project.items @('net.minecraft.item.ItemHoe', 'net.minecraft.item.ItemSpade', 'net.minecraft.item.ItemSword', 'net.minecraftforge.common.util.EnumHelper')
    Add-AtMarker $project.items 'items' @(
        "/** Tool material: harvest level $lvl, $uses uses, speed $Speed, damage bonus $Damage, enchantability $Enchant. */",
        "public static final Item.ToolMaterial $mat = EnumHelper.addToolMaterial(`"${Name}`", $lvl, $uses, $(F $Speed), $(F $Damage), $Enchant);",
        "public static final Item ${const}_SWORD = item(`"${Name}_sword`", new ItemSword($mat));",
        "public static final Item ${const}_PICKAXE = item(`"${Name}_pickaxe`", new ToolItems.Pickaxe($mat));",
        "public static final Item ${const}_AXE = item(`"${Name}_axe`", new ToolItems.Axe($mat, $(F $axeDamage), -3.0F));",
        "public static final Item ${const}_SHOVEL = item(`"${Name}_shovel`", new ItemSpade($mat));",
        "public static final Item ${const}_HOE = item(`"${Name}_hoe`", new ItemHoe($mat));")
    foreach ($piece in @('sword', 'pickaxe', 'axe', 'shovel', 'hoe')) { New-ItemAssets "${Name}_$piece" ("$title " + (ConvertTo-Title $piece)) 'tool' 'item/handheld' $Name }
    if ($Repair) {
        Add-Import $project.mainFile @('net.minecraft.item.Item', 'net.minecraft.item.ItemStack')
        Add-AtMarker $project.mainFile 'init' @("ModItems.$mat.setRepairItem(new ItemStack($(ConvertTo-ItemExpr $Repair)));")
    }
    $notes.Add('no crafting recipes generated; add them with agent.ps1 new recipe')
}

function Build-Armor {
    $const = ConvertTo-Constant $Name
    $values = @($Protection.Split(',') | ForEach-Object { [int]$_.Trim() })
    if ($values.Count -ne 4) { throw '-Protection takes helmet,chestplate,leggings,boots, e.g. 3,8,6,3 (diamond)' }
    $durability = if ($Durability -ge 0) { $Durability } else { 15 }
    $mat = "${const}_ARMOR"
    Add-Import $project.items @('net.minecraft.init.SoundEvents', 'net.minecraft.inventory.EntityEquipmentSlot', 'net.minecraft.item.ItemArmor', 'net.minecraftforge.common.util.EnumHelper')
    # ArmorMaterial reductions are indexed boots, leggings, chestplate, helmet.
    Add-AtMarker $project.items 'items' @(
        "/** Armor material: durability factor $durability, protection helmet/chest/legs/boots $Protection, toughness $Toughness. Texture: textures/models/armor/${Name}_layer_1/2.png */",
        "public static final ItemArmor.ArmorMaterial $mat = EnumHelper.addArmorMaterial(`"$Name`", $($project.mainClass).MODID + `":$Name`", $durability, new int[] {$($values[3]), $($values[2]), $($values[1]), $($values[0])}, $Enchant, SoundEvents.ITEM_ARMOR_EQUIP_IRON, $(F $Toughness));",
        "public static final Item ${const}_HELMET = item(`"${Name}_helmet`", new ItemArmor($mat, 0, EntityEquipmentSlot.HEAD));",
        "public static final Item ${const}_CHESTPLATE = item(`"${Name}_chestplate`", new ItemArmor($mat, 0, EntityEquipmentSlot.CHEST));",
        "public static final Item ${const}_LEGGINGS = item(`"${Name}_leggings`", new ItemArmor($mat, 0, EntityEquipmentSlot.LEGS));",
        "public static final Item ${const}_BOOTS = item(`"${Name}_boots`", new ItemArmor($mat, 0, EntityEquipmentSlot.FEET));")
    foreach ($piece in @('helmet', 'chestplate', 'leggings', 'boots')) { New-ItemAssets "${Name}_$piece" ("$title " + (ConvertTo-Title $piece)) 'item' 'item/generated' $Name }
    New-Texture (Join-Path $project.assets "textures/models/armor/${Name}_layer_1.png") 'armor' $Name
    New-Texture (Join-Path $project.assets "textures/models/armor/${Name}_layer_2.png") 'armor' $Name
    if ($Repair) {
        Add-Import $project.mainFile @('net.minecraft.item.Item', 'net.minecraft.item.ItemStack')
        Add-AtMarker $project.mainFile 'init' @("ModItems.$mat.setRepairItem(new ItemStack($(ConvertTo-ItemExpr $Repair)));")
    }
    $notes.Add('no crafting recipes generated; add them with agent.ps1 new recipe')
}

# "ruby", "minecraft:dye@4", "ore:ingotIron" -> recipe ingredient JSON
function ConvertTo-Ingredient([string]$Ref) {
    if ($Ref -match '^ore:([A-Za-z0-9_]+)$') { return '{ "type": "forge:ore_dict", "ore": "' + $Matches[1] + '" }' }
    $id = ConvertTo-ItemId $Ref
    if ($Ref -match '@(\d+)$') { return '{ "item": "' + $id + '", "data": ' + $Matches[1] + ' }' }
    return '{ "item": "' + $id + '" }'
}

function Build-Recipe {
    if (-not $Result) { throw '-Result <item> is required' }
    $countRange = Get-Range $Count 1 1 '-Count'
    $resultJson = '{ "item": "' + (ConvertTo-ItemId $Result) + '"' + $(if ($Result -match '@(\d+)$') { ', "data": ' + $Matches[1] } else { '' }) + $(if ($countRange[0] -gt 1) { ', "count": ' + $countRange[0] } else { '' }) + ' }'
    if ($Shape) {
        $rows = @($Shape.Split(','))
        if ($rows.Count -gt 3 -or @($rows | Where-Object { $_.Length -gt 3 -or $_.Length -eq 0 }).Count) { throw '-Shape is up to 3 comma-separated rows of up to 3 characters, e.g. "###,#X#,###" (space = empty)' }
        $map = [ordered]@{}
        foreach ($entry in $Key) { foreach ($part in $entry.Split(',')) { if ($part -notmatch '^(.)=(.+)$') { throw "-Key entries look like X=item, got '$part'" }; $map[$Matches[1]] = $Matches[2] } }
        $symbols = @(($rows -join '').ToCharArray() | Where-Object { $_ -ne ' ' } | ForEach-Object { [string]$_ } | Select-Object -Unique)
        foreach ($s in $symbols) { if (-not $map.Contains($s)) { throw "Shape symbol '$s' has no -Key $s=<item>" } }
        foreach ($k in @($map.Keys)) { if ($k -notin $symbols) { throw "-Key '$k' is not used in -Shape" } }
        $keyJson = ($map.Keys | ForEach-Object { '    "' + $_ + '": ' + (ConvertTo-Ingredient $map[$_]) }) -join ",`n"
        $json = "{`n  `"type`": `"minecraft:crafting_shaped`",`n  `"pattern`": [ " + (($rows | ForEach-Object { '"' + $_ + '"' }) -join ', ') + " ],`n  `"key`": {`n$keyJson`n  },`n  `"result`": $resultJson`n}`n"
    } elseif ($Ingredients) {
        $list = @($Ingredients | ForEach-Object { $_.Split(',') } | Where-Object { $_ })
        if ($list.Count -lt 1 -or $list.Count -gt 9) { throw '-Ingredients takes 1 to 9 items' }
        $json = "{`n  `"type`": `"minecraft:crafting_shapeless`",`n  `"ingredients`": [ " + (($list | ForEach-Object { ConvertTo-Ingredient $_ }) -join ', ') + " ],`n  `"result`": $resultJson`n}`n"
    } else { throw 'Give -Shape with -Key (shaped) or -Ingredients (shapeless).' }
    Write-New (Join-Path $project.assets "recipes/$Name.json") $json
}

function Build-Smelting {
    if (-not $From -or -not $Result) { throw 'Usage: agent.ps1 new smelting <name> -From <item> -Result <item> [-Count n] [-Xp 0.7]' }
    $countRange = Get-Range $Count 1 1 '-Count'
    $xpValue = if ($Xp) { [double]$Xp } else { 0.7 }
    Add-Import $project.mainFile @('net.minecraft.item.Item', 'net.minecraft.item.ItemStack', 'net.minecraftforge.fml.common.registry.GameRegistry')
    Add-AtMarker $project.mainFile 'init' @("GameRegistry.addSmelting(new ItemStack($(ConvertTo-ItemExpr $From)), new ItemStack($(ConvertTo-ItemExpr $Result), $($countRange[0])), $(F $xpValue)); // smelting: $Name")
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
    Write-New (Join-Path $root ('src/gametest/java/' + $project.package.Replace('.', '/') + "/$pascal.java")) @"
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
        t.check("expected-outcome", () -> true, () -> "describe what was observed");
    }
}
"@
}

# ---- main --------------------------------------------------------------------------------

try {
    if (-not $Name) { throw "Usage: agent.ps1 new $Kind <name> [options]; see docs/GENERATORS.md" }
    if ($Kind -ne 'test' -and $Name -notmatch '^[a-z][a-z0-9_]*$') { throw "Names are lowercase registry paths like ruby_ore, got '$Name'" }
    $project = Get-Project
    $title = if ($Display) { $Display } else { ConvertTo-Title $Name }
    if ($Kind -in @('item', 'block', 'ore', 'food', 'tile')) { Assert-NewName $Name }
    if ($Kind -eq 'tools') { Assert-NewName "${Name}_sword" }
    if ($Kind -eq 'armor') { Assert-NewName "${Name}_helmet" }
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
    if ($Json) { [ordered]@{ schemaVersion = 1; status = 'PASS'; kind = $Kind; name = $Name; files = $created; notes = $notes } | ConvertTo-Json -Compress }
    else {
        "created $Kind $Name"
        foreach ($file in $created) { '  ' + $file }
        foreach ($note in $notes) { 'note: ' + $note }
    }
} catch {
    if ($Json) { [ordered]@{ schemaVersion = 1; status = 'FAIL'; message = $_.Exception.Message; files = $created } | ConvertTo-Json -Compress }
    else { 'FAIL: ' + $_.Exception.Message; if ($created.Count) { 'partially written: ' + ($created -join ', ') } }
    exit 1
}
