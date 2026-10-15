package dev.agentic.routingchecks;

import dev.agentic.routing.*;
import java.util.*;
import net.minecraft.init.Blocks;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.tileentity.TileEntityChest;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraft.world.chunk.Chunk;
import net.minecraftforge.common.ForgeChunkManager;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.world.ChunkEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.event.FMLServerStartedEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

// Actual provider unload/save/reload and normal server ticks. Never in the playable JAR.
@Mod(modid="routingchunkchecks",name="Routing Chunk Checks",version="1",dependencies="required-after:routingproof")
public final class ChunkChecks {
    private WorldServer world;
    private ForgeChunkManager.Ticket ticket;
    private final Set<ChunkPos> chunks=new HashSet<>(),missing=new HashSet<>();
    private final Map<ChunkPos,int[]> savedCounts=new HashMap<>();
    private final List<Long> partialTimes=new ArrayList<>(),loadedTimes=new ArrayList<>();
    private BlockPos[] positions;
    private int test=-1,age,phase,checks,absentTicks,totalTicks;
    private long tickStart;
    private boolean done;
    private final String[] names={"source-unloaded","extractor-side-unloaded","extractor-unloaded","splitter-side-unloaded","matching-unloaded","matching-side-only","other-unloaded","other-side-only","corner-both-destinations-unloaded"};
    private void require(boolean ok,String why){checks++;if(!ok)throw new IllegalStateException("ROUTING_CHUNK_ASSERTIONS_FAIL: "+names[Math.max(0,test)]+": "+why);}
    @Mod.EventHandler public void preinit(FMLPreInitializationEvent e) {
        ForgeChunkManager.setForcedChunkLoadingCallback(this,new ForgeChunkManager.LoadingCallback(){
            public void ticketsLoaded(List<ForgeChunkManager.Ticket> tickets,World world){for(ForgeChunkManager.Ticket t:tickets)ForgeChunkManager.releaseTicket(t);}
        });
    }
    private TileEntity tile(int index){require(world.isBlockLoaded(positions[index]),"QA tried reading absent tile");return world.getTileEntity(positions[index]);}
    private int[] contents(TileEntity te) {
        int[] result=new int[2];
        if(te instanceof TileEntityChest){TileEntityChest c=(TileEntityChest)te;for(int i=0;i<c.getSizeInventory();i++)add(result,c.getStackInSlot(i));}
        if(te instanceof RoutingTile){RoutingTile r=(RoutingTile)te;for(int i=0;i<r.buffer.getSlots();i++){ItemStack s=r.buffer.getStackInSlot(i);require(s.getCount()<=16,"Buffer exceeded bound");add(result,s);}}
        return result;
    }
    private void add(int[] counts,ItemStack s){if(s.getItem()==Item.getItemFromBlock(Blocks.COBBLESTONE))counts[0]+=s.getCount();if(s.getItem()==Item.getItemFromBlock(Blocks.DIRT))counts[1]+=s.getCount();}
    @SubscribeEvent public void unloading(ChunkEvent.Unload e) {
        if(e.getWorld()!=world || positions==null)return;
        ChunkPos cp=e.getChunk().getPos();if(!missing.contains(cp))return;
        int[] counts=new int[2];
        // Read the unloading chunk's existing map, without asking World for tiles.
        for(BlockPos p:positions)if(new ChunkPos(p).equals(cp)){int[] part=contents(e.getChunk().getTileEntityMap().get(p));counts[0]+=part[0];counts[1]+=part[1];}
        savedCounts.put(cp,counts);
        System.out.println("ROUTING_CHUNK_UNLOAD case="+names[test]+" chunk="+cp+" cobble="+counts[0]+" dirt="+counts[1]);
    }
    @SubscribeEvent public void loading(ChunkEvent.Load e) {
        if(e.getWorld()==world && phase<2 && savedCounts.containsKey(e.getChunk().getPos()))
            new Exception("ROUTING_CHUNK_UNEXPECTED_LOAD_TRACE").printStackTrace();
    }
    private void conserved() {
        int[] counts=new int[2];
        for(BlockPos p:positions)if(world.isBlockLoaded(p)){int[] part=contents(world.getTileEntity(p));counts[0]+=part[0];counts[1]+=part[1];}
        for(Map.Entry<ChunkPos,int[]> e:savedCounts.entrySet())if(!world.getChunkProvider().chunkExists(e.getKey().x,e.getKey().z)){counts[0]+=e.getValue()[0];counts[1]+=e.getValue()[1];}
        require(counts[0]==32 && counts[1]==24,"Conservation cobble="+counts[0]+" dirt="+counts[1]);
    }
    private void setup() {
        // Clean old fixture only after every old chunk was explicitly reloaded.
        if(positions!=null)for(BlockPos p:positions){
            TileEntity old=world.getTileEntity(p);
            if(old instanceof TileEntityChest)((TileEntityChest)old).clear();
            if(old instanceof RoutingTile)for(int i=0;i<2;i++)((RoutingTile)old).buffer.setStackInSlot(i,ItemStack.EMPTY);
            world.setBlockToAir(p);
        }
        for(ChunkPos cp:chunks)ForgeChunkManager.unforceChunk(ticket,cp);
        chunks.clear();missing.clear();savedCounts.clear();test++;
        if(test>=names.length){finish();return;}
        int boundary=2048+test*64,z=2052,splitX;
        if(test<2)splitX=boundary+1;else if(test<4)splitX=boundary;else splitX=boundary-1;
        if(test>=6)z=2063; if(test==6 || test==7)splitX=boundary+4;
        positions=new BlockPos[]{new BlockPos(splitX-2,80,z),new BlockPos(splitX-1,80,z),new BlockPos(splitX,80,z),new BlockPos(splitX+1,80,z),new BlockPos(splitX,80,z+1)};
        for(BlockPos p:positions)chunks.add(new ChunkPos(p));
        // Load the chest guard's ordinary surroundings too. Only the chosen
        // boundary side should be absent, not unrelated fixture edge chunks.
        for(int index:new int[]{0,3,4})for(int dx:new int[]{-3,3})for(int dz:new int[]{-3,3})chunks.add(new ChunkPos(positions[index].add(dx,0,dz)));
        for(ChunkPos cp:chunks){ForgeChunkManager.forceChunk(ticket,cp);world.getChunk(cp.x,cp.z);}
        for(int i=0;i<positions.length;i++){world.setBlockToAir(positions[i]);world.setBlockState(positions[i],i==1?RoutingProof.EXTRACTOR.getDefaultState():i==2?RoutingProof.SPLITTER.getDefaultState():Blocks.CHEST.getDefaultState(),3);}
        TileEntityChest source=(TileEntityChest)tile(0);source.setInventorySlotContents(0,new ItemStack(Blocks.COBBLESTONE,16));source.setInventorySlotContents(1,new ItemStack(Blocks.DIRT,16));source.markDirty();
        RoutingTile extractor=(RoutingTile)tile(1),splitter=(RoutingTile)tile(2);
        extractor.buffer.insertItem(0,new ItemStack(Blocks.COBBLESTONE,8),false);
        splitter.setFilter(new ItemStack(Blocks.DIRT));splitter.buffer.insertItem(0,new ItemStack(Blocks.DIRT,8),false);splitter.buffer.insertItem(1,new ItemStack(Blocks.COBBLESTONE,8),false);
        ChunkPos cut=new ChunkPos(positions[test<2?0:test<4?1:test<6?3:4]);
        if(test<8 && test%2==1){missing.addAll(chunks);missing.remove(cut);}else missing.add(cut);
        if(test==8)missing.add(new ChunkPos(positions[3]));
        require(!missing.isEmpty() && missing.size()<chunks.size(),"Fixture needs both held and absent chunks");
        conserved();
        for(ChunkPos cp:missing){ForgeChunkManager.unforceChunk(ticket,cp);Chunk chunk=world.getChunkProvider().getLoadedChunk(cp.x,cp.z);require(chunk!=null,"Chunk initially loaded");world.getChunkProvider().queueUnload(chunk);}
        phase=0;age=0;absentTicks=0;
    }
    @Mod.EventHandler public void started(FMLServerStartedEvent e) {
        world=net.minecraftforge.common.DimensionManager.getWorld(0);
        world.getGameRules().setOrCreateGameRule("doMobSpawning","false");
        world.getGameRules().setOrCreateGameRule("doTileDrops","false");
        ticket=ForgeChunkManager.requestTicket(this,world,ForgeChunkManager.Type.NORMAL);require(ticket!=null,"Forge ticket allocation");
        MinecraftForge.EVENT_BUS.register(this);setup();
    }
    @SubscribeEvent public void tick(TickEvent.ServerTickEvent e) {
        if(done)return;
        if(e.phase==TickEvent.Phase.START){tickStart=System.nanoTime();return;}
        long nanos=System.nanoTime()-tickStart;totalTicks++;age++;
        if(phase==0){
            boolean gone=true;for(ChunkPos cp:missing)gone &= !world.getChunkProvider().chunkExists(cp.x,cp.z);
            require(age<=20,"Actual unload timeout");
            if(gone){require(savedCounts.size()==missing.size(),"Actual unload event missing");phase=1;age=0;}
            else for(ChunkPos cp:missing){Chunk c=world.getChunkProvider().loadedChunks.get(ChunkPos.asLong(cp.x,cp.z));if(c!=null)world.getChunkProvider().queueUnload(c);}
            return;
        }
        if(phase==1){
            for(ChunkPos cp:missing)require(!world.getChunkProvider().chunkExists(cp.x,cp.z),"Routing unexpectedly loaded absent chunk");
            for(ChunkPos cp:chunks)if(!missing.contains(cp))require(world.getChunkProvider().chunkExists(cp.x,cp.z),"Held chunk stopped loading");
            conserved();absentTicks++;if(age>10)partialTimes.add(nanos);
            if(age==60){
                System.out.println("ROUTING_CHUNK_STATE case="+names[test]+" absentTicks="+absentTicks+" conserved=56 noReload=true");
                phase=2;age=0;
                for(ChunkPos cp:missing){ForgeChunkManager.forceChunk(ticket,cp);world.getChunk(cp.x,cp.z);}
            }
            return;
        }
        conserved();if(age>10)loadedTimes.add(nanos);
        RoutingTile splitter=(RoutingTile)tile(2);require(splitter.filterCopy().getItem()==Item.getItemFromBlock(Blocks.DIRT),"Filter changed on reload");
        if(age==80){
            int[] matching=contents(tile(3)),other=contents(tile(4));
            require(matching[1]==24 && matching[0]==0 && other[0]==32 && other[1]==0,"Routes did not resume correctly");
            require(((TileEntityChest)tile(0)).isEmpty() && contents(tile(1))[0]==0 && contents(tile(2))[0]==0 && contents(tile(2))[1]==0,"Retained items did not finish delivery");
            System.out.println("ROUTING_CHUNK_CASE_PASS case="+names[test]+" matchingDirt=24 otherCobble=32 total=56");setup();
        }
    }
    private String timing(List<Long> values){Collections.sort(values);return "samples="+values.size()+" p95Ms="+String.format(java.util.Locale.ROOT,"%.3f",values.get((int)Math.ceil(values.size()*.95)-1)/1e6)+" maxMs="+String.format(java.util.Locale.ROOT,"%.3f",values.get(values.size()-1)/1e6);}
    private void finish(){
        ForgeChunkManager.releaseTicket(ticket);done=true;
        System.out.println("ROUTING_CHUNK_TIMING partial "+timing(partialTimes));System.out.println("ROUTING_CHUNK_TIMING loaded "+timing(loadedTimes));
        System.out.println("ROUTING_CHUNK_RESULT cases=9 checks="+checks+" ticks="+totalTicks+" productionChanges=1");System.out.println("ROUTING_CHUNK_LIVE_PASS");
    }
}
