package com.example.machinestarter;

import net.minecraft.item.ItemStack;

// Inventory/menu scaffold only. Add ticking, recipes and energy for the requested feature.
public final class MachineTile extends InventoryTile {
    public MachineTile() {super(3);}
    @Override public String getName() {return "tile.machinestarter.machine.name";}
    @Override public boolean isItemValidForSlot(int slot,ItemStack stack) {return slot==0;}
}
