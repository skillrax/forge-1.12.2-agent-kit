package dev.agentic.workshop;
import java.util.Random;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
public final class TrialOre extends Block {
    public TrialOre() {
        super(Material.ROCK);setRegistryName(Workshop.ID,"test_ore");setTranslationKey(Workshop.ID+".test_ore");
        setHardness(3F);setResistance(5F);setHarvestLevel("pickaxe",1);setCreativeTab(CreativeTabs.BUILDING_BLOCKS);
    }
    @Override public Item getItemDropped(IBlockState state,Random random,int fortune) {return Items.EMERALD;}
}
