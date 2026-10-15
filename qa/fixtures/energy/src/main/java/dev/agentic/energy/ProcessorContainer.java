package dev.agentic.energy;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.IContainerListener;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;

public final class ProcessorContainer extends Container {
    public final ProcessorTile tile;
    private final int[] last={-1,-1,-1};
    public ProcessorContainer(InventoryPlayer player,ProcessorTile tile) {
        this.tile=tile;
        for(int i=0;i<3;i++) {
            final int index=i;
            addSlotToContainer(new Slot(tile,i,i==0?40:106+(i-1)*22,35) {
                @Override public boolean isItemValid(ItemStack stack) {return tile.isItemValidForSlot(index,stack);}
            });
        }
        for(int row=0;row<3;row++)for(int col=0;col<9;col++)addSlotToContainer(new Slot(player,col+row*9+9,8+col*18,84+row*18));
        for(int col=0;col<9;col++)addSlotToContainer(new Slot(player,col,8+col*18,142));
    }
    @Override public boolean canInteractWith(EntityPlayer player) {return tile.isUsableByPlayer(player);}
    @Override public void addListener(IContainerListener listener) {
        super.addListener(listener);for(int id=0;id<tile.getFieldCount();id++)listener.sendWindowProperty(this,id,tile.getField(id));
    }
    @Override public void detectAndSendChanges() {
        super.detectAndSendChanges();
        for(int id=0;id<tile.getFieldCount();id++) {
            int value=tile.getField(id);
            if(value!=last[id])for(IContainerListener listener:listeners)listener.sendWindowProperty(this,id,value);
            last[id]=value;
        }
    }
    @Override public void updateProgressBar(int id,int value) {tile.setField(id,value);}
    @Override public ItemStack transferStackInSlot(EntityPlayer player,int index) {
        if(index<0 || index>=inventorySlots.size())return ItemStack.EMPTY;
        Slot slot=inventorySlots.get(index);if(!slot.getHasStack())return ItemStack.EMPTY;
        ItemStack stack=slot.getStack(),original=stack.copy();
        if(index<3) {if(!mergeItemStack(stack,3,inventorySlots.size(),true))return ItemStack.EMPTY;}
        else if(!tile.isItemValidForSlot(0,stack) || !mergeItemStack(stack,0,1,false))return ItemStack.EMPTY;
        if(stack.isEmpty())slot.putStack(ItemStack.EMPTY);else slot.onSlotChanged();
        if(stack.getCount()==original.getCount())return ItemStack.EMPTY;
        slot.onTake(player,stack);return original;
    }
}
