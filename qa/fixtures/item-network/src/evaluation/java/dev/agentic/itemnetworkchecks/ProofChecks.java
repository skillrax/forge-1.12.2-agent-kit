package dev.agentic.itemnetworkchecks;
import dev.agentic.itemnetwork.*;
import java.util.*;
import net.minecraft.init.*;
import net.minecraft.item.*;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.*;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.*;
import net.minecraftforge.event.world.ChunkEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLServerStartedEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.eventhandler.*;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.items.*;

@Mod(modid="itemnetworkchecks",name="Item Network Checks",version="1",dependencies="required-after:itemnetwork")
public final class ProofChecks {
    public static final BlockPos SOURCE=new BlockPos(4,4,4),A=new BlockPos(7,4,4),B=new BlockPos(6,4,6);
    public static final BlockPos INPUT=new BlockPos(3,4,4),OUT_A=new BlockPos(8,4,4),OUT_B=new BlockPos(6,4,7);
    public static int checks;
    private WorldServer w;private int ticks;private boolean dedicated,done;
    private ForgeChunkManager.Ticket ticket;private BlockPos farSource=new BlockPos(152,4,151),farDest=new BlockPos(159,4,151),farInput=new BlockPos(151,4,151),farOutput=new BlockPos(160,4,151);
    private net.minecraft.world.chunk.Chunk boundary;private boolean absent,unloaded,queued;private int frozen;private int loads;private boolean watching;
    public static void require(boolean b,String s){checks++;if(!b)throw new IllegalStateException("ITEM_NETWORK_ASSERTIONS_FAIL: "+s);}
    public static DuctTile duct(WorldServer w,BlockPos p,int role,EnumFacing face,ItemStack filter){w.setBlockState(p,Content.DUCT.getDefaultState());DuctTile t=(DuctTile)w.getTileEntity(p);t.role=role;t.face=face.ordinal();t.select(filter);return t;}
    public static TileEntityChest chest(WorldServer w,BlockPos p){w.setBlockState(p,Blocks.CHEST.getDefaultState());return (TileEntityChest)w.getTileEntity(p);}
    public static int count(TileEntityChest c,Item item){int n=0;for(int i=0;i<27;i++)if(c.getStackInSlot(i).getItem()==item)n+=c.getStackInSlot(i).getCount();return n;}
    public static void fill(TileEntityChest c){for(int i=0;i<27;i++)c.setInventorySlotContents(i,new ItemStack(Blocks.STONE,64));}
    public static void setup(WorldServer w){
        w.getChunk(0,0);
        for(int x=2;x<=9;x++)for(int z=3;z<=8;z++){TileEntity t=w.getTileEntity(new BlockPos(x,4,z));if(t instanceof net.minecraft.inventory.IInventory)((net.minecraft.inventory.IInventory)t).clear();w.setBlockToAir(new BlockPos(x,4,z));}
        chest(w,INPUT);chest(w,OUT_A);chest(w,OUT_B);
        duct(w,SOURCE,DuctTile.SOURCE,EnumFacing.WEST,ItemStack.EMPTY);
        duct(w,new BlockPos(5,4,4),0,EnumFacing.WEST,ItemStack.EMPTY);duct(w,new BlockPos(6,4,4),0,EnumFacing.WEST,ItemStack.EMPTY);
        duct(w,new BlockPos(6,4,5),0,EnumFacing.WEST,ItemStack.EMPTY);
        duct(w,A,DuctTile.DESTINATION,EnumFacing.EAST,new ItemStack(Blocks.DIRT));
        duct(w,B,DuctTile.DESTINATION,EnumFacing.SOUTH,new ItemStack(Blocks.COBBLESTONE));
    }
    public static void seed(WorldServer w){TileEntityChest c=(TileEntityChest)w.getTileEntity(INPUT);c.setInventorySlotContents(0,new ItemStack(Blocks.DIRT,16));c.setInventorySlotContents(1,new ItemStack(Blocks.COBBLESTONE,12));}
    private DuctTile d(BlockPos p){return (DuctTile)w.getTileEntity(p);}
    private TileEntityChest c(BlockPos p){return (TileEntityChest)w.getTileEntity(p);}
    private int dirt(){return count(c(INPUT),Item.getItemFromBlock(Blocks.DIRT))+count(c(OUT_A),Item.getItemFromBlock(Blocks.DIRT))+count(c(OUT_B),Item.getItemFromBlock(Blocks.DIRT))+d(SOURCE).getStackInSlot(0).getCount();}
    @Mod.EventHandler public void pre(FMLPreInitializationEvent e){net.minecraftforge.fml.common.registry.GameRegistry.registerTileEntity(PartialTile.class,new net.minecraft.util.ResourceLocation("itemnetworkchecks","partial"));ForgeChunkManager.setForcedChunkLoadingCallback(this,(ForgeChunkManager.LoadingCallback)(tickets,world)->{for(ForgeChunkManager.Ticket t:tickets)ForgeChunkManager.releaseTicket(t);});}
    @Mod.EventHandler public void started(FMLServerStartedEvent e){w=DimensionManager.getWorld(0);dedicated=w.getMinecraftServer().isDedicatedServer();setup(w);
        DuctTile source=d(SOURCE);ItemStack tagged=new ItemStack(Blocks.DIRT);NBTTagCompound tag=new NBTTagCompound();tag.setString("Proof","tagged");tagged.setTagCompound(tag);source.select(tagged);
        require(!source.matches(new ItemStack(Blocks.DIRT)),"strict NBT");source.control(3);require(source.matches(new ItemStack(Blocks.DIRT)),"ignore NBT");source.select(new ItemStack(Blocks.PLANKS));require(!source.matches(new ItemStack(Blocks.PLANKS,1,1)),"strict metadata");source.control(4);require(source.matches(new ItemStack(Blocks.PLANKS,1,1)),"ignore metadata");require(!source.matches(new ItemStack(Blocks.DIRT)),"item identity always exact");
        source.select(ItemStack.EMPTY);source.ignoreNBT=false;source.ignoreMetadata=false;
        require(!source.hasCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY,EnumFacing.WEST),"duct cannot be unintentionally extracted by automation");
        if(dedicated)seed(w);else source.enabled=false;
        MinecraftForge.EVENT_BUS.register(this);
    }
    @SubscribeEvent public void tick(TickEvent.ServerTickEvent e){if(e.phase!=TickEvent.Phase.END || !dedicated || done)return;ticks++;
        if(ticks==60){require(count(c(OUT_A),Item.getItemFromBlock(Blocks.DIRT))==16 && count(c(OUT_B),Item.getItemFromBlock(Blocks.COBBLESTONE))==12 && c(INPUT).isEmpty() && d(SOURCE).isEmpty(),"real multi-hop branch exact 16 dirt + 12 cobble");
            System.out.println("ITEM_NETWORK_BRANCH_PASS dirt=16 cobble=12 source=0 buffer=0");
            c(OUT_A).clear();c(OUT_B).clear();fill(c(OUT_A));fill(c(OUT_B));c(OUT_B).setInventorySlotContents(0,new ItemStack(Blocks.DIRT,61));d(B).select(ItemStack.EMPTY);c(INPUT).setInventorySlotContents(0,new ItemStack(Blocks.DIRT,12));}
        if(ticks==90){require(count(c(INPUT),Item.getItemFromBlock(Blocks.DIRT))==9 && count(c(OUT_B),Item.getItemFromBlock(Blocks.DIRT))==64 && d(SOURCE).isEmpty(),"partial fallback 3 accepted, 9 retained at source");c(OUT_A).setInventorySlotContents(0,ItemStack.EMPTY);}
        if(ticks==120){require(c(INPUT).isEmpty() && count(c(OUT_A),Item.getItemFromBlock(Blocks.DIRT))==9,"full destination resume after 9 source items paused");
            c(OUT_A).clear();c(OUT_B).clear();d(B).select(new ItemStack(Blocks.COBBLESTONE));w.setBlockToAir(new BlockPos(5,4,4));seed(w);}
        if(ticks==145){require(count(c(INPUT),Item.getItemFromBlock(Blocks.DIRT))==16 && count(c(OUT_A),Item.getItemFromBlock(Blocks.DIRT))==0,"removed trunk pauses extraction");duct(w,new BlockPos(5,4,4),0,EnumFacing.WEST,ItemStack.EMPTY);}
        if(ticks==190){require(count(c(OUT_A),Item.getItemFromBlock(Blocks.DIRT))==16 && count(c(OUT_B),Item.getItemFromBlock(Blocks.COBBLESTONE))==12 && c(INPUT).isEmpty(),"fresh topology resumes without duplicate/loss");
            c(OUT_A).clear();c(OUT_B).clear();d(SOURCE).own(new ItemStack(Blocks.DIRT,5));d(SOURCE).control(0);d(SOURCE).select(new ItemStack(Items.DIAMOND));d(A).select(new ItemStack(Items.DIAMOND));
            NBTTagCompound save=d(SOURCE).writeToNBT(new NBTTagCompound());DuctTile restored=new DuctTile();restored.readFromNBT(save);require(restored.getStackInSlot(0).getCount()==5 && restored.role==2 && restored.filter.getStackInSlot(0).getItem()==Items.DIAMOND && INPUT.equals(restored.origin),"owned buffer/config/origin NBT retained");w.setTileEntity(SOURCE,restored);}
        if(ticks==210){require(d(SOURCE).getStackInSlot(0).getCount()==5 && c(OUT_A).isEmpty(),"buffer retained under new destination filter");d(A).select(new ItemStack(Blocks.DIRT));}
        if(ticks==230){require(d(SOURCE).isEmpty() && count(c(OUT_A),Item.getItemFromBlock(Blocks.DIRT))==5,"buffer drains after role/source filter edits using current destination rule");d(SOURCE).role=1;d(SOURCE).select(ItemStack.EMPTY);c(OUT_A).clear();
            duct(w,new BlockPos(4,4,5),0,EnumFacing.WEST,ItemStack.EMPTY);duct(w,new BlockPos(5,4,5),0,EnumFacing.WEST,ItemStack.EMPTY);duct(w,new BlockPos(4,4,6),1,EnumFacing.WEST,ItemStack.EMPTY);chest(w,new BlockPos(3,4,6)).setInventorySlotContents(0,new ItemStack(Blocks.DIRT,24));c(INPUT).setInventorySlotContents(0,new ItemStack(Blocks.DIRT,24));}
        if(ticks>=231 && ticks<=290){int a=count(c(INPUT),Item.getItemFromBlock(Blocks.DIRT)),b=count(c(new BlockPos(3,4,6)),Item.getItemFromBlock(Blocks.DIRT)),out=count(c(OUT_A),Item.getItemFromBlock(Blocks.DIRT));require(a+b+out==48,"loop/two-source per-item conservation");if(w.getTotalWorldTime()%10==0){int delta=out-lastOut;require(delta<=8,"one shared budget despite loop/two sources");lastOut=out;}}
        if(ticks==290){require(count(c(OUT_A),Item.getItemFromBlock(Blocks.DIRT))==48 && c(INPUT).isEmpty() && c(new BlockPos(3,4,6)).isEmpty(),"two-source loop delivers each item once");System.out.println("ITEM_NETWORK_LOOP_PASS total=48 cadenceMax=8");
            // A destination alias on source inventory must never feed back, even from a different duct.
            d(A).enabled=false;d(B).enabled=false;duct(w,new BlockPos(3,4,5),2,EnumFacing.NORTH,ItemStack.EMPTY);c(INPUT).setInventorySlotContents(0,new ItemStack(Blocks.DIRT,8));}
        if(ticks==315){require(count(c(INPUT),Item.getItemFromBlock(Blocks.DIRT))==8 && d(SOURCE).isEmpty(),"source-destination feedback excluded");
            setupBoundary();}
        if(ticks==340){require(count(c(farOutput),Item.getItemFromBlock(Blocks.DIRT))==8,"cross-chunk initial delivery");c(farInput).setInventorySlotContents(0,new ItemStack(Blocks.DIRT,8));boundary=w.getChunk(10,9);watching=true;}
        if(ticks==355){require(queued && count(c(farInput),Item.getItemFromBlock(Blocks.DIRT))==8,"queued endpoint pauses");}
        if(ticks>=356 && ticks<=390 && unloaded){absent=true;require(!w.getChunkProvider().chunkExists(10,9),"actual endpoint absence remains");require(count(c(farInput),Item.getItemFromBlock(Blocks.DIRT))==8 && frozen==8,"actual unload retains source + frozen destination");}
        if(ticks==390){require(absent && loads==0,"actual unload event/absence with no unintended loads");watching=false;w.getChunk(10,9);}
        if(ticks==420){require(count(c(farOutput),Item.getItemFromBlock(Blocks.DIRT))==16 && c(farInput).isEmpty(),"reacquired endpoint reload resumes exact accounting");System.out.println("ITEM_NETWORK_CHUNK_PASS checks="+checks+" branch=28 fallback=3 resume=9 topology=28 loop=48 sharedBudget=8 bufferNBT=5 queuedAndAbsent=true reloadTotal=16");
            chest(w,new BlockPos(10,4,10)).setInventorySlotContents(0,new ItemStack(Blocks.DIRT,8));duct(w,new BlockPos(11,4,10),1,EnumFacing.WEST,ItemStack.EMPTY);duct(w,new BlockPos(12,4,10),0,EnumFacing.WEST,ItemStack.EMPTY);duct(w,new BlockPos(13,4,10),2,EnumFacing.EAST,ItemStack.EMPTY);w.setBlockState(new BlockPos(14,4,10),PartialTile.RECEIVER.getDefaultState());}
        if(ticks==430){DuctTile owner=d(new BlockPos(11,4,10));PartialTile receiver=(PartialTile)w.getTileEntity(new BlockPos(14,4,10));System.out.println("ITEM_NETWORK_REMAINDER_OBSERVE time="+w.getTotalWorldTime()+" buffer="+owner.getStackInSlot(0)+" accepted="+receiver.items.getStackInSlot(0)+" source="+c(new BlockPos(10,4,10)).getStackInSlot(0)+" loaded="+(Network.loaded(w,owner.getPos())==owner));require(owner.getStackInSlot(0).getCount()==5 && receiver.items.getStackInSlot(0).getCount()==3 && c(new BlockPos(10,4,10)).isEmpty(),"commit remainder retained 5 + accepted3 = extracted8");d(new BlockPos(13,4,10)).select(new ItemStack(Items.DIAMOND));DuctTile restored=new DuctTile();restored.readFromNBT(owner.writeToNBT(new NBTTagCompound()));w.setTileEntity(new BlockPos(11,4,10),restored);}
        if(ticks==450){require(d(new BlockPos(11,4,10)).getStackInSlot(0).getCount()==5,"actual commit remainder persists through NBT replacement and changed route filter");d(new BlockPos(13,4,10)).select(ItemStack.EMPTY);}
        if(ticks==470){require(d(new BlockPos(11,4,10)).isEmpty() && ((PartialTile)w.getTileEntity(new BlockPos(14,4,10))).items.getStackInSlot(0).getCount()==8,"buffer resumes all8 exactly once after filter restored");System.out.println("ITEM_NETWORK_LIVE_PASS checks="+checks+" commitRemainder=5 accepted=3 resumedTotal=8 priorBranchLoopChunkChecks=true");done=true;}
    }
    private int lastOut;
    private void setupBoundary(){
        ticket=ForgeChunkManager.requestTicket(this,w,ForgeChunkManager.Type.NORMAL);require(ticket!=null,"chunk ticket");ForgeChunkManager.forceChunk(ticket,new net.minecraft.util.math.ChunkPos(9,9));ForgeChunkManager.forceChunk(ticket,new net.minecraft.util.math.ChunkPos(10,9));w.getChunk(10,9);
        chest(w,farInput);chest(w,farOutput);for(int x=152;x<=159;x++)duct(w,new BlockPos(x,4,151),0,EnumFacing.WEST,ItemStack.EMPTY);d(farSource).role=1;d(farDest).role=2;d(farDest).face=EnumFacing.EAST.ordinal();c(farInput).setInventorySlotContents(0,new ItemStack(Blocks.DIRT,8));
    }
    @SubscribeEvent(priority=EventPriority.HIGHEST) public void queue(TickEvent.WorldTickEvent e){if(e.world==w && e.phase==TickEvent.Phase.END && watching && !queued && w.getTotalWorldTime()%10==0){ForgeChunkManager.unforceChunk(ticket,new net.minecraft.util.math.ChunkPos(10,9));w.getChunkProvider().queueUnload(boundary);queued=true;require(boundary.unloadQueued,"endpoint queued before routing");}}
    @SubscribeEvent(priority=EventPriority.LOWEST) public void afterNetwork(TickEvent.WorldTickEvent e){if(e.world==w && e.phase==TickEvent.Phase.END && queued && !unloaded){require(boundary.unloadQueued && count(c(farInput),Item.getItemFromBlock(Blocks.DIRT))==8,"routing preserves pending unload and source items");}}
    @SubscribeEvent public void unload(ChunkEvent.Unload e){if(watching && e.getWorld()==w && e.getChunk().x==10 && e.getChunk().z==9){unloaded=true;TileEntity t=e.getChunk().getTileEntityMap().get(farOutput);frozen=count((TileEntityChest)t,Item.getItemFromBlock(Blocks.DIRT));}}
    @SubscribeEvent public void load(ChunkEvent.Load e){if(watching && unloaded && e.getWorld()==w && e.getChunk().x==10 && e.getChunk().z==9)loads++;}
}
