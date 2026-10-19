package com.example.rubymod;

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
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.registry.GameRegistry;

/**
 * Every block of this mod. Declare blocks as static fields created with block(...); each is
 * registered with a matching ItemBlock (unless it implements NoItem). Assets per block:
 * blockstates/<name>.json, models/block/<name>.json, models/item/<name>.json, lang tile.modid.name.name.
 */
@Mod.EventBusSubscriber(modid = RubymodMod.MODID)
public final class ModBlocks {
    public static final List<Block> ALL = new ArrayList<>();

    /** Blocks implementing this get no ItemBlock (technical blocks, crops placed by seeds). */
    public interface NoItem {}

    public static final Block RUBY_ORE = block("ruby_ore", new OreBlock(3.0F, 2, () -> ModItems.RUBY, 1, 2, 3, 7));
    public static final Block RUBY_BLOCK = block("ruby_block", new BasicBlock(Material.IRON, SoundType.METAL, 5.0F, 5.0F).harvest("pickaxe", 2));
    public static final Block RUBY_BOX = block("ruby_box", new RubyBoxBlock());
    public static final Block FACING_BLOCK = block("facing_block", new FacingBlock());
    public static final Block RUBY_CROP = block("ruby_crop", new RubyCrop());
    public static final Block AMBER_CROP = block("amber_crop", new AmberCrop());
    /** Vanilla water textures tinted by setColor: no texture files needed. */
    public static final Fluid SYRUP_FLUID = fluid(new Fluid("syrup", new ResourceLocation("minecraft:blocks/water_still"), new ResourceLocation("minecraft:blocks/water_flow")).setColor(0xFFB5651D).setViscosity(3000));
    public static final Block SYRUP = block("syrup", new SyrupBlock(SYRUP_FLUID));
    // kit:blocks

    /** Register the fluid before creating its block; the bucket needs FluidRegistry.enableUniversalBucket() in the main class. */
    static Fluid fluid(Fluid fluid) {
        FluidRegistry.registerFluid(fluid);
        FluidRegistry.addBucketForFluid(fluid);
        return fluid;
    }

    static <T extends Block> T block(String name, T block) {
        block.setRegistryName(RubymodMod.MODID, name);
        block.setTranslationKey(RubymodMod.MODID + "." + name);
        ALL.add(block);
        return block;
    }

    @SubscribeEvent
    public static void registerBlocks(RegistryEvent.Register<Block> event) {
        for (Block block : ALL) event.getRegistry().register(block);
        GameRegistry.registerTileEntity(RubyBoxTile.class, new ResourceLocation(RubymodMod.MODID, "ruby_box"));
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