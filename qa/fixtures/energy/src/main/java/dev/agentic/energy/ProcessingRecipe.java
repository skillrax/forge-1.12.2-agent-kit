package dev.agentic.energy;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;

public final class ProcessingRecipe {
    public final ItemStack input,primary,secondary;
    public final int ticks,rfPerTick;
    public final float secondaryChance;
    public ProcessingRecipe(ItemStack input,ItemStack primary,ItemStack secondary,int ticks,int rfPerTick,float chance) {
        if(input.isEmpty() || primary.isEmpty() || secondary.isEmpty() || ticks<1 || rfPerTick<1 || chance<0 || chance>1)
            throw new IllegalArgumentException("Invalid processing recipe");
        this.input=input.copy();this.primary=primary.copy();this.secondary=secondary.copy();
        this.ticks=ticks;this.rfPerTick=rfPerTick;secondaryChance=chance;
    }
    public boolean matches(ItemStack stack) {
        return !stack.isEmpty() && ItemStack.areItemsEqual(stack,input) && ItemStack.areItemStackTagsEqual(stack,input) && stack.getCount()>=input.getCount();
    }
    public boolean producesSecondary(float roll) {return roll<secondaryChance;}
    public static List<ProcessingRecipe> recipes() {
        return Collections.unmodifiableList(Arrays.asList(new ProcessingRecipe(new ItemStack(Blocks.COBBLESTONE),
            new ItemStack(Blocks.GRAVEL),new ItemStack(Items.FLINT),20,4,0.20F),
            new ProcessingRecipe(new ItemStack(Blocks.STONE),new ItemStack(Blocks.COBBLESTONE),
                new ItemStack(Blocks.COBBLESTONE),20,4,0.50F)));
    }
}
