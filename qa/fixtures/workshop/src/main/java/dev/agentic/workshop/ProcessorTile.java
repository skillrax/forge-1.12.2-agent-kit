package dev.agentic.workshop;

import net.minecraft.init.Blocks;
import net.minecraft.inventory.ISidedInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ITickable;

public final class ProcessorTile extends InventoryTile implements ITickable,ISidedInventory {
    public static final int DURATION=100;
    private int progress;
    public ProcessorTile() {super(2);}
    @Override public String getName() {return "tile.kitworkshop.processor.name";}
    public static boolean input(ItemStack stack) {return !stack.isEmpty() && stack.getItem()==Item.getItemFromBlock(Blocks.COBBLESTONE);}
    public boolean canProcess() {
        ItemStack output=getStackInSlot(1);
        return input(getStackInSlot(0)) && (output.isEmpty() ||
            (output.getItem()==Item.getItemFromBlock(Blocks.STONE) && output.getMetadata()==0 && !output.hasTagCompound()
                && output.getCount()<output.getMaxStackSize()));
    }
    @Override public void update() {
        if(world==null || world.isRemote)return;
        if(!input(getStackInSlot(0))) {if(progress!=0){progress=0;markDirty();}return;}
        if(!canProcess())return;
        if(++progress>=DURATION) {
            getStackInSlot(0).shrink(1);
            if(getStackInSlot(1).isEmpty())stacks.set(1,new ItemStack(Blocks.STONE));
            else getStackInSlot(1).grow(1);
            progress=0;
        }
        markDirty();
    }
    @Override public boolean isItemValidForSlot(int slot,ItemStack stack) {return slot==0 && input(stack);}
    @Override public int[] getSlotsForFace(EnumFacing face) {return face==EnumFacing.DOWN?new int[]{1}:new int[]{0};}
    @Override public boolean canInsertItem(int slot,ItemStack stack,EnumFacing face) {return face!=EnumFacing.DOWN && isItemValidForSlot(slot,stack);}
    @Override public boolean canExtractItem(int slot,ItemStack stack,EnumFacing face) {return slot==1 && face==EnumFacing.DOWN;}
    @Override public int getField(int id) {return id==0?progress:0;}
    @Override public void setField(int id,int value) {if(id==0)progress=Math.max(0,Math.min(DURATION-1,value));}
    @Override public int getFieldCount() {return 1;}
    @Override public NBTTagCompound writeToNBT(NBTTagCompound tag) {super.writeToNBT(tag);tag.setInteger("Progress",progress);return tag;}
    @Override public void readFromNBT(NBTTagCompound tag) {super.readFromNBT(tag);setField(0,tag.getInteger("Progress"));}
}
