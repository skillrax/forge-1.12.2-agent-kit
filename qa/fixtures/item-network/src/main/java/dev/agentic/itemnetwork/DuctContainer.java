package dev.agentic.itemnetwork;
import net.minecraft.entity.player.*;
import net.minecraft.inventory.*;
import net.minecraft.item.ItemStack;
public final class DuctContainer extends Container {
    public final DuctTile tile;private final int[] last=new int[7];
    public DuctContainer(InventoryPlayer p,DuctTile t){
        tile=t;java.util.Arrays.fill(last,-1);
        addSlotToContainer(new Slot(t.filter,0,12,28){@Override public boolean isItemValid(ItemStack s){return false;}@Override public boolean canTakeStack(EntityPlayer p){return false;}});
        addSlotToContainer(new Slot(t,0,186,28){@Override public boolean isItemValid(ItemStack s){return false;}});
        for(int r=0;r<3;r++)for(int c=0;c<9;c++)addSlotToContainer(new Slot(p,c+r*9+9,30+c*18,162+r*18));
        for(int c=0;c<9;c++)addSlotToContainer(new Slot(p,c,30+c*18,220));
    }
    @Override public boolean canInteractWith(EntityPlayer p){return tile.isUsableByPlayer(p);}
    @Override public ItemStack slotClick(int id,int button,ClickType type,EntityPlayer p){
        if(id==0){if(canInteractWith(p) && type==ClickType.PICKUP && (button==0 || button==1))tile.select(button==1?ItemStack.EMPTY:p.inventory.getItemStack());return ItemStack.EMPTY;}
        return super.slotClick(id,button,type,p);
    }
    @Override public boolean enchantItem(EntityPlayer p,int b){if(!canInteractWith(p) || b<0 || b>4)return false;if(!tile.getWorld().isRemote)tile.control(b);return true;}
    @Override public boolean canDragIntoSlot(Slot s){return s.slotNumber>=2 && super.canDragIntoSlot(s);}
    @Override public void addListener(IContainerListener l){super.addListener(l);for(int i=0;i<7;i++)l.sendWindowProperty(this,i,tile.getField(i));}
    @Override public void detectAndSendChanges(){super.detectAndSendChanges();for(int i=0;i<7;i++){int n=tile.getField(i);if(n!=last[i])for(IContainerListener l:listeners)l.sendWindowProperty(this,i,n);last[i]=n;}}
    @Override public void updateProgressBar(int id,int n){tile.setField(id,n);}
    @Override public ItemStack transferStackInSlot(EntityPlayer p,int i){
        if(i!=1)return ItemStack.EMPTY;Slot slot=getSlot(i);ItemStack s=slot.getStack(),copy=s.copy();if(s.isEmpty() || !mergeItemStack(s,2,inventorySlots.size(),true))return ItemStack.EMPTY;
        if(s.isEmpty())slot.putStack(ItemStack.EMPTY);else slot.onSlotChanged();slot.onTake(p,s);return copy;
    }
}
