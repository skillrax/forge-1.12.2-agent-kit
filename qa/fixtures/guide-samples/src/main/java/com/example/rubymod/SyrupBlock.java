package com.example.rubymod;

import net.minecraft.block.material.Material;
import net.minecraftforge.fluids.BlockFluidClassic;
import net.minecraftforge.fluids.Fluid;

/** Placed syrup (fluids.md); the universal bucket carries it, so no ItemBlock. */
public class SyrupBlock extends BlockFluidClassic implements ModBlocks.NoItem {
    public SyrupBlock(Fluid fluid) {
        super(fluid, Material.WATER);
    }
}
