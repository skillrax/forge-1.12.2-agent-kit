package com.example.rubymod;

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