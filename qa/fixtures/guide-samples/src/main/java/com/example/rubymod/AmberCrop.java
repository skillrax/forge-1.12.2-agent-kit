package com.example.rubymod;

import net.minecraft.block.BlockCrops;
import net.minecraft.block.properties.PropertyInteger;
import net.minecraft.block.state.BlockStateContainer;
import net.minecraft.item.Item;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraftforge.common.EnumPlantType;

/** Crop with 4 stages (age 0-3), the blocks.md pattern for a stage count other than 8. */
public class AmberCrop extends BlockCrops implements ModBlocks.NoItem {
    /** Static: the BlockCrops constructor reads getAgeProperty() before instance fields exist. */
    public static final PropertyInteger AGE = PropertyInteger.create("age", 0, 3);

    @Override protected PropertyInteger getAgeProperty() { return AGE; }
    @Override public int getMaxAge() { return 3; }
    @Override protected BlockStateContainer createBlockState() { return new BlockStateContainer(this, AGE); }
    @Override protected Item getSeed() { return ModItems.AMBER_SEEDS; }
    @Override protected Item getCrop() { return ModItems.RUBY; }
    @Override public EnumPlantType getPlantType(IBlockAccess world, BlockPos pos) { return EnumPlantType.Crop; }
}
