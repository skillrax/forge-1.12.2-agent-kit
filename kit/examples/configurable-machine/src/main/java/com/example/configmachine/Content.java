package com.example.configmachine;
import net.minecraft.block.Block;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.item.Item;
import net.minecraft.item.ItemBlock;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.registry.GameRegistry;

@Mod.EventBusSubscriber(modid=MachineMod.ID)
public final class Content {
    public static final MachineBlock MACHINE=new MachineBlock();
    public static final Item UPGRADE=new Item().setRegistryName(MachineMod.ID,"recipe_upgrade").setTranslationKey(MachineMod.ID+".recipe_upgrade").setMaxStackSize(1).setCreativeTab(CreativeTabs.REDSTONE);
    @SubscribeEvent public static void blocks(RegistryEvent.Register<Block> e){
        e.getRegistry().register(MACHINE);
        GameRegistry.registerTileEntity(MachineTile.class,new ResourceLocation(MachineMod.ID,"machine"));
    }
    @SubscribeEvent public static void items(RegistryEvent.Register<Item> e){
        e.getRegistry().register(new ItemBlock(MACHINE).setRegistryName(MACHINE.getRegistryName()));
        e.getRegistry().register(UPGRADE);
    }
}
