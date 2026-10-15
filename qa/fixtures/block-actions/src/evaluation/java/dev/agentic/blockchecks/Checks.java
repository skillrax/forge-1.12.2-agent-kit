package dev.agentic.blockchecks;
import dev.agentic.blockactions.*;
import net.minecraft.block.*;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.*;
import net.minecraft.item.*;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.*;
import net.minecraft.util.math.*;
import net.minecraft.world.*;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.gen.ChunkProviderServer;
import net.minecraftforge.common.*;
import net.minecraftforge.event.world.BlockEvent;
import net.minecraftforge.fluids.*;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLServerStartedEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

@Mod(modid="blockchecks",name="Block Checks",version="1",dependencies="required-after:blockactions")
public final class Checks {
    private int count;
    private boolean cancelBreak,cancelPlace;
    private WorldServer w;private MachineTile m;private BlockPos p=new BlockPos(8,80,8),t=p.east();
    private void ok(boolean v,String why){count++;if(!v)throw new IllegalStateException("BLOCK_ASSERTIONS_FAIL: "+why+" status="+(m==null?-1:m.status));}
    @SubscribeEvent public void breaking(BlockEvent.BreakEvent e){if(cancelBreak)e.setCanceled(true);}
    @SubscribeEvent public void placing(BlockEvent.PlaceEvent e){if(cancelPlace)e.setCanceled(true);}
    private void clear(){for(int i=0;i<8;i++)m.setInventorySlotContents(i,ItemStack.EMPTY);w.setBlockToAir(t);m.tank.setFluid(null);m.mode=0;m.policy=0;m.context=0;m.xp=0;m.armed=true;m.face=EnumFacing.UP.getIndex();m.high=0;}
    private int outputs(Item item){int n=0;for(int i=2;i<8;i++)if(m.getStackInSlot(i).getItem()==item)n+=m.getStackInSlot(i).getCount();return n;}
    private void block(Block b){w.setBlockState(t,b.getDefaultState(),3);}
    private void input(Block b){m.setInventorySlotContents(0,new ItemStack(b,2));}
    @Mod.EventHandler public void started(FMLServerStartedEvent e) throws Exception{
        if(!net.minecraftforge.fml.common.FMLCommonHandler.instance().getMinecraftServerInstance().isDedicatedServer())return;
        w=DimensionManager.getWorld(0);MinecraftForge.EVENT_BUS.register(this);
        for(int x=-3;x<=3;x++)for(int z=-3;z<=3;z++)w.getChunk(x,z);
        if(w.getTileEntity(p) instanceof MachineTile && ((MachineTile)w.getTileEntity(p)).xp==19){
            m=(MachineTile)w.getTileEntity(p);
            ok(m.xp==19 && m.tank.getFluidAmount()==1000 && m.mode==3 && m.high==1 && m.getStackInSlot(0).getCount()==2 && !m.armed,"Actual separate JVM saved fields/default pause");
            System.out.println("BLOCK_RELOAD_PASS checks="+count);System.out.println("BLOCK_LIVE_PASS");return;
        }
        w.setBlockState(p,MachineStarter.MACHINE.getDefaultState(),3);m=(MachineTile)w.getTileEntity(p);clear();m.armed=false;
        block(Blocks.STONE);m.step();ok(m.status==0 && w.getBlockState(t).getBlock()==Blocks.STONE,"Default off");
        m.armed=true;m.setInventorySlotContents(1,new ItemStack(Items.WOODEN_PICKAXE));block(Blocks.DIAMOND_ORE);m.step();ok(m.status==7 && w.getBlockState(t).getBlock()==Blocks.DIAMOND_ORE,"Real harvest level rejection");
        m.setInventorySlotContents(1,new ItemStack(Items.DIAMOND_PICKAXE));block(Blocks.STONE);m.step();ok(m.status==2 && w.isAirBlock(t) && outputs(Item.getItemFromBlock(Blocks.COBBLESTONE))==1 && m.getStackInSlot(1).getItemDamage()==1,"Ordinary stone / durability");
        clear();m.setInventorySlotContents(1,new ItemStack(Items.DIAMOND_PICKAXE));m.policy=1;block(Blocks.STONE);m.step();ok(outputs(Item.getItemFromBlock(Blocks.STONE))==1 && m.xp==0,"Silk stone");
        clear();m.setInventorySlotContents(1,new ItemStack(Items.DIAMOND_PICKAXE));block(Blocks.DIAMOND_ORE);m.step();ok(outputs(Items.DIAMOND)==1 && m.xp>=3 && m.xp<=7,"Ore drops / stored XP");
        clear();m.setInventorySlotContents(1,new ItemStack(Items.DIAMOND_PICKAXE));m.policy=1;block(Blocks.DIAMOND_ORE);m.step();ok(outputs(Item.getItemFromBlock(Blocks.DIAMOND_ORE))==1 && m.xp==0,"Silk ore / no XP");
        clear();m.setInventorySlotContents(1,new ItemStack(Items.DIAMOND_PICKAXE));m.policy=4;block(Blocks.DIAMOND_ORE);m.step();ok(outputs(Items.DIAMOND)>=1 && outputs(Items.DIAMOND)<=4,"Bounded fortune III");
        clear();block(Blocks.GLASS);m.step();ok(m.status==2 && outputs(Item.getItemFromBlock(Blocks.GLASS))==0,"Ordinary glass has no drops");
        clear();m.setInventorySlotContents(1,new ItemStack(Items.DIAMOND_PICKAXE));m.policy=1;block(Blocks.GLASS);m.step();ok(outputs(Item.getItemFromBlock(Blocks.GLASS))==1,"Silk glass");
        clear();m.setInventorySlotContents(1,new ItemStack(Items.DIAMOND_PICKAXE));for(int i=2;i<8;i++)m.setInventorySlotContents(i,new ItemStack(Items.STICK,64));block(Blocks.STONE);m.step();ok(m.status==8 && w.getBlockState(t).getBlock()==Blocks.STONE && m.getStackInSlot(1).getItemDamage()==0 && m.xp==0,"Mining backpressure no spending/removal");
        clear();block(Blocks.CHEST);((net.minecraft.tileentity.TileEntityChest)w.getTileEntity(t)).setInventorySlotContents(0,new ItemStack(Items.DIAMOND,7));m.step();ok(m.status==5 && ((net.minecraft.tileentity.TileEntityChest)w.getTileEntity(t)).getStackInSlot(0).getCount()==7,"Inventory tile and contents rejected intact");
        clear();block(Blocks.BEDROCK);m.step();ok(m.status==6,"Unbreakable");
        clear();m.setInventorySlotContents(1,new ItemStack(Items.DIAMOND_PICKAXE));block(Blocks.STONE);cancelBreak=true;m.step();cancelBreak=false;ok(m.status==9 && w.getBlockState(t).getBlock()==Blocks.STONE && m.getStackInSlot(1).getItemDamage()==0,"Break cancellation");
        clear();m.mode=1;input(Blocks.OAK_STAIRS);m.yaw=3;m.face=EnumFacing.EAST.getIndex();m.high=1;m.step();IBlockState s=w.getBlockState(t);ok(m.status==2 && s.getValue(BlockStairs.FACING)==EnumFacing.EAST && s.getValue(BlockStairs.HALF)==BlockStairs.EnumHalf.TOP && m.getStackInSlot(0).getCount()==1,"Player facing/hit coords/item consumed");
        clear();m.mode=1;input(Blocks.STONE_SLAB);m.face=EnumFacing.EAST.getIndex();m.high=1;m.step();ok(w.getBlockState(t).getValue(BlockSlab.HALF)==BlockSlab.EnumBlockHalf.TOP,"Player top slab");
        clear();m.mode=1;input(Blocks.STONE_SLAB);m.step();ok(w.getBlockState(t).getValue(BlockSlab.HALF)==BlockSlab.EnumBlockHalf.BOTTOM,"Player bottom slab");
        clear();m.mode=1;input(Blocks.STONE_SLAB);block(Blocks.AIR);w.setBlockState(t.up(),Blocks.STONE_SLAB.getDefaultState(),3);m.step();ok(m.status==20 && w.isAirBlock(t) && m.getStackInSlot(0).getCount()==2,"Adjacent slab merge refused");w.setBlockToAir(t.up());
        clear();m.mode=1;input(Blocks.LEAVES);m.step();ok(!w.getBlockState(t).getValue(BlockLeaves.DECAYABLE),"Player leaves persistence via ItemLeaves metadata");
        clear();m.mode=1;m.context=1;m.metadata=0;input(Blocks.LEAVES);m.step();ok(w.getBlockState(t).getValue(BlockLeaves.DECAYABLE),"Direct natural leaf state");
        clear();m.mode=1;m.context=1;m.metadata=4;input(Blocks.OAK_STAIRS);m.step();ok(w.getBlockState(t).equals(Blocks.OAK_STAIRS.getStateFromMeta(4)),"Direct raw state ignores yaw/hit");
        clear();m.mode=1;input(Blocks.STONE);block(Blocks.DIRT);m.step();ok(m.status==10 && m.getStackInSlot(0).getCount()==2 && w.getBlockState(t).getBlock()==Blocks.DIRT,"Occupied placement intact");
        clear();m.mode=1;input(Blocks.CHEST);m.step();ok(m.status==11 && m.getStackInSlot(0).getCount()==2,"Unsupported inventory item");
        clear();m.mode=1;input(Blocks.STONE);m.getStackInSlot(0).setTagInfo("BlockEntityTag",new NBTTagCompound());m.step();ok(m.status==11 && w.isAirBlock(t),"NBT transplantation rejected");
        for(int context=0;context<=1;context++){clear();m.mode=1;m.context=context;input(Blocks.STONE);cancelPlace=true;m.step();cancelPlace=false;ok(m.status==9 || m.status==12,"Placement cancellation status");ok(w.isAirBlock(t) && m.getStackInSlot(0).getCount()==2,"Placement cancellation rollback context "+context);}
        clear();m.mode=1;input(Blocks.STONE);w.setBlockState(t,Blocks.TALLGRASS.getDefaultState(),3);m.step();ok(m.status==2 && w.getBlockState(t).getBlock()==Blocks.STONE,"Replaceable vanilla target");
        for(Fluid f:new Fluid[]{FluidRegistry.WATER,FluidRegistry.LAVA,MachineStarter.PROOF_FLUID}){
            clear();m.mode=3;m.tank.fill(new FluidStack(f,1000),true);m.step();ok(m.status==2 && m.tank.getFluidAmount()==0,"Pour 1000 "+f.getName());
            m.mode=2;m.step();ok(m.status==2 && w.isAirBlock(t) && m.tank.getFluidAmount()==1000,"Collect 1000 "+f.getName());
        }
        clear();m.mode=2;w.setBlockState(t,Blocks.FLOWING_WATER.getDefaultState().withProperty(BlockLiquid.LEVEL,1),3);m.step();ok(m.status==13 && m.tank.getFluidAmount()==0,"Flowing rejection");
        clear();m.mode=2;block(Blocks.WATER);m.tank.fill(new FluidStack(FluidRegistry.LAVA,1000),true);m.step();ok(m.status==14 && w.getBlockState(t).getMaterial().isLiquid() && m.tank.getFluidAmount()==1000,"Mixed-fluid rejection");
        clear();m.mode=2;block(Blocks.WATER);m.tank.fill(new FluidStack(FluidRegistry.WATER,4000),true);m.step();ok(m.status==14 && m.tank.getFluidAmount()==4000,"Full tank source intact");
        clear();m.mode=3;block(Blocks.STONE);m.tank.fill(new FluidStack(FluidRegistry.WATER,1000),true);m.step();ok(m.status==10 && m.tank.getFluidAmount()==1000,"Pour occupied no consumption");
        clear();m.mode=3;m.tank.fill(new FluidStack(FluidRegistry.WATER,1000),true);cancelPlace=true;m.step();cancelPlace=false;ok(m.status==9 && w.isAirBlock(t) && m.tank.getFluidAmount()==1000,"Fluid placement cancellation");
        for(Fluid f:new Fluid[]{FluidRegistry.WATER,FluidRegistry.LAVA,MachineStarter.PROOF_FLUID}){
            clear();m.mode=4;m.setInventorySlotContents(0,FluidUtil.getFilledBucket(new FluidStack(f,1000)));m.step();ok(m.status==2 && m.getStackInSlot(0).isEmpty() && outputs(Items.BUCKET)==1 && m.tank.getFluidAmount()==1000,"Bucket load conservation "+f.getName());
            m.mode=5;m.setInventorySlotContents(0,new ItemStack(Items.BUCKET));m.step();ok(m.status==2 && m.tank.getFluidAmount()==0 && m.getStackInSlot(0).isEmpty(),"Bucket unload conservation "+f.getName());
        }
        clear();m.mode=4;m.setInventorySlotContents(0,new ItemStack(Items.WATER_BUCKET));for(int i=2;i<8;i++)m.setInventorySlotContents(i,new ItemStack(Items.STICK,64));m.step();ok(m.status==8 && m.getStackInSlot(0).getItem()==Items.WATER_BUCKET && m.tank.getFluidAmount()==0,"Bucket output blocked");
        clear();m.setInventorySlotContents(1,new ItemStack(Items.DIAMOND_PICKAXE));block(Blocks.DIAMOND_ORE);w.getGameRules().setOrCreateGameRule("doTileDrops","false");m.step();ok(m.status==2 && m.xp==0 && outputs(Items.DIAMOND)==0,"doTileDrops honored");w.getGameRules().setOrCreateGameRule("doTileDrops","true");
        // Queued and actually absent chunks must not be touched even by the observer.
        clear();m.setInventorySlotContents(1,new ItemStack(Items.DIAMOND_PICKAXE));block(Blocks.STONE);Chunk c=LoadedAccess.chunk(w,t);c.unloadQueued=true;m.step();ok(m.status==3 && c.unloadQueued,"Pending chunk flag preserved");c.unloadQueued=false;
        BlockPos far=new BlockPos(16000,80,16000);MachineTile detached=new MachineTile();detached.setWorld(w);detached.setPos(far);detached.armed=true;detached.step();ok(detached.status==3 && !((ChunkProviderServer)w.getChunkProvider()).chunkExists(1000,1000),"Absent chunks stay absent");
        // Nether rejection in an isolated dimension fixture, before fluid placement.
        WorldServer nether=DimensionManager.getWorld(-1);if(nether==null){DimensionManager.initDimension(-1);nether=DimensionManager.getWorld(-1);}
        for(int x=-3;x<=3;x++)for(int z=-3;z<=3;z++)nether.getChunk(x,z);
        MachineTile n=new MachineTile();n.setWorld(nether);n.setPos(p);n.armed=true;n.mode=3;n.tank.fill(new FluidStack(FluidRegistry.WATER,1000),true);n.step();ok(n.status==16 && n.tank.getFluidAmount()==1000,"Nether water no loss");
        clear();m.mode=3;m.high=1;m.xp=19;m.tank.fill(new FluidStack(FluidRegistry.WATER,1000),true);input(Blocks.STONE);m.markDirty();w.saveAllChunks(true,null);
        System.out.println("BLOCK_WORLD_PASS checks="+count+" drops/tools/xp placement/states events fluids/buckets chunks/nether");System.out.println("BLOCK_LIVE_PASS");
    }
}
