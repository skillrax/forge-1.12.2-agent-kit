package com.example.rubymod;

import net.minecraft.block.BlockCrops;
import net.minecraft.item.Item;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraftforge.common.EnumPlantType;

/** Wheat-like crop (age 0-7) planted from ModItems.RUBY_SEEDS; drops ModItems.RUBY when mature. */
public class RubyCrop extends BlockCrops implements ModBlocks.NoItem {
    @Override protected Item getSeed() { return ModItems.RUBY_SEEDS; }
    @Override protected Item getCrop() { return ModItems.RUBY; }
    /** BlockCrops subclasses default to Plains; farmland only sustains Crop. */
    @Override public EnumPlantType getPlantType(IBlockAccess world, BlockPos pos) { return EnumPlantType.Crop; }
}
