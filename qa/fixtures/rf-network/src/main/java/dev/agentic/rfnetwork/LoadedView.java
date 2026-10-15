package dev.agentic.rfnetwork;

import java.util.HashMap;
import java.util.Map;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraft.world.chunk.Chunk;

/** Short-lived synchronous lookup index. Never asks the provider to load or unqueue a chunk. */
final class LoadedView {
    private final World world;
    private final Map<Long,Chunk> chunks=new HashMap<>();
    LoadedView(World w){
        world=w;
        if(w instanceof WorldServer)for(Chunk c:((WorldServer)w).getChunkProvider().getLoadedChunks())
            chunks.put(ChunkPos.asLong(c.x,c.z),c);
    }
    TileEntity tile(BlockPos p){
        if(!(world instanceof WorldServer))return world.isBlockLoaded(p)?world.getTileEntity(p):null;
        Chunk c=chunks.get(ChunkPos.asLong(p.getX()>>4,p.getZ()>>4));
        if(c==null || c.unloadQueued || !c.isLoaded())return null;
        TileEntity t=c.getTileEntityMap().get(p);
        return t==null || t.isInvalid()?null:t;
    }
}
