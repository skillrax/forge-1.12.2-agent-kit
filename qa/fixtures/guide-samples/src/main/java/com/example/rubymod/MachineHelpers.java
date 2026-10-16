package com.example.rubymod;

import java.util.List;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.CraftingManager;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.World;
import net.minecraftforge.fluids.FluidTank;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemHandlerHelper;

/** Machine fragments from guides/automated-crafting.md, item-transport.md and fluids.md; tested in SnippetTests. */
public final class MachineHelpers {
    /** One craft from a grid of copies (one item per used cell); adds container items to returns. EMPTY if nothing matches. */
    static ItemStack craftOnce(InventoryCrafting actualGrid, World world, List<ItemStack> returns) {
        IRecipe recipe = CraftingManager.findMatchingRecipe(actualGrid, world);
        if (recipe == null || !recipe.matches(actualGrid, world)) return ItemStack.EMPTY;
        ItemStack result = recipe.getCraftingResult(actualGrid);                 // not getRecipeOutput(): results can depend on inputs
        returns.addAll(recipe.getRemainingItems(actualGrid));                  // buckets, tools, containers
        return result;
    }

    /** Moves up to rate items from buffer's slot into target without losing any; returns how many moved. */
    static int push(IItemHandler buffer, int slot, IItemHandler target, int rate) {
        ItemStack offered = buffer.extractItem(slot, rate, true);                       // simulate
        ItemStack remainder = ItemHandlerHelper.insertItem(target, offered.copy(), false);
        int accepted = offered.getCount() - remainder.getCount();
        if (accepted > 0) buffer.extractItem(slot, accepted, false);                     // commit what moved
        return accepted;
    }

    /** Reads a tank saved by a wrenched block whose item NBT was merged over an empty tank's {Empty:""}. */
    static void readTank(FluidTank tank, NBTTagCompound nbt) {
        NBTTagCompound tankData = nbt.getCompoundTag("Tank").copy();
        if (tankData.hasKey("FluidName", 8)) tankData.removeTag("Empty");
        tank.readFromNBT(tankData);
    }

    private MachineHelpers() {}
}
