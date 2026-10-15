package com.example.skillprobechecks;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Items;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLServerStartingEvent;
import net.minecraftforge.fml.common.registry.ForgeRegistries;

/** Separate fixture-only mod. Never copy this into a production mod. */
@Mod(modid = "skillprobechecks", name = "Skill Probe Checks", version = "1.0", dependencies = "required-after:skillprobe")
public final class SkillProbeChecks {
    @Mod.EventHandler
    public void check(FMLServerStartingEvent event) {
        if (!Boolean.getBoolean("skillprobe.checks")) return;
        Item item = ForgeRegistries.ITEMS.getValue(new ResourceLocation("skillprobe", "probe_token"));
        require(item != null && item != Items.AIR, "Item not registered");
        require(item.getItemStackLimit() == 16, "Wrong stack limit");
        IRecipe recipe = ForgeRegistries.RECIPES.getValue(new ResourceLocation("skillprobe", "probe_token"));
        require(recipe != null, "Recipe not registered");
        require(recipe.getRecipeOutput().getItem() == item && recipe.getRecipeOutput().getCount() == 1, "Wrong recipe output");
        InventoryCrafting grid = new InventoryCrafting(new Container() {
            @Override public boolean canInteractWith(EntityPlayer player) { return false; }
        }, 2, 2);
        grid.setInventorySlotContents(0, new ItemStack(Items.IRON_INGOT));
        grid.setInventorySlotContents(1, new ItemStack(Items.DIAMOND));
        require(recipe.matches(grid, event.getServer().getEntityWorld()), "Valid recipe inputs rejected");
        require(recipe.getCraftingResult(grid).getItem() == item && recipe.getCraftingResult(grid).getCount() == 1, "Wrong crafting result");
        grid.setInventorySlotContents(0, new ItemStack(Items.DIAMOND));
        grid.setInventorySlotContents(1, new ItemStack(Items.IRON_INGOT));
        require(recipe.matches(grid, event.getServer().getEntityWorld()), "Reversed shapeless inputs rejected");
        grid.setInventorySlotContents(1, new ItemStack(Items.GOLD_INGOT));
        require(!recipe.matches(grid, event.getServer().getEntityWorld()), "Wrong recipe inputs accepted");
        System.out.println("SKILLPROBE_ASSERTIONS_PASS");
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException("SKILLPROBE_ASSERTIONS_FAIL: " + message);
    }
}
