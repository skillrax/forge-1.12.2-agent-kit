package dev.agentic.blockactions;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.gen.ChunkProviderServer;
/** Collection access preserves queued unloads, unlike getLoadedChunk. */
public final class LoadedAccess {
    public static Chunk chunk(World w,BlockPos p) {
        if(!(w.getChunkProvider() instanceof ChunkProviderServer))return null;
        for(Chunk c:((ChunkProviderServer)w.getChunkProvider()).getLoadedChunks())
            if(c.x==p.getX()>>4 && c.z==p.getZ()>>4 && !c.unloadQueued)return c;
        return null;
    }
    public static boolean safe(World w,BlockPos p) {
        if(p.getY()<1 || p.getY()>254 || !w.getWorldBorder().contains(p))return false;
        for(int x=(p.getX()-32)>>4;x<=(p.getX()+32)>>4;x++)
            for(int z=(p.getZ()-32)>>4;z<=(p.getZ()+32)>>4;z++)
                if(chunk(w,new BlockPos(x<<4,p.getY(),z<<4))==null)return false;
        return true;
    }
    private LoadedAccess() {}
}
