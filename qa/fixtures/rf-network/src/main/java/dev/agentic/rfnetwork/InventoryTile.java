package dev.agentic.rfnetwork;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.ItemStackHelper;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.NonNullList;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentTranslation;

public abstract class InventoryTile extends TileEntity implements IInventory {
    private final int size;
    protected NonNullList<ItemStack> stacks;
    protected InventoryTile(int size) {this.size=size;stacks=NonNullList.withSize(size,ItemStack.EMPTY);}
    @Override public void markDirty() {if(world!=null && !world.isRemote)world.markChunkDirty(pos,this);}
    @Override public int getSizeInventory() {return size;}
    @Override public boolean isEmpty() {for(ItemStack stack:stacks)if(!stack.isEmpty())return false;return true;}
    @Override public ItemStack getStackInSlot(int slot) {return stacks.get(slot);}
    @Override public ItemStack decrStackSize(int slot,int count) {
        ItemStack result=ItemStackHelper.getAndSplit(stacks,slot,count);if(!result.isEmpty())markDirty();return result;
    }
    @Override public ItemStack removeStackFromSlot(int slot) {
        ItemStack result=ItemStackHelper.getAndRemove(stacks,slot);if(!result.isEmpty())markDirty();return result;
    }
    @Override public void setInventorySlotContents(int slot,ItemStack stack) {
        stacks.set(slot,stack);
        if(stack.getCount()>Math.min(getInventoryStackLimit(),stack.getMaxStackSize()))
            stack.setCount(Math.min(getInventoryStackLimit(),stack.getMaxStackSize()));
        markDirty();
    }
    @Override public int getInventoryStackLimit() {return 64;}
    @Override public boolean isUsableByPlayer(EntityPlayer player) {
        return world!=null && !isInvalid() && world.getTileEntity(pos)==this
            && player.getDistanceSq(pos.getX()+.5,pos.getY()+.5,pos.getZ()+.5)<=64;
    }
    @Override public void openInventory(EntityPlayer player) {}
    @Override public void closeInventory(EntityPlayer player) {}
    @Override public boolean isItemValidForSlot(int slot,ItemStack stack) {return true;}
    @Override public int getField(int id) {return 0;}
    @Override public void setField(int id,int value) {}
    @Override public int getFieldCount() {return 0;}
    @Override public void clear() {stacks.clear();markDirty();}
    @Override public boolean hasCustomName() {return false;}
    @Override public ITextComponent getDisplayName() {return new TextComponentTranslation(getName());}
    @Override public NBTTagCompound writeToNBT(NBTTagCompound tag) {
        super.writeToNBT(tag);ItemStackHelper.saveAllItems(tag,stacks);return tag;
    }
    @Override public void readFromNBT(NBTTagCompound tag) {
        super.readFromNBT(tag);stacks=NonNullList.withSize(size,ItemStack.EMPTY);ItemStackHelper.loadAllItems(tag,stacks);
    }
}
