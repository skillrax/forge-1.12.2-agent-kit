package com.example.networkstarter;
import java.util.Arrays;
import net.minecraft.entity.player.*;
import net.minecraft.inventory.*;
import net.minecraft.item.ItemStack;
public final class MachineContainer extends Container {
    public final EnergyTile tile;
    private final int[] last=new int[32];
    private final int slots;
    public MachineContainer(InventoryPlayer p,EnergyTile t){
        tile=t;slots=t.getSizeInventory();Arrays.fill(last,-1);
        for(int i=0;i<slots;i++){final int s=i;addSlotToContainer(new Slot(t,i,24+i*72,64){public boolean isItemValid(ItemStack v){return t.isItemValidForSlot(s,v);}public int getSlotStackLimit(){return s==1?1:64;}});}
        for(int row=0;row<3;row++)for(int col=0;col<9;col++)addSlotToContainer(new Slot(p,9+col+row*9,8+col*18,142+row*18));
        for(int col=0;col<9;col++)addSlotToContainer(new Slot(p,col,8+col*18,200));
    }
    public boolean canInteractWith(EntityPlayer p){return tile.isUsableByPlayer(p);}
    public void addListener(IContainerListener l){super.addListener(l);for(int i=0;i<32;i++)l.sendWindowProperty(this,i,tile.getField(i));}
    public void detectAndSendChanges(){super.detectAndSendChanges();for(int i=0;i<32;i++){int v=tile.getField(i);if(v!=last[i])for(IContainerListener l:listeners)l.sendWindowProperty(this,i,v);last[i]=v;}}
    public void updateProgressBar(int i,int v){tile.setField(i,v);}
    public ItemStack transferStackInSlot(EntityPlayer p,int index){
        if(index<0 || index>=inventorySlots.size())return ItemStack.EMPTY;Slot s=inventorySlots.get(index);if(!s.getHasStack())return ItemStack.EMPTY;ItemStack stack=s.getStack(),before=stack.copy();
        if(index<slots){if(!mergeItemStack(stack,slots,inventorySlots.size(),true))return ItemStack.EMPTY;}
        else{int target=slots>0 && tile.isItemValidForSlot(1,stack)?1:slots>0 && tile.isItemValidForSlot(0,stack)?0:-1;if(target<0 || !mergeItemStack(stack,target,target+1,false))return ItemStack.EMPTY;}
        if(stack.isEmpty())s.putStack(ItemStack.EMPTY);else s.onSlotChanged();if(stack.getCount()==before.getCount())return ItemStack.EMPTY;s.onTake(p,stack);return before;
    }
}
