package dev.agentic.crafting;
import net.minecraft.entity.player.*;
import net.minecraft.inventory.*;
import net.minecraft.item.ItemStack;

public final class ProcessorContainer extends Container {
    public final ProcessorTile tile;
    private final int[] last={-1,-1,-1,-1,-1,-1};
    public ProcessorContainer(InventoryPlayer player,ProcessorTile tile) {
        this.tile=tile;
        for(int i=0;i<9;i++)addSlotToContainer(new Slot(tile.pattern,i,8+i%3*18,43+i/3*18) {
            @Override public boolean isItemValid(ItemStack stack) {return false;}
            @Override public boolean canTakeStack(EntityPlayer player) {return false;}
        });
        for(int i=0;i<9;i++)addSlotToContainer(new Slot(tile,i,82+i%3*18,43+i/3*18));
        for(int i=0;i<10;i++)addSlotToContainer(new Slot(tile,9+i,156+i%5*18,43+i/5*18) {
            @Override public boolean isItemValid(ItemStack stack) {return false;}
        });
        for(int row=0;row<3;row++)for(int col=0;col<9;col++)addSlotToContainer(new Slot(player,col+row*9+9,44+col*18,169+row*18));
        for(int col=0;col<9;col++)addSlotToContainer(new Slot(player,col,44+col*18,227));
    }
    @Override public ItemStack slotClick(int id,int button,ClickType type,EntityPlayer player) {
        if(id>=0 && id<9) {
            // Vanilla suppresses echo slot updates for an accepted predicted click.
            // Predict the ghost copy too; the server independently validates/applies it.
            if(canInteractWith(player) && type==ClickType.PICKUP && (button==0 || button==1))
                tile.select(id,button==1?ItemStack.EMPTY:player.inventory.getItemStack());
            return ItemStack.EMPTY;
        }
        return super.slotClick(id,button,type,player);
    }
    @Override public boolean enchantItem(EntityPlayer player,int button) {
        if(!canInteractWith(player) || button<0 || button>3)return false;
        if(!tile.getWorld().isRemote)tile.control(button);return true;
    }
    @Override public boolean canInteractWith(EntityPlayer player) {return tile.isUsableByPlayer(player);}
    @Override public boolean canDragIntoSlot(Slot slot) {return slot.slotNumber>=9 && super.canDragIntoSlot(slot);}
    @Override public void addListener(IContainerListener listener) {
        super.addListener(listener);for(int i=0;i<tile.getFieldCount();i++)listener.sendWindowProperty(this,i,tile.getField(i));
    }
    @Override public void detectAndSendChanges() {
        super.detectAndSendChanges();for(int i=0;i<tile.getFieldCount();i++) {
            int v=tile.getField(i);if(v!=last[i])for(IContainerListener l:listeners)l.sendWindowProperty(this,i,v);last[i]=v;
        }
    }
    @Override public void updateProgressBar(int id,int value) {tile.setField(id,value);}
    @Override public ItemStack transferStackInSlot(EntityPlayer player,int index) {
        if(index<9 || index>=inventorySlots.size())return ItemStack.EMPTY;
        Slot slot=inventorySlots.get(index);if(!slot.getHasStack())return ItemStack.EMPTY;
        ItemStack s=slot.getStack(),original=s.copy();
        if(index<28){if(!mergeItemStack(s,28,inventorySlots.size(),true))return ItemStack.EMPTY;}
        else if(!mergeItemStack(s,9,18,false))return ItemStack.EMPTY;
        if(s.isEmpty())slot.putStack(ItemStack.EMPTY);else slot.onSlotChanged();
        if(s.getCount()==original.getCount())return ItemStack.EMPTY;
        slot.onTake(player,s);return original;
    }
}
