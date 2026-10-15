package dev.agentic.smelter;

import javax.annotation.Nullable;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.inventory.ISidedInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.tileentity.TileEntityChest;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ITickable;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.energy.CapabilityEnergy;
import net.minecraftforge.items.CapabilityItemHandler;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemHandlerHelper;

public final class SmelterTile extends InventoryTile implements ITickable,ISidedInventory {
    // Role 0 disables a face; roles 1..4 map to slots 0..3. Upgrade slot 4 is GUI-only.
    private final int[] roles={3,1,0,0,2,4}; // EnumFacing order: DOWN UP NORTH SOUTH WEST EAST.
    private final IItemHandler[] ports=new IItemHandler[6];
    // Per-face last transfer result, synchronized for diagnosis; not persistent gameplay state.
    // 0 disabled, 1 auto off, 2 redstone pause, 3 no items, 4 missing chunk,
    // 5 no inventory, 6 no item capability, 7 chest-area guard, 8 rejected, 9 moved.
    private final int[] transferStatus=new int[6];
    public final EnergyBuffer energy=new EnergyBuffer(16000,256,0,this::markDirty);
    private int progress,cadence,status,clientDuration=100;
    private boolean autoInput,autoOutput,runPowered,powered;
    private String operationKey="";
    public long completed,spent;
    public SmelterTile(){super(5);for(EnumFacing f:EnumFacing.values())ports[f.getIndex()]=new FacePort(f);}
    @Override public String getName(){return "tile.smelterproof.smelter.name";}
    public boolean upgraded(){return !getStackInSlot(4).isEmpty() && getStackInSlot(4).getItem()==Content.UPGRADE;}
    public SmeltingOperation operation(){return SmeltingOperation.select(getStackInSlot(0),getStackInSlot(1),upgraded());}
    public int role(EnumFacing face){return roles[face.getIndex()];}
    public boolean control(int action){
        if(action<0 || action>8)return false;
        if(action<6)roles[action]=(roles[action]+1)%5;
        else if(action==6)autoInput=!autoInput;
        else if(action==7)autoOutput=!autoOutput;
        else runPowered=!runPowered;
        markDirty();return true;
    }
    private static boolean same(ItemStack a,ItemStack b){return ItemStack.areItemsEqual(a,b) && ItemStack.areItemStackTagsEqual(a,b);}
    private int capacity(int slot,ItemStack offered){
        if(offered.isEmpty())return 0;
        ItemStack current=getStackInSlot(slot);
        if(!current.isEmpty() && !same(current,offered))return 0;
        return Math.max(0,Math.min(64,offered.getMaxStackSize())-(current.isEmpty()?0:current.getCount()));
    }
    private boolean room(int slot,ItemStack output){return output.isEmpty() || capacity(slot,output)>=output.getCount();}
    private void add(int slot,ItemStack output){if(output.isEmpty())return;if(getStackInSlot(slot).isEmpty())stacks.set(slot,output.copy());else getStackInSlot(slot).grow(output.getCount());}
    private ItemStack insert(int slot,ItemStack offered,boolean simulate){
        if(!isItemValidForSlot(slot,offered))return offered.copy();
        int accepted=Math.min(offered.getCount(),capacity(slot,offered));
        if(!simulate && accepted>0){ItemStack acceptedStack=offered.copy();acceptedStack.setCount(accepted);add(slot,acceptedStack);markDirty();}
        ItemStack rest=offered.copy();rest.shrink(accepted);return rest;
    }
    private final class FacePort implements IItemHandler {
        private final EnumFacing face;
        FacePort(EnumFacing face){this.face=face;}
        public int getSlots(){return 1;}
        private int slot(){return role(face)-1;}
        public ItemStack getStackInSlot(int index){return index==0 && slot()>=0?SmelterTile.this.getStackInSlot(slot()).copy():ItemStack.EMPTY;}
        public ItemStack insertItem(int index,ItemStack stack,boolean simulate){int s=slot();return index==0 && s>=0 && s<2?insert(s,stack,simulate):stack.copy();}
        public ItemStack extractItem(int index,int amount,boolean simulate){
            int s=slot();if(index!=0 || s<2 || amount<=0)return ItemStack.EMPTY;
            ItemStack copy=SmelterTile.this.getStackInSlot(s).copy();copy.setCount(Math.min(copy.getCount(),amount));
            if(!simulate && !copy.isEmpty())decrStackSize(s,copy.getCount());return copy;
        }
        public int getSlotLimit(int index){return index==0 && slot()>=0?64:0;}
    }
    private IItemHandler neighbor(EnumFacing side){
        int index=side.getIndex();BlockPos target=pos.offset(side);
        if(!world.isBlockLoaded(target)){transferStatus[index]=4;return null;}
        TileEntity tile=world.getTileEntity(target);
        if(tile==null){transferStatus[index]=5;return null;}
        if(tile instanceof TileEntityChest && !world.isAreaLoaded(target.add(-3,0,-3),target.add(3,0,3))){transferStatus[index]=7;return null;}
        IItemHandler handler=tile.getCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY,side.getOpposite());
        if(handler==null)transferStatus[index]=6;return handler;
    }
    private void transfer(){
        for(EnumFacing side:EnumFacing.values()){
            int slot=role(side)-1,index=side.getIndex();
            if(slot<0){transferStatus[index]=0;continue;}
            if(slot<2 && !autoInput || slot>=2 && !autoOutput){transferStatus[index]=1;continue;}
            IItemHandler other=neighbor(side);if(other==null)continue;
            if(slot<2){
                transferStatus[index]=3;
                for(int i=0;i<other.getSlots();i++){
                    ItemStack offered=other.extractItem(i,8,true);if(offered.isEmpty())continue;
                    int accepted=offered.getCount()-insert(slot,offered,true).getCount();if(accepted<=0){transferStatus[index]=8;continue;}
                    ItemStack taken=other.extractItem(i,accepted,false);
                    ItemStack remainder=insert(slot,taken,false);
                    // A noncompliant source must not cause silent item loss.
                    if(!remainder.isEmpty())world.spawnEntity(new EntityItem(world,pos.getX()+.5,pos.getY()+1,pos.getZ()+.5,remainder));
                    transferStatus[index]=taken.getCount()>remainder.getCount()?9:8;
                    break;
                }
            }else{
                ItemStack offered=getStackInSlot(slot).copy();if(offered.isEmpty()){transferStatus[index]=3;continue;}offered.setCount(Math.min(8,offered.getCount()));
                ItemStack rest=ItemHandlerHelper.insertItemStacked(other,offered.copy(),false);
                int accepted=offered.getCount()-rest.getCount();transferStatus[index]=accepted>0?9:8;if(accepted>0)decrStackSize(slot,accepted);
            }
        }
    }
    @Override public void update(){
        if(world==null || world.isRemote)return;
        // Redstone probes can read adjacent positions; do not force absent chunks to tick.
        if(!world.isAreaLoaded(pos.add(-1,-1,-1),pos.add(1,1,1))){status=5;java.util.Arrays.fill(transferStatus,4);return;}
        powered=world.isBlockPowered(pos);
        SmeltingOperation operation=operation();
        String key=operation==null?"":operation.key;
        if(!key.equals(operationKey)){progress=0;operationKey=key;markDirty();}
        if(powered!=runPowered){status=1;java.util.Arrays.fill(transferStatus,2);return;}
        if(++cadence>=10){cadence=0;transfer();operation=operation();key=operation==null?"":operation.key;if(!key.equals(operationKey)){progress=0;operationKey=key;markDirty();}}
        if(operation==null){status=2;return;}
        if(!room(2,operation.main) || !room(3,operation.secondary)){status=3;return;}
        if(!energy.spend(operation.rfPerTick)){status=4;return;}
        status=0;spent+=operation.rfPerTick;
        if(++progress>=operation.ticks){
            getStackInSlot(0).shrink(1);if(operation.usesSand)getStackInSlot(1).shrink(1);
            add(2,operation.main);add(3,operation.secondary);progress=0;completed++;
        }
        markDirty();
    }
    @Override public boolean isItemValidForSlot(int slot,ItemStack stack){
        return !stack.isEmpty() && (slot==0 && (SmeltingOperation.furnaceInput(stack) || SmeltingOperation.ore(stack)) || slot==1 && SmeltingOperation.sand(stack) || slot==4 && stack.getItem()==Content.UPGRADE);
    }
    @Override public int[] getSlotsForFace(EnumFacing side){int s=role(side)-1;return s<0?new int[0]:new int[]{s};}
    @Override public boolean canInsertItem(int slot,ItemStack stack,EnumFacing side){return role(side)==slot+1 && slot<2 && isItemValidForSlot(slot,stack);}
    @Override public boolean canExtractItem(int slot,ItemStack stack,EnumFacing side){return role(side)==slot+1 && slot>=2 && slot<4;}
    @Override public boolean hasCapability(Capability<?> capability,@Nullable EnumFacing side){
        if(capability==CapabilityItemHandler.ITEM_HANDLER_CAPABILITY)return side!=null && role(side)>0;
        return capability==CapabilityEnergy.ENERGY || super.hasCapability(capability,side);
    }
    @Override public <T> T getCapability(Capability<T> capability,@Nullable EnumFacing side){
        if(capability==CapabilityItemHandler.ITEM_HANDLER_CAPABILITY)return side!=null && role(side)>0?CapabilityItemHandler.ITEM_HANDLER_CAPABILITY.cast(ports[side.getIndex()]):null;
        if(capability==CapabilityEnergy.ENERGY)return CapabilityEnergy.ENERGY.cast(energy);
        return super.getCapability(capability,side);
    }
    @Override public int getFieldCount(){return 20;}
    @Override public int getField(int id){
        if(id==0)return progress;if(id==1)return energy.getEnergyStored();
        if(id==2){if(world!=null && world.isRemote)return clientDuration;SmeltingOperation r=operation();return r==null?100:r.ticks;}
        if(id==3)return autoInput?1:0;if(id==4)return autoOutput?1:0;if(id==5)return runPowered?1:0;
        if(id>=6 && id<12)return roles[id-6];if(id==12)return status;if(id==13)return powered?1:0;
        if(id>=14 && id<20)return transferStatus[id-14];return 0;
    }
    @Override public void setField(int id,int value){
        if(id==0)progress=Math.max(0,value);else if(id==1)energy.restore(value);else if(id==2)clientDuration=Math.max(1,value);
        else if(id==3)autoInput=value!=0;else if(id==4)autoOutput=value!=0;else if(id==5)runPowered=value!=0;
        else if(id>=6 && id<12)roles[id-6]=Math.max(0,Math.min(4,value));else if(id==12)status=value;else if(id==13)powered=value!=0;
        else if(id>=14 && id<20)transferStatus[id-14]=Math.max(0,Math.min(9,value));
    }
    @Override public NBTTagCompound writeToNBT(NBTTagCompound tag){
        super.writeToNBT(tag);tag.setIntArray("Faces",roles);tag.setBoolean("AutoInput",autoInput);tag.setBoolean("AutoOutput",autoOutput);tag.setBoolean("RunPowered",runPowered);
        tag.setInteger("Energy",energy.getEnergyStored());tag.setInteger("Progress",progress);tag.setString("OperationKey",operationKey);tag.setLong("Completed",completed);tag.setLong("Spent",spent);return tag;
    }
    @Override public void readFromNBT(NBTTagCompound tag){
        super.readFromNBT(tag);int[] saved=tag.getIntArray("Faces");if(saved.length==6)for(int i=0;i<6;i++)roles[i]=Math.max(0,Math.min(4,saved[i]));
        autoInput=tag.getBoolean("AutoInput");autoOutput=tag.getBoolean("AutoOutput");runPowered=tag.getBoolean("RunPowered");energy.restore(tag.getInteger("Energy"));
        SmeltingOperation r=operation();operationKey=tag.getString("OperationKey");progress=r!=null && r.key.equals(operationKey)?Math.max(0,Math.min(r.ticks-1,tag.getInteger("Progress"))):0;
        completed=tag.getLong("Completed");spent=tag.getLong("Spent");
    }
}
