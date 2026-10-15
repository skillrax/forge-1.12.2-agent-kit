package dev.agentic.routing;

import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.tileentity.TileEntityChest;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ITickable;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.items.CapabilityItemHandler;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemHandlerHelper;
import net.minecraftforge.items.ItemStackHandler;

public final class RoutingTile extends TileEntity implements ITickable {
    // These blocks have no comparator output. Vanilla markDirty also sends
    // comparator updates two blocks away, which can load an absent chunk.
    // Mark only our already-loaded chunk for saving.
    @Override public void markDirty(){if(world!=null && !world.isRemote)world.markChunkDirty(pos,this);}
    public final ItemStackHandler buffer=new ItemStackHandler(2) {
        @Override public int getSlotLimit(int slot){return 16;}
        @Override protected void onContentsChanged(int slot){markDirty();}
    };
    private ItemStack filter=new ItemStack(Blocks.COBBLESTONE);
    private int clock,cursor;
    private boolean splitter(){return world!=null && world.getBlockState(pos).getBlock()==RoutingProof.SPLITTER;}
    public void setFilter(ItemStack sample){filter=sample.copy();filter.setCount(1);markDirty();}
    public ItemStack filterCopy(){return filter.copy();}
    private boolean matches(ItemStack stack){return !filter.isEmpty() && stack.getItem()==filter.getItem() && stack.getMetadata()==filter.getMetadata();}
    public String describe(){return splitter()?"Splitter: filter "+filter.getDisplayName()+" (item + metadata). W input; E matching ["+buffer.getStackInSlot(0).getCount()+"/16]; S other ["+buffer.getStackInSlot(1).getCount()+"/16]. Held-item click sets; empty hand views.":"Extractor: W source; E output. Buffer "+buffer.getStackInSlot(0).getCount()+"/16; 8 items / 10 ticks.";}

    // Port views never expose mutable internal stacks. Output ports cannot insert;
    // input ports cannot extract. Splitter routes at insertion, retaining that route
    // for already-buffered stacks even if the player changes the filter.
    private IItemHandler view(final int slot,final boolean input) {
        return new IItemHandler() {
            public int getSlots(){return 1;}
            public int getSlotLimit(int s){check(s);return 16;}
            private void check(int s){if(s!=0)throw new IndexOutOfBoundsException();}
            public ItemStack getStackInSlot(int s){check(s);return input && splitter()?ItemStack.EMPTY:buffer.getStackInSlot(slot).copy();}
            public ItemStack insertItem(int s,ItemStack stack,boolean simulate) {
                check(s);if(!input)return stack;
                return buffer.insertItem(splitter()?(matches(stack)?0:1):0,stack.copy(),simulate);
            }
            public ItemStack extractItem(int s,int amount,boolean simulate){check(s);return input?ItemStack.EMPTY:buffer.extractItem(slot,amount,simulate);}
        };
    }
    @Override public boolean hasCapability(Capability<?> cap,EnumFacing side) {
        if(cap==CapabilityItemHandler.ITEM_HANDLER_CAPABILITY)return side==EnumFacing.WEST || side==EnumFacing.EAST || (splitter() && side==EnumFacing.SOUTH);
        return super.hasCapability(cap,side);
    }
    @Override public <T> T getCapability(Capability<T> cap,EnumFacing side) {
        if(cap==CapabilityItemHandler.ITEM_HANDLER_CAPABILITY) {
            if(side==EnumFacing.WEST)return CapabilityItemHandler.ITEM_HANDLER_CAPABILITY.cast(view(0,true));
            if(side==EnumFacing.EAST)return CapabilityItemHandler.ITEM_HANDLER_CAPABILITY.cast(view(0,false));
            if(splitter() && side==EnumFacing.SOUTH)return CapabilityItemHandler.ITEM_HANDLER_CAPABILITY.cast(view(1,false));
            return null;
        }
        return super.getCapability(cap,side);
    }
    private IItemHandler neighbor(EnumFacing side) {
        BlockPos target=pos.offset(side);
        if(!world.isBlockLoaded(target))return null;
        TileEntity tile=world.getTileEntity(target);
        // Forge's vanilla chest capability searches adjacent blocks for a second
        // half; chest writes also trigger comparator probes two blocks away.
        // Include that half's reach (1 + 2) before invoking either operation.
        // Pause conservatively at partial chunk boundaries rather than load them.
        if(tile instanceof TileEntityChest && !world.isAreaLoaded(target.add(-3,0,-3),target.add(3,0,3)))return null;
        return tile==null?null:tile.getCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY,side.getOpposite());
    }
    private void push(int slot,EnumFacing side) {
        IItemHandler target=neighbor(side);if(target==null)return;
        ItemStack offered=buffer.extractItem(slot,8,true);if(offered.isEmpty())return;
        // Commit receiver insertion first; remove exactly its accepted count.
        // Tick operations are serial on the logical server; failed insertions stay here.
        ItemStack remainder=ItemHandlerHelper.insertItem(target,offered.copy(),false);
        int accepted=offered.getCount()-remainder.getCount();
        if(accepted>0)buffer.extractItem(slot,accepted,false);
    }
    private void pull() {
        IItemHandler source=neighbor(EnumFacing.WEST);if(source==null || source.getSlots()==0)return;
        for(int n=0;n<source.getSlots();n++) {
            int slot=(cursor+n)%source.getSlots();
            ItemStack offered=source.extractItem(slot,8,true);if(offered.isEmpty())continue;
            int accepted=offered.getCount()-buffer.insertItem(0,offered,true).getCount();
            if(accepted==0)continue;
            ItemStack extracted=source.extractItem(slot,accepted,false);
            // Buffer capacity was simulated immediately before this extraction.
            buffer.insertItem(0,extracted,false);cursor=(slot+1)%source.getSlots();markDirty();return;
        }
    }
    @Override public void update() {
        if(world==null || world.isRemote || ++clock<10)return;clock=0;
        push(0,EnumFacing.EAST);
        if(splitter())push(1,EnumFacing.SOUTH);else pull();
    }
    @Override public NBTTagCompound writeToNBT(NBTTagCompound tag) {
        super.writeToNBT(tag);tag.setTag("Buffer",buffer.serializeNBT());tag.setTag("Filter",filter.writeToNBT(new NBTTagCompound()));tag.setInteger("Cursor",cursor);return tag;
    }
    @Override public void readFromNBT(NBTTagCompound tag) {
        super.readFromNBT(tag);buffer.deserializeNBT(tag.getCompoundTag("Buffer"));
        if(tag.hasKey("Filter"))filter=new ItemStack(tag.getCompoundTag("Filter"));cursor=Math.max(0,tag.getInteger("Cursor"));
    }
}
