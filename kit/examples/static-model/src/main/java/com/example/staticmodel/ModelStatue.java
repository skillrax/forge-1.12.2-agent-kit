package com.example.staticmodel;

import net.minecraft.block.Block;
import net.minecraft.item.Item;
import net.minecraft.item.ItemBlock;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.registry.GameRegistry;

@Mod(modid=ModelStatue.ID,name="Static Model Starter",version="1.0.0",acceptedMinecraftVersions="[1.12.2]")
@Mod.EventBusSubscriber(modid=ModelStatue.ID)
public final class ModelStatue {
    public static final String ID="staticmodel";
    public static final Block STATUE=new StatueBlock();
    @SubscribeEvent public static void blocks(RegistryEvent.Register<Block> e) {
        e.getRegistry().register(STATUE);GameRegistry.registerTileEntity(StatueTile.class,new ResourceLocation(ID,"statue"));
    }
    @SubscribeEvent public static void items(RegistryEvent.Register<Item> e) {
        e.getRegistry().register(new ItemBlock(STATUE).setRegistryName(STATUE.getRegistryName()));
    }
}
