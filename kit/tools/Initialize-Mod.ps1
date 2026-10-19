# Creates a working mod skeleton in an empty src/main:
#   <Name>Mod (entry point), ModItems, ModBlocks, BasicBlock, ClientEvents, lang/mcmod.info/pack.mcmeta.
# Items and blocks added to ModItems/ModBlocks are registered and given item models automatically.
# Usage: agent.ps1 init <modid> ["Display Name"] [-Package com.example.x] [-Version 1.0.0]
[CmdletBinding()]
param(
    [Parameter(Mandatory, Position = 0)][ValidatePattern('^[a-z][a-z0-9_]{1,63}$')][string]$ModId,
    [Parameter(Position = 1)][string]$Name,
    [ValidatePattern('^[a-z_][a-z0-9_]*(\.[a-z_][a-z0-9_]*)+$')][string]$Package,
    [ValidatePattern('^\d+\.\d+\.\d+(-[A-Za-z0-9.-]+)?$')][string]$Version = '1.0.0',
    [switch]$Json
)
$ErrorActionPreference = 'Stop'
[Console]::OutputEncoding = New-Object Text.UTF8Encoding($false)
$root = Split-Path -Parent $PSScriptRoot
$utf8 = New-Object Text.UTF8Encoding($false)
function Write-KitFile([string]$Path, [string]$Text) {
    New-Item -ItemType Directory -Force -Path (Split-Path -Parent $Path) | Out-Null
    [IO.File]::WriteAllText($Path, $Text.Replace("`r`n", "`n"), $utf8)
}
try {
    $words = @($ModId.Split('_') | Where-Object { $_ } | ForEach-Object { $_.Substring(0, 1).ToUpperInvariant() + $_.Substring(1) })
    if (-not $Name) { $Name = $words -join ' ' }
    if ($Name -match '[\x00-\x1F"\\]') { throw 'Display name must be one line without quotes or backslashes.' }
    if (-not $Package) { $Package = 'com.example.' + $ModId }
    $reserved = 'abstract assert boolean break byte case catch char class const continue default do double else enum extends final finally float for goto if implements import instanceof int interface long native new package private protected public return short static strictfp super switch synchronized this throw throws transient try void volatile while true false null'.Split(' ')
    if (@($Package.Split('.') | Where-Object { $_ -in $reserved }).Count) { throw 'Package contains a reserved Java word.' }
    $main = Join-Path $root 'src/main'
    $existing = @(Get-ChildItem -LiteralPath $main -Recurse -File -Force -ErrorAction SilentlyContinue | Where-Object { $_.Name -ne '.gitkeep' })
    if ($existing.Count) { throw 'src/main already has content. Extend the existing mod (agent.ps1 new ...) instead of initializing again.' }
    $buildPath = Join-Path $root 'build.gradle'
    $build = [IO.File]::ReadAllText($buildPath)
    if ([regex]::Matches($build, "(?m)^version = '[^']*'\r?$").Count -ne 1 -or [regex]::Matches($build, "(?m)^group = '[^']*'\r?$").Count -ne 1 -or $build -match 'archivesName') { throw 'build.gradle differs from the kit foundation; set version/group/archivesName by hand.' }
    $build = [regex]::Replace($build, "(?m)^version = '[^']*'\r?$", "version = '$Version'")
    $build = [regex]::Replace($build, "(?m)^group = '[^']*'\r?$", "group = '$Package'")
    $build += "`nbase { archivesName = '$ModId' }`n"

    $mainClass = ($words -join '') + 'Mod'
    $java = Join-Path $main ('java/' + $Package.Replace('.', '/'))
    $assets = Join-Path $main ('resources/assets/' + $ModId)

    Write-KitFile (Join-Path $java "$mainClass.java") @"
package $Package;

import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Mod(modid = $mainClass.MODID, name = $mainClass.NAME, version = $mainClass.VERSION, acceptedMinecraftVersions = "[1.12.2]")
public class $mainClass {
    public static final String MODID = "$ModId";
    public static final String NAME = "$Name";
    /** Keep equal to build.gradle and mcmod.info; use agent.ps1 version <x.y.z> to change all three. */
    public static final String VERSION = "$Version";
    public static final Logger LOGGER = LogManager.getLogger(MODID);

    @Mod.Instance(MODID)
    public static $mainClass INSTANCE;

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        // kit:preinit
    }

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        // kit:init
    }
}
"@

    Write-KitFile (Join-Path $java 'ModItems.java') @"
package $Package;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.item.Item;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

/**
 * Every item of this mod. Declare items as static fields created with item(...); they are
 * registered automatically and ClientEvents gives each the model models/item/<name>.json.
 */
@Mod.EventBusSubscriber(modid = $mainClass.MODID)
public final class ModItems {
    public static final List<Item> ALL = new ArrayList<>();

    // kit:items

    /** Sets registry name modid:name and translation key item.modid.name (lang: item.modid.name.name=...). */
    static <T extends Item> T item(String name, T item) {
        item.setRegistryName($mainClass.MODID, name);
        item.setTranslationKey($mainClass.MODID + "." + name);
        ALL.add(item);
        return item;
    }

    @SubscribeEvent
    public static void register(RegistryEvent.Register<Item> event) {
        for (Item item : ALL) event.getRegistry().register(item);
        // kit:after-items
    }

    private ModItems() {}
}
"@

    Write-KitFile (Join-Path $java 'ModBlocks.java') @"
package $Package;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.block.Block;
import net.minecraft.block.SoundType;
import net.minecraft.block.material.Material;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.item.Item;
import net.minecraft.item.ItemBlock;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.registry.GameRegistry;

/**
 * Every block of this mod. Declare blocks as static fields created with block(...); each is
 * registered with a matching ItemBlock (unless it implements NoItem). Assets per block:
 * blockstates/<name>.json, models/block/<name>.json, models/item/<name>.json, lang tile.modid.name.name.
 */
@Mod.EventBusSubscriber(modid = $mainClass.MODID)
public final class ModBlocks {
    public static final List<Block> ALL = new ArrayList<>();

    /** Blocks implementing this get no ItemBlock (technical blocks, crops placed by seeds). */
    public interface NoItem {}

    // kit:blocks

    static <T extends Block> T block(String name, T block) {
        block.setRegistryName($mainClass.MODID, name);
        block.setTranslationKey($mainClass.MODID + "." + name);
        ALL.add(block);
        return block;
    }

    @SubscribeEvent
    public static void registerBlocks(RegistryEvent.Register<Block> event) {
        for (Block block : ALL) event.getRegistry().register(block);
        // kit:tiles
    }

    @SubscribeEvent
    public static void registerItemBlocks(RegistryEvent.Register<Item> event) {
        for (Block block : ALL) {
            if (!(block instanceof NoItem)) event.getRegistry().register(new ItemBlock(block).setRegistryName(block.getRegistryName()));
        }
    }

    private ModBlocks() {}
}
"@

    Write-KitFile (Join-Path $java 'BasicBlock.java') @"
package $Package;

import net.minecraft.block.Block;
import net.minecraft.block.SoundType;
import net.minecraft.block.material.Material;
import net.minecraft.creativetab.CreativeTabs;

/** A plain full block. Block.setSoundType is protected in 1.12, hence this small subclass. */
public class BasicBlock extends Block {
    public BasicBlock(Material material, SoundType sound, float hardness, float resistance) {
        super(material);
        setSoundType(sound);
        setHardness(hardness);
        setResistance(resistance);
        setCreativeTab(CreativeTabs.BUILDING_BLOCKS);
    }

    /** Tool class ("pickaxe", "axe", "shovel") and level (0 wood/gold, 1 stone, 2 iron, 3 diamond) needed to get drops. */
    public BasicBlock harvest(String tool, int level) {
        setHarvestLevel(tool, level);
        return this;
    }

    /** Emitted light, 0.0 to 1.0 (1.0 = level 15, like glowstone). */
    public BasicBlock light(float level) {
        setLightLevel(level);
        return this;
    }
}
"@

    Write-KitFile (Join-Path $java 'ClientEvents.java') @"
package $Package;

import net.minecraft.client.renderer.block.model.ModelResourceLocation;
import net.minecraft.item.Item;
import net.minecraftforge.client.event.ModelRegistryEvent;
import net.minecraftforge.client.model.ModelLoader;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.registry.ForgeRegistries;
import net.minecraftforge.fml.relauncher.Side;

/** Client-only (never loaded on a dedicated server). Keep all rendering/model/GUI code in client classes like this. */
@Mod.EventBusSubscriber(modid = $mainClass.MODID, value = Side.CLIENT)
public final class ClientEvents {
    /** Items implementing this register their own models in models(). */
    public interface CustomModel {}

    @SubscribeEvent
    public static void models(ModelRegistryEvent event) {
        for (Item item : ForgeRegistries.ITEMS) {
            if (!$mainClass.MODID.equals(item.getRegistryName().getNamespace()) || item instanceof CustomModel) continue;
            ModelLoader.setCustomModelResourceLocation(item, 0, new ModelResourceLocation(item.getRegistryName(), "inventory"));
        }
        // kit:models
    }

    private ClientEvents() {}
}
"@

    Write-KitFile (Join-Path $assets 'lang/en_us.lang') "# key=Display text. Items: item.$ModId.<name>.name  Blocks: tile.$ModId.<name>.name`n"
    $metadata = ConvertTo-Json -InputObject @([ordered]@{ modid = $ModId; name = $Name; description = ''; version = $Version; mcversion = '1.12.2'; authorList = @(); credits = ''; url = '' }) -Depth 4
    Write-KitFile (Join-Path $main 'resources/mcmod.info') $metadata
    Write-KitFile (Join-Path $main 'resources/pack.mcmeta') ('{"pack":{"pack_format":3,"description":"' + $Name + ' resources"}}')
    [IO.File]::WriteAllText($buildPath, $build, $utf8)
    # AGENTS.md has the request written into TASK.md before init: keep it; replace only the shipped placeholder.
    $task = Join-Path $root 'TASK.md'
    $taskKept = [IO.File]::Exists($task) -and -not [IO.File]::ReadAllText($task).Contains("request, in their words)")
    if (-not $taskKept) { Write-KitFile $task "# Task`nGoal: (user's request, in their words)`nDecisions: (choices made, defaults assumed)`nDone: skeleton for $ModId ($Version)`nNext: add content (agent.ps1 new ...), then agent.ps1 check server`n" }

    if ($Json) { [ordered]@{ schemaVersion = 1; status = 'PASS'; modId = $ModId; package = $Package; mainClass = $mainClass; version = $Version } | ConvertTo-Json -Compress }
    else {
        "created $ModId $Version in $Package ($mainClass, ModItems, ModBlocks, BasicBlock, ClientEvents; unused ones can stay)"
        if ($taskKept) { 'kept your TASK.md' } else { 'TASK.md: write the request, decisions and acceptance checks there' }
        'next: agent.ps1 new <item|block|ore|food|tools|armor|recipe|smelting|tile|mob|test> ...  then agent.ps1 check'
    }
} catch {
    if ($Json) { [ordered]@{ schemaVersion = 1; status = 'FAIL'; message = $_.Exception.Message } | ConvertTo-Json -Compress } else { 'FAIL: ' + $_.Exception.Message }
    exit 1
}
