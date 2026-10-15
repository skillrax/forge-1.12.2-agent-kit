package dev.agentic.blockactions;
import java.util.*;
import net.minecraft.block.*;
import net.minecraft.block.state.IBlockState;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.init.*;
import net.minecraft.item.*;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.*;
import net.minecraft.util.math.*;
import net.minecraft.world.*;
import net.minecraftforge.common.*;
import net.minecraftforge.event.ForgeEventFactory;
import net.minecraftforge.common.util.*;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.fluids.*;
import net.minecraftforge.fml.common.eventhandler.Event;
public final class MachineTile extends InventoryTile {
    public static final String[] MODES={"Mine","Place","Collect source","Pour source","Load bucket","Unload bucket"};
    public static final String[] POLICIES={"Ordinary","Silk touch","Fortune I","Fortune II","Fortune III"};
    public static final String[] STATUS={"Paused","Ready","Done","Unsafe height/border/chunk halo","Unsupported block","Tile entity rejected","Unbreakable","Tool cannot harvest","Output full","Forge event canceled","Occupied target","Unsupported item / NBT","Item-use refused / canceled","Flowing fluid rejected","Tank full / incompatible","Need 1000 mB","Water rejected in Nether","Input bucket required","XP counter full","Nested capture rejected","Adjacent slab merge rejected","Protected target"};
    private static final Set<Block> BLOCKS=new HashSet<>(Arrays.asList(Blocks.STONE,Blocks.COBBLESTONE,Blocks.DIRT,Blocks.GRASS,Blocks.SAND,Blocks.GRAVEL,Blocks.PLANKS,Blocks.LOG,Blocks.LOG2,Blocks.GLASS,Blocks.OBSIDIAN,Blocks.COAL_ORE,Blocks.IRON_ORE,Blocks.GOLD_ORE,Blocks.DIAMOND_ORE,Blocks.EMERALD_ORE,Blocks.LAPIS_ORE,Blocks.REDSTONE_ORE,Blocks.LIT_REDSTONE_ORE,Blocks.QUARTZ_ORE,Blocks.LEAVES,Blocks.LEAVES2,Blocks.STONE_SLAB,Blocks.WOODEN_SLAB,Blocks.OAK_STAIRS,Blocks.STONE_STAIRS,Blocks.BRICK_STAIRS));
    public int mode,direction=EnumFacing.EAST.getIndex(),policy,context,yaw,face=EnumFacing.UP.getIndex(),high,metadata,status,xp;
    public boolean armed;
    public final FluidTank tank=new FluidTank(4000);
    private int clientAmount,clientFluid;
    public MachineTile(){super(8);}
    @Override public String getName(){return "tile.blockactions.machine.name";}
    @Override public boolean isItemValidForSlot(int slot,ItemStack stack){return slot<2;}
    public BlockPos target(){return pos.offset(EnumFacing.byIndex(direction));}
    public static int fluidId(Fluid f){return f==FluidRegistry.WATER?1:f==FluidRegistry.LAVA?2:f==MachineStarter.PROOF_FLUID?3:0;}
    public static Fluid fluid(int id){return id==1?FluidRegistry.WATER:id==2?FluidRegistry.LAVA:id==3?MachineStarter.PROOF_FLUID:null;}
    @Override public int getFieldCount(){return 14;}
    @Override public int getField(int i){int[] v={mode,direction,policy,context,yaw,face,high,metadata,armed?1:0,status,tank.getFluidAmount(),tank.getFluid()==null?0:fluidId(tank.getFluid().getFluid()),xp&32767,(xp>>>15)&32767};return v[i];}
    @Override public void setField(int i,int v){switch(i){
        case 0:mode=v;break;case 1:direction=v;break;case 2:policy=v;break;case 3:context=v;break;case 4:yaw=v;break;case 5:face=v;break;case 6:high=v;break;case 7:metadata=v;break;case 8:armed=v!=0;break;case 9:status=v;break;
        case 10:clientAmount=v;syncTank();break;case 11:clientFluid=v;syncTank();break;
        case 12:xp=(xp&~32767)|(v&32767);break;case 13:xp=(xp&32767)|((v&32767)<<15);break;default:break;}}
    private void syncTank(){Fluid f=fluid(clientFluid);tank.setFluid(f==null?null:new FluidStack(f,clientAmount));}
    public void control(int id){
        if(world==null || world.isRemote || LoadedAccess.chunk(world,pos)==null)return;
        switch(id){case 0:armed=!armed;status=armed?1:0;break;case 1:mode=(mode+1)%6;break;case 2:direction=(direction+1)%6;break;case 3:policy=(policy+1)%5;break;case 4:context=1-context;break;case 5:yaw=(yaw+1)%4;break;case 6:face=(face+1)%6;break;case 7:high=1-high;break;case 8:metadata=(metadata+1)%16;break;case 9:step();break;default:return;}markDirty();
        IBlockState own=world.getBlockState(pos);world.notifyBlockUpdate(pos,own,own,2);
    }
    private void fail(int s){status=s;}
    private FakePlayer player(BlockPos t){
        FakePlayer p=new MachineActor((WorldServer)world,pos);
        p.interactionManager.setGameType(GameType.SURVIVAL);p.inventory.clear();p.setPosition(t.getX()+.5,t.getY()-1,t.getZ()+.5);p.rotationYaw=yaw*90;p.rotationPitch=0;p.setSneaking(false);return p;
    }
    public void step(){
        if(world==null || world.isRemote)return;
        if(!armed){fail(0);return;}
        BlockPos t=target();
        if(!LoadedAccess.safe(world,pos)||!LoadedAccess.safe(world,t)){fail(3);return;}
        if(world.captureBlockSnapshots || !world.capturedBlockSnapshots.isEmpty() || MachineStarter.MACHINE.capturing()){fail(19);return;}
        FakePlayer p=player(t);
        try{
            p.setHeldItem(EnumHand.MAIN_HAND,(mode==0?stacks.get(1):mode==1?stacks.get(0):ItemStack.EMPTY).copy());
            if(mode<4 && (!world.isBlockModifiable(p,t) || !p.canPlayerEdit(t,EnumFacing.byIndex(face),p.getHeldItemMainhand()))){fail(21);return;}
            if(mode==0)mine(t,p);else if(mode==1)place(t,p);else if(mode==2)collect(t,p);else if(mode==3)pour(t,p);else bucket(mode==4);
        }
        finally{p.inventory.clear();p.setSneaking(false);markDirty();}
    }
    private boolean output(List<ItemStack> items,boolean commit){
        List<ItemStack> planned=new ArrayList<>();for(int i=2;i<8;i++)planned.add(stacks.get(i).copy());
        for(ItemStack item:items){ItemStack rest=item.copy();
            for(ItemStack dst:planned)if(!dst.isEmpty() && ItemStack.areItemsEqual(dst,rest) && ItemStack.areItemStackTagsEqual(dst,rest)){int n=Math.min(rest.getCount(),dst.getMaxStackSize()-dst.getCount());dst.grow(n);rest.shrink(n);}
            for(int i=0;i<planned.size() && !rest.isEmpty();i++)if(planned.get(i).isEmpty()){ItemStack dst=rest.copy();dst.setCount(Math.min(rest.getCount(),rest.getMaxStackSize()));planned.set(i,dst);rest.shrink(dst.getCount());}
            if(!rest.isEmpty())return false;
        }
        if(commit)for(int i=0;i<planned.size();i++)stacks.set(i+2,planned.get(i));return true;
    }
    private void mine(BlockPos t,FakePlayer p){
        IBlockState s=world.getBlockState(t);Block b=s.getBlock();
        if(b.hasTileEntity(s)){fail(5);return;}if(s.getBlockHardness(world,t)<0){fail(6);return;}if(!BLOCKS.contains(b)){fail(4);return;}
        ItemStack actual=stacks.get(1),tool=actual.copy();
        if(!tool.isEmpty() && (!"minecraft".equals(tool.getItem().getRegistryName().getNamespace()) || !(tool.getItem() instanceof ItemTool || tool.getItem()==Items.SHEARS))){fail(11);return;}
        Map<net.minecraft.enchantment.Enchantment,Integer> ench=EnchantmentHelper.getEnchantments(tool);ench.remove(Enchantments.SILK_TOUCH);ench.remove(Enchantments.FORTUNE);
        if(policy==1 && !tool.isEmpty())ench.put(Enchantments.SILK_TOUCH,1);if(policy>=2 && !tool.isEmpty())ench.put(Enchantments.FORTUNE,policy-1);if(!tool.isEmpty())EnchantmentHelper.setEnchantments(ench,tool);
        p.setHeldItem(EnumHand.MAIN_HAND,tool);
        if(!b.canHarvestBlock(world,t,p) || (policy!=0 && tool.isEmpty())){fail(7);return;}
        if(!tool.isEmpty() && tool.getItem().onBlockStartBreak(tool,t,p)){fail(12);return;}
        int experience=ForgeHooks.onBlockBreakEvent(world,GameType.SURVIVAL,p,t);
        if(experience<0){fail(9);return;}
        if(!world.getGameRules().getBoolean("doTileDrops"))experience=0;
        if((long)xp+experience>1073741823L){fail(18);return;}
        List<ItemStack> drops;MachineStarter.MACHINE.capture(true);
        try{b.harvestBlock(world,p,t,s,null,tool);}finally{drops=new ArrayList<>(MachineStarter.MACHINE.capture(false));}
        if(!output(drops,false)){fail(8);return;}
        if(!world.getBlockState(t).equals(s)){fail(12);return;}
        if(!b.removedByPlayer(s,world,t,p,true)){fail(12);return;}b.onPlayerDestroy(world,t,s);
        output(drops,true);xp+=experience;
        if(!actual.isEmpty()){
            ItemStack before=actual.copy();actual.onBlockDestroyed(world,s,t,p);
            if(actual.isEmpty())ForgeEventFactory.onPlayerDestroyItem(p,before,EnumHand.MAIN_HAND);
        }
        world.playEvent(2001,t,Block.getStateId(s));status=2;
    }
    private boolean supported(ItemStack in){
        if(in.isEmpty() || !(in.getItem() instanceof ItemBlock) || in.getSubCompound("BlockEntityTag")!=null || in.getSubCompound("BlockStateTag")!=null)return false;
        Block b=((ItemBlock)in.getItem()).getBlock();return BLOCKS.contains(b) && !b.hasTileEntity(b.getDefaultState());
    }
    private boolean empty(BlockPos t){IBlockState s=world.getBlockState(t);return !s.getBlock().hasTileEntity(s) && "minecraft".equals(s.getBlock().getRegistryName().getNamespace()) && !s.getMaterial().isLiquid() && s.getBlock().isReplaceable(world,t);}
    private void place(BlockPos t,FakePlayer p){
        ItemStack in=stacks.get(0);if(!supported(in)){fail(11);return;}if(!empty(t)){fail(10);return;}
        ItemBlock item=(ItemBlock)in.getItem();EnumFacing side=EnumFacing.byIndex(face);ItemStack held=in.copy();p.setHeldItem(EnumHand.MAIN_HAND,held);float y=high==0?.25F:.75F;
        if(context==0){
            // ItemSlab tries merging the face-adjacent slab before ordinary ItemBlock placement.
            if(item instanceof ItemSlab && world.getBlockState(t.offset(side)).getBlock()==item.getBlock()){fail(20);return;}
            PlayerInteractEvent.RightClickBlock e=ForgeHooks.onRightClickBlock(p,EnumHand.MAIN_HAND,t,side,new Vec3d(t.getX()+.5,t.getY()+y,t.getZ()+.5));
            if(e.isCanceled() || e.getUseItem()==Event.Result.DENY){fail(9);return;}
            EnumActionResult r=held.onItemUse(p,world,t,EnumHand.MAIN_HAND,side,.5F,y,.5F);
            if(r!=EnumActionResult.SUCCESS || held.getCount()!=in.getCount()-1){fail(12);return;}stacks.set(0,held);status=2;
        }else{
            IBlockState s=item.getBlock().getStateFromMeta(metadata);if(!world.mayPlace(item.getBlock(),t,false,side,p)){fail(12);return;}
            if(!direct(t,s,p,side))return;in.shrink(1);status=2;
        }
    }
    private void collect(BlockPos t,FakePlayer p){
        IBlockState s=world.getBlockState(t);Fluid f=FluidRegistry.lookupFluidForBlock(s.getBlock());if(fluidId(f)==0){fail(4);return;}
        if(s.getBlock() instanceof BlockLiquid){if(s.getValue(BlockLiquid.LEVEL)!=0){fail(13);return;}}
        else if(s.getBlock()!=MachineStarter.FLUID_BLOCK || !MachineStarter.FLUID_BLOCK.isSourceBlock(world,t)){fail(13);return;}
        FluidStack volume=new FluidStack(f,1000);if(tank.fill(volume,false)!=1000){fail(14);return;}
        if(ForgeHooks.onBlockBreakEvent(world,GameType.SURVIVAL,p,t)<0){fail(9);return;}
        if(!world.setBlockToAir(t)){fail(12);return;}tank.fill(volume,true);status=2;
    }
    private void pour(BlockPos t,FakePlayer p){
        if(tank.getFluidAmount()<1000){fail(15);return;}Fluid f=tank.getFluid().getFluid();if(fluidId(f)==0){fail(4);return;}
        if(f==FluidRegistry.WATER && world.provider.doesWaterVaporize()){fail(16);return;}if(!world.isAirBlock(t)){fail(10);return;}
        IBlockState s=(f==FluidRegistry.WATER?Blocks.FLOWING_WATER:f==FluidRegistry.LAVA?Blocks.FLOWING_LAVA:MachineStarter.FLUID_BLOCK).getDefaultState();
        if(!direct(t,s,p,EnumFacing.byIndex(face)))return;tank.drain(1000,true);status=2;
    }
    private boolean direct(BlockPos t,IBlockState s,FakePlayer p,EnumFacing side){
        if(!world.capturedBlockSnapshots.isEmpty()){fail(19);return false;}
        BlockSnapshot snap=BlockSnapshot.getBlockSnapshot(world,t);
        boolean changed;
        world.captureBlockSnapshots=true;
        try{changed=world.setBlockState(t,s,3);}finally{world.captureBlockSnapshots=false;world.capturedBlockSnapshots.clear();}
        if(!changed){fail(12);return false;}
        if(ForgeEventFactory.onPlayerBlockPlace(p,snap,side,EnumHand.MAIN_HAND).isCanceled()){
            world.restoringBlockSnapshots=true;
            try{snap.restore(true,false);}finally{world.restoringBlockSnapshots=false;}
            fail(9);return false;
        }
        // Deliberate direct placement omits getStateForPlacement/onBlockPlacedBy/NBT merge.
        s.getBlock().onBlockAdded(world,t,s);
        world.markAndNotifyBlock(t,LoadedAccess.chunk(world,t),snap.getReplacedBlock(),s,3);return true;
    }
    private void bucket(boolean load){
        ItemStack in=stacks.get(0);FluidStack f=FluidUtil.getFluidContained(in);ItemStack result;
        if(load){if(f==null || f.amount!=1000 || fluidId(f.getFluid())==0 || in.getCount()!=1 || (in.getItem()!=Items.WATER_BUCKET && in.getItem()!=Items.LAVA_BUCKET && in.getItem()!=ForgeModContainer.getInstance().universalBucket)){fail(17);return;}if(tank.fill(f,false)!=1000){fail(14);return;}result=new ItemStack(Items.BUCKET);}
        else{if(in.isEmpty() || in.getItem()!=Items.BUCKET){fail(17);return;}if(tank.getFluidAmount()<1000){fail(15);return;}result=FluidUtil.getFilledBucket(new FluidStack(tank.getFluid().getFluid(),1000));if(result.isEmpty()){fail(11);return;}}
        if(!output(Collections.singletonList(result),false)){fail(8);return;}if(load)tank.fill(f,true);else tank.drain(1000,true);in.shrink(1);output(Collections.singletonList(result),true);status=2;
    }
    @Override public NBTTagCompound writeToNBT(NBTTagCompound n){super.writeToNBT(n);n.setInteger("Mode",mode);n.setInteger("Direction",direction);n.setInteger("Policy",policy);n.setInteger("Context",context);n.setInteger("Yaw",yaw);n.setInteger("Face",face);n.setInteger("High",high);n.setInteger("Metadata",metadata);n.setInteger("XP",xp);n.setTag("Tank",tank.writeToNBT(new NBTTagCompound()));return n;}
    @Override public void readFromNBT(NBTTagCompound n){super.readFromNBT(n);mode=Math.floorMod(n.getInteger("Mode"),6);direction=Math.floorMod(n.getInteger("Direction"),6);policy=Math.floorMod(n.getInteger("Policy"),5);context=n.getInteger("Context")&1;yaw=n.getInteger("Yaw")&3;face=Math.floorMod(n.getInteger("Face"),6);high=n.getInteger("High")&1;metadata=n.getInteger("Metadata")&15;xp=Math.max(0,Math.min(1073741823,n.getInteger("XP")));tank.readFromNBT(n.getCompoundTag("Tank"));armed=false;status=0;}
    @Override public NBTTagCompound getUpdateTag(){NBTTagCompound n=writeToNBT(new NBTTagCompound());n.setBoolean("DisplayArmed",armed);n.setInteger("DisplayStatus",status);return n;}
    @Override public net.minecraft.network.play.server.SPacketUpdateTileEntity getUpdatePacket(){return new net.minecraft.network.play.server.SPacketUpdateTileEntity(pos,0,getUpdateTag());}
    @Override public void onDataPacket(net.minecraft.network.NetworkManager manager,net.minecraft.network.play.server.SPacketUpdateTileEntity packet){handleUpdateTag(packet.getNbtCompound());}
    @Override public void handleUpdateTag(NBTTagCompound n){readFromNBT(n);armed=n.getBoolean("DisplayArmed");status=n.getInteger("DisplayStatus");}
}
