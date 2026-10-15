package dev.agentic.workshop;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.gen.ChunkGeneratorFlat;
import net.minecraft.util.math.BlockPos;
public final class TrialChunkGenerator extends ChunkGeneratorFlat {
    public TrialChunkGenerator(World world) {
        super(world,world.getSeed(),false,"3;minecraft:bedrock,60*minecraft:stone,2*minecraft:dirt,minecraft:grass;1;");
    }
    @Override public Chunk generateChunk(int x,int z) {
        Chunk chunk=super.generateChunk(x,z);
        // Generated once with the origin chunk; revisiting never repairs or overwrites player edits.
        if(x==0 && z==0)chunk.setBlockState(new BlockPos(0,64,0),Content.PORTAL.getDefaultState());
        return chunk;
    }
}