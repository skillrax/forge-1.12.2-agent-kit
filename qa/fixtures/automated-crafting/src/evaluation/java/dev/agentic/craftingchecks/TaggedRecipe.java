package dev.agentic.craftingchecks;

import net.minecraft.init.Items;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.util.NonNullList;
import net.minecraft.world.World;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.registries.IForgeRegistryEntry;

// QA-only registered recipe: demands a token and carries REAL input tags into
// both result and remainder. Demonstrates that fuzzy filtering cannot bypass it.
@Mod.EventBusSubscriber(modid="craftingchecks")
public final class TaggedRecipe extends IForgeRegistryEntry.Impl<IRecipe> implements IRecipe {
    @SubscribeEvent public static void register(RegistryEvent.Register<IRecipe> event) {
        event.getRegistry().register(new TaggedRecipe().setRegistryName("craftingchecks","tagged"));
    }
    @Override public boolean matches(InventoryCrafting grid,World world) {
        ItemStack s=grid.getStackInSlot(0);
        if(s.getItem()!=Items.DIAMOND || !s.hasTagCompound() || !s.getTagCompound().getBoolean("Token"))return false;
        for(int i=1;i<grid.getSizeInventory();i++)if(!grid.getStackInSlot(i).isEmpty())return false;return true;
    }
    private ItemStack tagged(net.minecraft.item.Item item,InventoryCrafting grid) {
        ItemStack s=new ItemStack(item);s.setTagCompound(grid.getStackInSlot(0).getTagCompound().copy());return s;
    }
    @Override public ItemStack getCraftingResult(InventoryCrafting grid) {return tagged(Items.PAPER,grid);}
    @Override public NonNullList<ItemStack> getRemainingItems(InventoryCrafting grid) {
        NonNullList<ItemStack> out=NonNullList.withSize(grid.getSizeInventory(),ItemStack.EMPTY);out.set(0,tagged(Items.STICK,grid));return out;
    }
    @Override public boolean canFit(int width,int height) {return width*height>=1;}
    @Override public ItemStack getRecipeOutput() {return ItemStack.EMPTY;}
}
