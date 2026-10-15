package com.example.rubymod;

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