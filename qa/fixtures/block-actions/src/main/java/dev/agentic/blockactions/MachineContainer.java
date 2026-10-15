package dev.agentic.blockactions;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.IContainerListener;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;

public final class MachineContainer extends Container {
    public final MachineTile tile;
    private final int[] last;
    public MachineContainer(InventoryPlayer player,MachineTile tile) {
        this.tile=tile;last=new int[tile.getFieldCount()];java.util.Arrays.fill(last,-1);
        for(int i=0;i<8;i++) {
            final int index=i;
            addSlotToContainer(new Slot(tile,i,i<2?12+i*28:108+(i-2)*20,160) {
                @Override public boolean isItemValid(ItemStack stack) {return tile.isItemValidForSlot(index,stack);}
            });
        }
        for(int row=0;row<3;row++)for(int col=0;col<9;col++)addSlotToContainer(new Slot(player,col+row*9+9,70+col*18,198+row*18));
        for(int col=0;col<9;col++)addSlotToContainer(new Slot(player,col,70+col*18,256));
    }
    @Override public boolean canInteractWith(EntityPlayer player) {return tile.isUsableByPlayer(player);}
    @Override public boolean enchantItem(EntityPlayer player,int id) {
        if(!canInteractWith(player) || id<0 || id>9)return false;tile.control(id);return true;
    }
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
        if(index<8) {if(!mergeItemStack(stack,8,inventorySlots.size(),true))return ItemStack.EMPTY;}
        else {int dest=stack.getItem() instanceof net.minecraft.item.ItemTool || stack.getItem()==net.minecraft.init.Items.SHEARS?1:0;
            if(!mergeItemStack(stack,dest,dest+1,false))return ItemStack.EMPTY;}
        if(stack.isEmpty())slot.putStack(ItemStack.EMPTY);else slot.onSlotChanged();
        if(stack.getCount()==original.getCount())return ItemStack.EMPTY;
        slot.onTake(player,stack);return original;
    }
}
