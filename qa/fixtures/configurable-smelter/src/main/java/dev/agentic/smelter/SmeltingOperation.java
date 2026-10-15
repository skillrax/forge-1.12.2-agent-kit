package dev.agentic.smelter;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.FurnaceRecipes;

public final class SmeltingOperation {
    public final ItemStack main,secondary;
    public final int ticks=100,rfPerTick=20;
    public final boolean usesSand;
    public final String key;
    private SmeltingOperation(ItemStack input,ItemStack main,ItemStack secondary,boolean sand){
        this.main=main.copy();this.secondary=secondary.copy();usesSand=sand;
        key=(sand?"secondary:":"furnace:")+input.getItem().getRegistryName()+":"+input.getMetadata()+":"+input.getTagCompound()+":"+main.serializeNBT()+":"+secondary.serializeNBT();
    }
    public static boolean ore(ItemStack input){return !input.isEmpty() && (input.getItem()==Item.getItemFromBlock(Blocks.IRON_ORE) || input.getItem()==Item.getItemFromBlock(Blocks.GOLD_ORE)) && input.getMetadata()==0;}
    public static boolean sand(ItemStack input){return !input.isEmpty() && input.getItem()==Item.getItemFromBlock(Blocks.SAND) && input.getMetadata()==0;}
    public static boolean furnaceInput(ItemStack input){return !input.isEmpty() && !FurnaceRecipes.instance().getSmeltingResult(input).isEmpty();}
    public static SmeltingOperation select(ItemStack input,ItemStack flux,boolean upgraded){
        if(input.isEmpty())return null;
        if(upgraded && ore(input) && sand(flux)){
            Item ingot=input.getItem()==Item.getItemFromBlock(Blocks.IRON_ORE)?Items.IRON_INGOT:Items.GOLD_INGOT;
            return new SmeltingOperation(input,new ItemStack(ingot,2),new ItemStack(Items.BRICK),true);
        }
        ItemStack output=FurnaceRecipes.instance().getSmeltingResult(input);
        return output.isEmpty()?null:new SmeltingOperation(input,output,ItemStack.EMPTY,false);
    }
}
