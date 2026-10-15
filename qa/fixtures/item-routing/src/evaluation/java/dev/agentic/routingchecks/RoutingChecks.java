package dev.agentic.routingchecks;

import dev.agentic.routing.*;
import net.minecraft.init.Blocks;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntityChest;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLServerStartedEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.items.CapabilityItemHandler;
import net.minecraftforge.items.IItemHandler;

@Mod(modid="routingchecks",name="Routing Checks",version="1",dependencies="required-after:routingproof")
public final class RoutingChecks {
    private WorldServer world;
    private RoutingTile extractor,splitter;
    private TileEntityChest source,match,other;
    private int ticks,checks;
    private boolean done;
    private void require(boolean ok,String why){checks++;if(!ok)throw new IllegalStateException("ROUTING_ASSERTIONS_FAIL: "+why);}
    private IItemHandler port(RoutingTile tile,EnumFacing face){return tile.getCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY,face);}
    private TileEntityChest chest(int x,int z){BlockPos pos=new BlockPos(x,80,z);world.setBlockState(pos,Blocks.CHEST.getDefaultState(),3);return (TileEntityChest)world.getTileEntity(pos);}
    private int count(TileEntityChest chest,Item item){int total=0;for(int i=0;i<chest.getSizeInventory();i++){ItemStack s=chest.getStackInSlot(i);if(s.getItem()==item)total+=s.getCount();}return total;}
    private int buffered(){return extractor.buffer.getStackInSlot(0).getCount()+splitter.buffer.getStackInSlot(0).getCount()+splitter.buffer.getStackInSlot(1).getCount();}
    @Mod.EventHandler public void started(FMLServerStartedEvent e) {
        world=net.minecraftforge.common.DimensionManager.getWorld(0);
        // The layout is close to chunk (0,0)'s west/north edges. Load ordinary
        // chest surroundings; intentional partial unloading has its own fixture.
        for(int x=-1;x<=0;x++)for(int z=-1;z<=0;z++)world.getChunk(x,z);
        // All positions inside one ticking spawn chunk, chests do not join.
        for(int x=1;x<=5;x++)for(int z=1;z<=3;z++)world.setBlockToAir(new BlockPos(x,80,z));
        source=chest(1,1);match=chest(4,1);other=chest(3,2);
        world.setBlockState(new BlockPos(2,80,1),RoutingProof.EXTRACTOR.getDefaultState(),3);
        world.setBlockState(new BlockPos(3,80,1),RoutingProof.SPLITTER.getDefaultState(),3);
        extractor=(RoutingTile)world.getTileEntity(new BlockPos(2,80,1));splitter=(RoutingTile)world.getTileEntity(new BlockPos(3,80,1));
        IItemHandler inlet=port(splitter,EnumFacing.WEST),out=port(splitter,EnumFacing.EAST);
        require(port(extractor,EnumFacing.SOUTH)==null && port(splitter,EnumFacing.UP)==null && port(splitter,EnumFacing.SOUTH)!=null,"Sided capability boundaries");
        ItemStack sample=new ItemStack(Blocks.COBBLESTONE,20);
        require(inlet.insertItem(0,sample,true).getCount()==4 && buffered()==0 && sample.getCount()==20,"Simulated insert mutated inventory or argument");
        require(inlet.extractItem(0,8,false).isEmpty() && out.insertItem(0,sample,false).getCount()==20,"Port permissions");
        require(inlet.insertItem(0,new ItemStack(Blocks.COBBLESTONE,8),false).isEmpty(),"Committed input");
        require(out.extractItem(0,3,true).getCount()==3 && splitter.buffer.getStackInSlot(0).getCount()==8,"Simulated extraction mutated buffer");
        ItemStack exposed=out.getStackInSlot(0);exposed.setCount(1);
        require(splitter.buffer.getStackInSlot(0).getCount()==8,"Mutable port alias");
        splitter.buffer.extractItem(0,64,false);
        source.setInventorySlotContents(0,new ItemStack(Blocks.COBBLESTONE,16));source.setInventorySlotContents(1,new ItemStack(Blocks.DIRT,12));source.markDirty();
        MinecraftForge.EVENT_BUS.register(this);
    }
    @SubscribeEvent public void tick(TickEvent.ServerTickEvent e) {
        if(e.phase!=TickEvent.Phase.END || done)return;ticks++;
        Item cobble=Item.getItemFromBlock(Blocks.COBBLESTONE),dirt=Item.getItemFromBlock(Blocks.DIRT);
        if(ticks==80) {
            require(count(match,cobble)==16 && count(other,dirt)==12,"Live two-item routing");
            require(count(match,dirt)==0 && count(other,cobble)==0,"Crossed routes");
            require(source.isEmpty() && buffered()==0,"Source drain and count conservation 28");
            System.out.println("ROUTING_STATE phase=routes source=0 matching=16 other=12 buffered=0 total=28");
            match.clear();other.clear();
            for(int i=0;i<match.getSizeInventory();i++)match.setInventorySlotContents(i,new ItemStack(Blocks.STONE,64));
            source.setInventorySlotContents(0,new ItemStack(Blocks.COBBLESTONE,40));source.setInventorySlotContents(1,new ItemStack(Blocks.DIRT,8));source.markDirty();match.markDirty();other.markDirty();
        }
        if(ticks==180) {
            require(count(match,cobble)==0 && count(match,Item.getItemFromBlock(Blocks.STONE))==1728,"Full destination changed");
            require(splitter.buffer.getStackInSlot(0).getCount()==16 && extractor.buffer.getStackInSlot(0).getCount()==16,"Bounded retained buffers");
            int total=count(source,cobble)+count(source,dirt)+count(other,dirt)+buffered();
            require(total==48 && count(other,dirt)==8,"Backpressure conservation and open second route");
            splitter.setFilter(new ItemStack(Blocks.DIRT));
            NBTTagCompound saved=splitter.writeToNBT(new NBTTagCompound());RoutingTile restored=new RoutingTile();restored.readFromNBT(saved);
            require(restored.filterCopy().getItem()==dirt && restored.buffer.getStackInSlot(0).getCount()==16 && restored.buffer.getStackInSlot(0).getItem()==cobble,"Filter and buffer NBT roundtrip");
            restored=new RoutingTile();restored.readFromNBT(extractor.writeToNBT(new NBTTagCompound()));
            require(restored.buffer.getStackInSlot(0).getCount()==16,"Extractor NBT roundtrip");
            splitter.setFilter(new ItemStack(Blocks.COBBLESTONE));
            System.out.println("ROUTING_STATE phase=blocked source="+(count(source,cobble)+count(source,dirt))+" matching=0 other=8 buffered="+buffered()+" total="+total);
            match.clear();match.markDirty();
        }
        if(ticks==260) {
            require(count(match,cobble)==40 && count(other,dirt)==8 && source.isEmpty() && buffered()==0,"Unblocked route resumed, conservation 48");
            System.out.println("ROUTING_STATE phase=resumed source=0 matching=40 other=8 buffered=0 total=48");
            System.out.println("ROUTING_PROOF_RESULT ticks="+ticks+" checks="+checks+" serialization=PASS actual-reopen=pending-user");
            System.out.println("ROUTING_LIVE_PASS");done=true;
        }
    }
}
