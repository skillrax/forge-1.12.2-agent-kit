package com.example.configmachine;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.FurnaceRecipes;

// Example configuration: ordered custom entries take priority over the live furnace registry.
public final class MachineRecipes {
    public static final class Entry {
        public final String id;
        public final ItemStack input,extra,main,secondary;
        public final boolean requiresUpgrade;
        public Entry(String id,ItemStack input,ItemStack extra,ItemStack main,ItemStack secondary,boolean requiresUpgrade){
            if(id.isEmpty() || input.isEmpty() || main.isEmpty())throw new IllegalArgumentException("Recipe needs an ID, input and primary output");
            this.id=id;this.input=input.copy();this.extra=extra.copy();this.main=main.copy();this.secondary=secondary.copy();this.requiresUpgrade=requiresUpgrade;
        }
    }
    public static final List<Entry> CUSTOM=new ArrayList<>();
    static {
        CUSTOM.add(new Entry("iron",new ItemStack(Blocks.IRON_ORE),new ItemStack(Blocks.SAND),new ItemStack(Items.IRON_INGOT,2),new ItemStack(Items.BRICK),true));
        CUSTOM.add(new Entry("gold",new ItemStack(Blocks.GOLD_ORE),new ItemStack(Blocks.SAND),new ItemStack(Items.GOLD_INGOT,2),new ItemStack(Items.BRICK),true));
    }
    public final ItemStack main,secondary;
    public final int mainCount,secondaryCount,ticks=MachineSettings.TICKS,rfPerTick=MachineSettings.RF_PER_TICK;
    public final String key;
    private MachineRecipes(String id,ItemStack input,ItemStack extra,ItemStack main,ItemStack secondary,int mainCount,int secondaryCount){
        this.main=main.copy();this.secondary=secondary.copy();this.mainCount=mainCount;this.secondaryCount=secondaryCount;
        key=id+":"+input.getItem().getRegistryName()+":"+input.getMetadata()+":"+input.getTagCompound()+":"+(secondaryCount==0?"":extra.serializeNBT())+":"+main.serializeNBT()+":"+secondary.serializeNBT()+":"+mainCount+":"+secondaryCount;
    }
    private static boolean matches(ItemStack actual,ItemStack expected){return !actual.isEmpty() && ItemStack.areItemsEqual(actual,expected);}
    public static boolean mainInput(ItemStack stack){
        if(stack.isEmpty())return false;
        if(!FurnaceRecipes.instance().getSmeltingResult(stack).isEmpty())return true;
        for(Entry e:CUSTOM)if(matches(stack,e.input))return true;return false;
    }
    public static boolean secondaryInput(ItemStack stack){for(Entry e:CUSTOM)if(!e.extra.isEmpty() && matches(stack,e.extra))return true;return false;}
    public static MachineRecipes select(ItemStack input,ItemStack extra,boolean upgraded){
        if(input.isEmpty())return null;
        for(Entry e:CUSTOM){
            if(e.requiresUpgrade && !upgraded || !matches(input,e.input) || input.getCount()<e.input.getCount())continue;
            if(!e.extra.isEmpty() && (!matches(extra,e.extra) || extra.getCount()<e.extra.getCount()))continue;
            return new MachineRecipes("custom:"+e.id,input,e.extra, e.main,e.secondary,e.input.getCount(),e.extra.getCount());
        }
        ItemStack output=FurnaceRecipes.instance().getSmeltingResult(input);
        return output.isEmpty()?null:new MachineRecipes("furnace",input,ItemStack.EMPTY,output,ItemStack.EMPTY,1,0);
    }
}
