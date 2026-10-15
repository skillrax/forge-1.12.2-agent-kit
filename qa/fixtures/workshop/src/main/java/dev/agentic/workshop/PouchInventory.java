package dev.agentic.workshop;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.InventoryBasic;
import net.minecraft.inventory.ItemStackHelper;
import net.minecraft.item.ItemShulkerBox;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.NonNullList;

/** One menu session bound to the exact server stack; no singleton item state. */
public final class PouchInventory extends InventoryBasic {
    private ItemStack bound;
    public final EntityPlayer owner;
    public final int heldSlot;
    public PouchInventory(EntityPlayer owner,int slot) {
        super("item.kitworkshop.pouch.name",false,3);this.owner=owner;heldSlot=slot;
        ItemStack stack=owner.inventory.getStackInSlot(slot);
        NonNullList<ItemStack> saved=NonNullList.withSize(3,ItemStack.EMPTY);
        if(stack.hasTagCompound())ItemStackHelper.loadAllItems(stack.getTagCompound().getCompoundTag("PouchContents"),saved);
        // Loading must not write a partially reconstructed inventory over the saved compound.
        for(int i=0;i<3;i++)super.setInventorySlotContents(i,saved.get(i));
        bound=stack;
    }
    public static boolean allowed(ItemStack stack) {
        return stack.getItem()!=Content.POUCH && !(stack.getItem() instanceof ItemShulkerBox);
    }
    @Override public boolean isItemValidForSlot(int slot,ItemStack stack) {return allowed(stack);}
    @Override public ItemStack removeStackFromSlot(int slot) {
        ItemStack result=super.removeStackFromSlot(slot);markDirty();return result;
    }
    @Override public void clear() {super.clear();markDirty();}
    @Override public boolean isUsableByPlayer(EntityPlayer player) {
        ItemStack current=owner.inventory.getStackInSlot(heldSlot);
        return player==owner && owner.inventory.currentItem==heldSlot && current.getItem()==Content.POUCH
            && current.getCount()==1 && (owner.world.isRemote || current==bound);
    }
    @Override public void markDirty() {
        super.markDirty();
        if(bound==null || owner.world.isRemote || !isUsableByPlayer(owner))return;
        NonNullList<ItemStack> contents=NonNullList.withSize(3,ItemStack.EMPTY);
        for(int i=0;i<3;i++)contents.set(i,getStackInSlot(i));
        NBTTagCompound saved=new NBTTagCompound();ItemStackHelper.saveAllItems(saved,contents);
        if(!bound.hasTagCompound())bound.setTagCompound(new NBTTagCompound());
        bound.getTagCompound().setTag("PouchContents",saved);owner.inventory.markDirty();
    }
}
