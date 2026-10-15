package dev.agentic.workshop;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.text.TextComponentString;

/** One tiny quest, stored in Forge's vanilla-player persisted data section. */
public final class PlayerQuest {
    private static final String KEY="KitWorkshopQuest";
    private static final int GOAL=8;
    private PlayerQuest() {}
    public static void reset(EntityPlayer player) {
        if(!player.world.isRemote)player.getEntityData().getCompoundTag(EntityPlayer.PERSISTED_NBT_TAG).removeTag(KEY);
    }
    public static int progress(EntityPlayer player) {
        NBTTagCompound persisted=player.getEntityData().getCompoundTag(EntityPlayer.PERSISTED_NBT_TAG);
        return persisted.hasKey(KEY,3)?Math.max(0,Math.min(GOAL,persisted.getInteger(KEY))):-1;
    }
    private static void save(EntityPlayer player,int progress) {
        NBTTagCompound data=player.getEntityData();
        NBTTagCompound persisted=data.getCompoundTag(EntityPlayer.PERSISTED_NBT_TAG);
        persisted.setInteger(KEY,progress);data.setTag(EntityPlayer.PERSISTED_NBT_TAG,persisted);
    }
    private static void tell(EntityPlayer player,String text) {player.sendMessage(new TextComponentString("Test Quest: "+text));}
    public static void interact(EntityPlayer player) {
        if(player.world.isRemote)return;
        if(player.openContainer!=player.inventoryContainer) {tell(player,"Close your menu first.");return;}
        int previous=progress(player);
        if(previous<0) {save(player,0);tell(player,"Accepted. Deliver8 cobblestone for1 diamond. Sneak-right-click again to submit.");return;}
        if(previous==GOAL) {tell(player,"Completed. Reward already claimed.");return;}
        Item cobble=Item.getItemFromBlock(Blocks.COBBLESTONE);
        int available=0;
        for(ItemStack stack:player.inventory.mainInventory)if(stack.getItem()==cobble)available+=stack.getCount();
        int submitted=Math.min(GOAL-previous,available);
        if(submitted==0) {tell(player,"Progress "+previous+"/8. Bring cobblestone in your inventory.");return;}
        boolean complete=previous+submitted==GOAL;
        if(complete) {
            // Check capacity before any quest/payment mutation, including Creative players.
            boolean room=false;
            ItemStack reward=new ItemStack(Items.DIAMOND);
            for(ItemStack stack:player.inventory.mainInventory)if(stack.isEmpty()
                || (ItemStack.areItemsEqual(stack,reward) && ItemStack.areItemStackTagsEqual(stack,reward)
                    && stack.getCount()<Math.min(stack.getMaxStackSize(),player.inventory.getInventoryStackLimit())))room=true;
            if(!room) {tell(player,"Make room for1 diamond before the final delivery.");return;}
            if(!player.inventory.addItemStackToInventory(reward)) {tell(player,"Reward could not fit; delivery unchanged.");return;}
        }
        int remaining=submitted;
        for(int slot=0;slot<player.inventory.mainInventory.size() && remaining>0;slot++) {
            ItemStack stack=player.inventory.mainInventory.get(slot);
            if(stack.getItem()!=cobble)continue;
            int taken=Math.min(remaining,stack.getCount());stack.shrink(taken);remaining-=taken;
            if(stack.isEmpty())player.inventory.mainInventory.set(slot,ItemStack.EMPTY);
        }
        save(player,previous+submitted);
        player.inventory.markDirty();player.inventoryContainer.detectAndSendChanges();
        tell(player,complete?"Completed8/8. Received1 diamond. This quest is once per player.":"Progress "+(previous+submitted)+"/8.");
    }
}
