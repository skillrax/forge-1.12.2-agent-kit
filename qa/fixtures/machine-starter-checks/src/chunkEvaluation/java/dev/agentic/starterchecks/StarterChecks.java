package dev.agentic.starterchecks;

import com.example.machinestarter.*;
import java.util.List;
import net.minecraft.init.Blocks;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraft.world.chunk.Chunk;
import net.minecraftforge.common.ForgeChunkManager;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.event.FMLServerStartedEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

// Focused starter dirty/save check, outside recipient kit and production JAR.
@Mod(modid="starterchecks",name="Starter Checks",version="1",dependencies="required-after:machinestarter")
public final class StarterChecks {
    private WorldServer world;
    private ForgeChunkManager.Ticket ticket;
    private final ChunkPos owner=new ChunkPos(128,128),neighbor=new ChunkPos(127,128);
    private final BlockPos pos=new BlockPos(2049,80,2052);
    private int stage,ticks,checks;
    private boolean done;
    private void require(boolean ok,String why){checks++;if(!ok)throw new IllegalStateException("STARTER_ASSERTIONS_FAIL: "+why);}
    @Mod.EventHandler public void preinit(FMLPreInitializationEvent e) {
        ForgeChunkManager.setForcedChunkLoadingCallback(this,new ForgeChunkManager.LoadingCallback(){public void ticketsLoaded(List<ForgeChunkManager.Ticket> list,World w){for(ForgeChunkManager.Ticket t:list)ForgeChunkManager.releaseTicket(t);}});
    }
    @Mod.EventHandler public void started(FMLServerStartedEvent e) {
        world=net.minecraftforge.common.DimensionManager.getWorld(0);
        world.getGameRules().setOrCreateGameRule("doMobSpawning","false");
        ticket=ForgeChunkManager.requestTicket(this,world,ForgeChunkManager.Type.NORMAL);require(ticket!=null,"Chunk ticket");
        ForgeChunkManager.forceChunk(ticket,owner);world.getChunk(owner.x,owner.z);world.getChunk(neighbor.x,neighbor.z);
        // A normal cube at x=2048 would make vanilla markDirty read x=2047.
        world.setBlockState(pos.west(),Blocks.STONE.getDefaultState(),3);
        world.setBlockState(pos,MachineStarter.MACHINE.getDefaultState(),3);
        MinecraftForge.EVENT_BUS.register(this);
        world.getChunkProvider().queueUnload(world.getChunkProvider().getLoadedChunk(neighbor.x,neighbor.z));
    }
    @SubscribeEvent public void tick(TickEvent.ServerTickEvent e) {
        if(done || e.phase!=TickEvent.Phase.END)return;ticks++;
        if(stage==0){
            if(world.getChunkProvider().chunkExists(neighbor.x,neighbor.z)){require(ticks<20,"Neighbor unload timeout");Chunk c=world.getChunkProvider().loadedChunks.get(ChunkPos.asLong(neighbor.x,neighbor.z));world.getChunkProvider().queueUnload(c);return;}
            stage=1;ticks=0;
        }
        if(stage==1){
            require(!world.getChunkProvider().chunkExists(neighbor.x,neighbor.z),"Dirty marking loaded absent neighbor");
            MachineTile machine=(MachineTile)world.getTileEntity(pos);Chunk owningChunk=world.getChunk(owner.x,owner.z);
            owningChunk.setModified(false);require(!owningChunk.needsSaving(false),"Dirty baseline");
            machine.setInventorySlotContents(0,new ItemStack(Blocks.DIRT,9));
            require(owningChunk.needsSaving(false),"Inventory change did not mark owning chunk dirty");
            require(!world.getChunkProvider().chunkExists(neighbor.x,neighbor.z),"Inventory write loaded absent neighbor");
            if(ticks<40)return;
            MachineTile restored=new MachineTile();restored.readFromNBT(machine.writeToNBT(new NBTTagCompound()));
            require(restored.getStackInSlot(0).getCount()==9,"Inventory serialization");
            // Keep the former neighbor ticking while the owner is really saved/unloaded.
            ForgeChunkManager.forceChunk(ticket,neighbor);world.getChunk(neighbor.x,neighbor.z);
            ForgeChunkManager.unforceChunk(ticket,owner);world.getChunkProvider().queueUnload(owningChunk);
            stage=2;ticks=0;return;
        }
        if(stage==2){
            if(world.getChunkProvider().chunkExists(owner.x,owner.z)){require(ticks<20,"Owner unload timeout");Chunk c=world.getChunkProvider().loadedChunks.get(ChunkPos.asLong(owner.x,owner.z));world.getChunkProvider().queueUnload(c);return;}
            ForgeChunkManager.forceChunk(ticket,owner);world.getChunk(owner.x,owner.z);
            MachineTile restored=(MachineTile)world.getTileEntity(pos);
            require(restored.getStackInSlot(0).getCount()==9 && restored.getStackInSlot(0).getItem()==Item.getItemFromBlock(Blocks.DIRT),"Actual unload/reload inventory");
            require(restored.decrStackSize(0,2).getCount()==2 && restored.getStackInSlot(0).getCount()==7,"Inventory removal");
            ForgeChunkManager.releaseTicket(ticket);done=true;
            System.out.println("STARTER_RESULT absentNeighborTicks=40 checks="+checks+" ownerDirty=true retainedDirt=9 removalRemaining=7");
            System.out.println("STARTER_LIVE_PASS");
        }
    }
}
