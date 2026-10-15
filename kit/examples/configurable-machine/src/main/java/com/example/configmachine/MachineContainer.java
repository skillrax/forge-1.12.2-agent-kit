package com.example.configmachine;
import java.util.Arrays;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.IContainerListener;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;

public final class MachineContainer extends Container {
    public final MachineTile tile;
    private final int[] last=new int[20];
    public MachineContainer(InventoryPlayer player,MachineTile tile){
        this.tile=tile;Arrays.fill(last,-1);
        int[][] positions={{20,46},{20,78},{146,46},{146,78},{83,65}};
        for(int i=0;i<5;i++){
            final int slot=i;
            addSlotToContainer(new Slot(tile,i,positions[i][0],positions[i][1]){
                @Override public boolean isItemValid(ItemStack stack){return tile.isItemValidForSlot(slot,stack);}
                @Override public int getSlotStackLimit(){return slot==4?1:64;}
            });
        }
        for(int row=0;row<3;row++)for(int col=0;col<9;col++)addSlotToContainer(new Slot(player,col+row*9+9,91+col*18,156+row*18));
        for(int col=0;col<9;col++)addSlotToContainer(new Slot(player,col,91+col*18,214));
    }
    @Override public boolean canInteractWith(EntityPlayer player){return tile.isUsableByPlayer(player);}
    @Override public boolean enchantItem(EntityPlayer player,int action){
        return !player.world.isRemote && player.openContainer==this && canInteractWith(player) && tile.control(action);
    }
    @Override public void addListener(IContainerListener listener){super.addListener(listener);for(int i=0;i<last.length;i++)listener.sendWindowProperty(this,i,tile.getField(i));}
    @Override public void detectAndSendChanges(){
        super.detectAndSendChanges();for(int i=0;i<last.length;i++){int value=tile.getField(i);if(value!=last[i])for(IContainerListener listener:listeners)listener.sendWindowProperty(this,i,value);last[i]=value;}
    }
    @Override public void updateProgressBar(int id,int value){tile.setField(id,value);}
    @Override public ItemStack transferStackInSlot(EntityPlayer player,int index){
        if(index<0 || index>=inventorySlots.size())return ItemStack.EMPTY;
        Slot slot=inventorySlots.get(index);if(!slot.getHasStack())return ItemStack.EMPTY;
        ItemStack stack=slot.getStack(),original=stack.copy();
        if(index<5){if(!mergeItemStack(stack,5,inventorySlots.size(),true))return ItemStack.EMPTY;}
        else{
            int target=tile.isItemValidForSlot(4,stack)?4:tile.isItemValidForSlot(0,stack)?0:tile.isItemValidForSlot(1,stack)?1:-1;
            if(target<0 || !mergeItemStack(stack,target,target+1,false))return ItemStack.EMPTY;
        }
        if(stack.isEmpty())slot.putStack(ItemStack.EMPTY);else slot.onSlotChanged();
        if(stack.getCount()==original.getCount())return ItemStack.EMPTY;slot.onTake(player,stack);return original;
    }
}
