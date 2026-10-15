package dev.agentic.workshop;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.IContainerListener;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;

public final class WorkshopContainer extends Container {
    public final InventoryTile tile;
    private final int machineSlots;
    private int lastProgress=-1;
    public WorkshopContainer(InventoryPlayer player,InventoryTile tile) {
        this.tile=tile;machineSlots=tile.getSizeInventory();
        if(tile instanceof ProcessorTile) {
            addSlotToContainer(new Slot(tile,0,56,35) {
                @Override public boolean isItemValid(ItemStack stack) {return tile.isItemValidForSlot(0,stack);}
            });
            addSlotToContainer(new Slot(tile,1,116,35) {
                @Override public boolean isItemValid(ItemStack stack) {return false;}
            });
        } else for(int i=0;i<9;i++)addSlotToContainer(new Slot(tile,i,62+i%3*18,18+i/3*18));
        for(int row=0;row<3;row++)for(int col=0;col<9;col++)
            addSlotToContainer(new Slot(player,col+row*9+9,8+col*18,84+row*18));
        for(int col=0;col<9;col++)addSlotToContainer(new Slot(player,col,8+col*18,142));
    }
    @Override public boolean canInteractWith(EntityPlayer player) {return tile.isUsableByPlayer(player);}
    @Override public void addListener(IContainerListener listener) {
        super.addListener(listener);listener.sendWindowProperty(this,0,tile.getField(0));
    }
    @Override public void detectAndSendChanges() {
        super.detectAndSendChanges();int progress=tile.getField(0);
        if(progress!=lastProgress)for(IContainerListener listener:listeners)listener.sendWindowProperty(this,0,progress);
        lastProgress=progress;
    }
    @Override public void updateProgressBar(int id,int value) {tile.setField(id,value);}
    @Override public ItemStack transferStackInSlot(EntityPlayer player,int index) {
        if(index<0 || index>=inventorySlots.size())return ItemStack.EMPTY;
        Slot slot=inventorySlots.get(index);if(!slot.getHasStack())return ItemStack.EMPTY;
        ItemStack stack=slot.getStack(),original=stack.copy();
        if(index<machineSlots) {
            if(!mergeItemStack(stack,machineSlots,inventorySlots.size(),true))return ItemStack.EMPTY;
        } else if(tile instanceof ProcessorTile) {
            if(!ProcessorTile.input(stack) || !mergeItemStack(stack,0,1,false))return ItemStack.EMPTY;
        } else if(!mergeItemStack(stack,0,machineSlots,false))return ItemStack.EMPTY;
        if(stack.isEmpty())slot.putStack(ItemStack.EMPTY);else slot.onSlotChanged();
        if(stack.getCount()==original.getCount())return ItemStack.EMPTY;
        slot.onTake(player,stack);return original;
    }
}
