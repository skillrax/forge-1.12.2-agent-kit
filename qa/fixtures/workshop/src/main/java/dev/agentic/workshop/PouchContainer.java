package dev.agentic.workshop;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.ClickType;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;

public final class PouchContainer extends Container {
    public final PouchInventory pouch;
    private final int locked;
    public PouchContainer(EntityPlayer player,int heldSlot) {
        pouch=new PouchInventory(player,heldSlot);locked=3+27+heldSlot;
        for(int i=0;i<3;i++)addSlotToContainer(new Slot(pouch,i,62+i*18,35) {
            @Override public boolean isItemValid(ItemStack stack) {return PouchInventory.allowed(stack);}
        });
        for(int row=0;row<3;row++)for(int col=0;col<9;col++)
            addSlotToContainer(new Slot(player.inventory,col+row*9+9,8+col*18,84+row*18));
        for(int col=0;col<9;col++) {
            final boolean active=col==heldSlot;
            addSlotToContainer(new Slot(player.inventory,col,8+col*18,142) {
                @Override public boolean canTakeStack(EntityPlayer player) {return !active;}
                @Override public boolean isItemValid(ItemStack stack) {return !active;}
            });
        }
    }
    @Override public boolean canInteractWith(EntityPlayer player) {return pouch.isUsableByPlayer(player);}
    @Override public ItemStack slotClick(int slot,int button,ClickType type,EntityPlayer player) {
        if(!canInteractWith(player) || slot==locked || (type==ClickType.SWAP && button==pouch.heldSlot))return ItemStack.EMPTY;
        return super.slotClick(slot,button,type,player);
    }
    @Override public ItemStack transferStackInSlot(EntityPlayer player,int index) {
        if(!canInteractWith(player) || index<0 || index>=inventorySlots.size() || index==locked)return ItemStack.EMPTY;
        Slot slot=inventorySlots.get(index);if(!slot.getHasStack())return ItemStack.EMPTY;
        ItemStack stack=slot.getStack(),before=stack.copy();
        if(index<3) {
            if(!mergeItemStack(stack,3,inventorySlots.size(),true))return ItemStack.EMPTY;
        }else if(!PouchInventory.allowed(stack) || !mergeItemStack(stack,0,3,false))return ItemStack.EMPTY;
        if(stack.isEmpty())slot.putStack(ItemStack.EMPTY);else slot.onSlotChanged();
        if(stack.getCount()==before.getCount())return ItemStack.EMPTY;
        slot.onTake(player,stack);return before;
    }
}
