package dev.agentic.workshop;
import net.minecraft.init.Biomes;
import net.minecraft.world.*;
import net.minecraft.world.biome.BiomeProviderSingle;
import net.minecraft.world.gen.IChunkGenerator;
import net.minecraft.util.math.BlockPos;
public final class TrialWorldProvider extends WorldProvider {
    @Override protected void init() {hasSkyLight=true;biomeProvider=new BiomeProviderSingle(Biomes.PLAINS);}
    @Override public DimensionType getDimensionType() {return TrialDimension.type;}
    @Override public IChunkGenerator createChunkGenerator() {return new TrialChunkGenerator(world);}
    @Override public boolean canRespawnHere() {return false;}
    @Override public int getRespawnDimension(net.minecraft.entity.player.EntityPlayerMP player) {return 0;}
    @Override public BlockPos getSpawnCoordinate() {return new BlockPos(2,64,0);}
}