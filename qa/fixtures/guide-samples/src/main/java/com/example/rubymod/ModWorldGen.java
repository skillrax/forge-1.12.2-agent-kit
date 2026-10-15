package com.example.rubymod;

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
        ore(ModBlocks.RUBY_ORE, 0, 8, 8, 5, 32);
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