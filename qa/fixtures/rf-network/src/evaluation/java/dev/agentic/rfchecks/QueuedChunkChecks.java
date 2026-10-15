package dev.agentic.rfchecks;

import java.util.List;
import dev.agentic.rfnetwork.*;
import net.minecraft.util.math.*;
import net.minecraft.world.*;
import net.minecraft.world.chunk.Chunk;
import net.minecraftforge.common.*;
import net.minecraftforge.fluids.*;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.*;
import net.minecraftforge.fml.common.eventhandler.*;
import net.minecraftforge.fml.common.gameevent.TickEvent;

/** Queue an INTERMEDIATE cable chunk after provider ticking, before network routing. */
@Mod(modid="rfqueuedchecks",name="RF Pending Unload Checks",version="1",dependencies="required-after:rfnetwork")
public final class QueuedChunkChecks {
    private WorldServer w;
    private ForgeChunkManager.Ticket ticket;
    private Chunk cut;
    private final BlockPos gpos=new BlockPos(4097,80,4104),spos=new BlockPos(4254,80,4104);
    private GeneratorTile generator;
    private StorageTile storage;
    private int age,phase,absent,checks;
    private long stoppedGeneration,stoppedCredit;
    private int stoppedLava;
    private boolean done;
    private void require(boolean ok,String why){checks++;if(!ok)throw new IllegalStateException("RF_ASSERTIONS_FAIL: queued unload: "+why);}
    @Mod.EventHandler public void pre(FMLPreInitializationEvent e){ForgeChunkManager.setForcedChunkLoadingCallback(this,(ForgeChunkManager.LoadingCallback)(tickets,world)->{for(ForgeChunkManager.Ticket t:tickets)ForgeChunkManager.releaseTicket(t);});}
    @Mod.EventHandler public void start(FMLServerStartedEvent e){
        if(!Boolean.getBoolean("rfnetwork.chunkOnly"))return;
        w=DimensionManager.getWorld(0);net.minecraftforge.common.MinecraftForge.EVENT_BUS.register(this);
        ticket=ForgeChunkManager.requestTicket(this,w,ForgeChunkManager.Type.NORMAL);require(ticket!=null,"QA ticket");
        for(int x=256;x<=265;x++){ForgeChunkManager.forceChunk(ticket,new ChunkPos(x,256));w.getChunk(x,256);}
        for(int x=gpos.getX();x<=spos.getX();x++){
            BlockPos p=new BlockPos(x,80,4104);w.setBlockToAir(p);w.setBlockState(p,(x==gpos.getX()?Content.GENERATOR:x==spos.getX()?Content.STORAGE:Content.CABLE).getDefaultState(),3);
        }
        generator=(GeneratorTile)w.getTileEntity(gpos);storage=(StorageTile)w.getTileEntity(spos);
        generator.setInventorySlotContents(1,new ItemStack(Content.UPGRADE));generator.fluids().fill(new FluidStack(FluidRegistry.LAVA,1000),true);
        cut=w.getChunk(260,256);w.getChunkProvider().saveChunks(true);
    }
    private boolean present(){for(Chunk c:w.getChunkProvider().getLoadedChunks())if(c.x==260 && c.z==256)return true;return false;}
    @SubscribeEvent(priority=EventPriority.HIGHEST) public void beforeNetwork(TickEvent.WorldTickEvent e){
        if(done || w==null || e.world!=w || e.phase!=TickEvent.Phase.END)return;
        if(phase==0 && age==8){
            require(generator.generated>0 && generator.generated==storage.stored(),"Ten-chunk line transfers and conserves while loaded");
            stoppedGeneration=generator.generated;stoppedCredit=generator.credit();stoppedLava=generator.lava();
            ForgeChunkManager.unforceChunk(ticket,new ChunkPos(260,256));w.getChunkProvider().queueUnload(cut);
            require(cut.unloadQueued,"Intermediate cable chunk queued before network END");phase=1;
        }
    }
    @SubscribeEvent public void afterTick(TickEvent.ServerTickEvent e){
        if(e.phase!=TickEvent.Phase.END || w==null || done)return;age++;
        if(phase==1){
            require(generator.generated==stoppedGeneration && storage.stored()==stoppedGeneration,"No transfer once intermediate chunk is queued for unload");
            require(generator.credit()==stoppedCredit && generator.lava()==stoppedLava,"No fuel expenditure while path unavailable");
            if(present())require(cut.unloadQueued,"Routing/cable shape lookup must not cancel pending unload");
            // The production shape query also must not clear the provider flag.
            if(present()){
                BlockPos edge=new BlockPos(4159,80,4104);Content.CABLE.getActualState(Content.CABLE.getDefaultState(),w,edge);
                require(cut.unloadQueued,"Server cable connection query preserves queued unload");
            }else{
                require(Network.loaded(w,new BlockPos(4160,80,4104))==null,"Missing middle remains unavailable without loading");absent++;
                if(absent==6){ForgeChunkManager.forceChunk(ticket,new ChunkPos(260,256));w.getChunk(260,256);phase=2;age=0;}
            }
            if(age>50)require(false,"Queued intermediate chunk never unloaded");
        }else if(phase==2 && age==6){
            require(generator.generated==stoppedGeneration+12000 && storage.stored()==generator.generated,"Reloaded middle reconnects ten-chunk line with exact conservation");
            require(generator.charged==generator.generated*3 && generator.credit()+(long)generator.lava()*4000+generator.charged==4000000,"Saved fuel reserve conserved through pause and rejoin");
            done=true;ForgeChunkManager.releaseTicket(ticket);
            System.out.println("RF_SERVER_PASS queued-unload=true ten-chunk-line=true assertions="+checks+" absentTicks="+absent+" generated=stored="+generator.generated);
        }
    }
}
