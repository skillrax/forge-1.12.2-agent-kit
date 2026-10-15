package com.example.rubymod;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraftforge.items.SlotItemHandler;

/** Server+client slot layout for the 3x3 box (same layout as the vanilla dispenser texture). */
public class RubyBoxContainer extends Container {
    private final RubyBoxTile tile;

    public RubyBoxContainer(InventoryPlayer playerInventory, RubyBoxTile tile) {
        this.tile = tile;
        for (int row = 0; row < 3; row++)
            for (int col = 0; col < 3; col++)
                addSlotToContainer(new SlotItemHandler(tile.inventory, col + row * 3, 62 + col * 18, 17 + row * 18));
        for (int row = 0; row < 3; row++)
            for (int col = 0; col < 9; col++)
                addSlotToContainer(new Slot(playerInventory, col + row * 9 + 9, 8 + col * 18, 84 + row * 18));
        for (int col = 0; col < 9; col++)
            addSlotToContainer(new Slot(playerInventory, col, 8 + col * 18, 142));
    }

    @Override
    public boolean canInteractWith(EntityPlayer player) {
        return !tile.isInvalid() && player.getDistanceSq(tile.getPos()) <= 64;
    }

    /** Shift-click: box slots 0-8 <-> player slots 9-44. */
    @Override
    public ItemStack transferStackInSlot(EntityPlayer player, int index) {
        Slot slot = inventorySlots.get(index);
        if (slot == null || !slot.getHasStack()) return ItemStack.EMPTY;
        ItemStack stack = slot.getStack();
        ItemStack original = stack.copy();
        if (index < 9) { if (!mergeItemStack(stack, 9, 45, true)) return ItemStack.EMPTY; }
        else if (!mergeItemStack(stack, 0, 9, false)) return ItemStack.EMPTY;
        if (stack.isEmpty()) slot.putStack(ItemStack.EMPTY); else slot.onSlotChanged();
        return original;
    }
}
