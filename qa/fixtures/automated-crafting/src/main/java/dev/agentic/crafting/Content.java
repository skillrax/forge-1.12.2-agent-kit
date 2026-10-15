package dev.agentic.crafting;

import net.minecraft.block.Block;
import net.minecraft.item.Item;
import net.minecraft.item.ItemBlock;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.registry.GameRegistry;

@Mod.EventBusSubscriber(modid=CraftingProof.ID)
public final class Content {
    public static final ProofBlock GENERATOR=new ProofBlock("generator",0),PIPE=new ProofBlock("pipe",1),PROCESSOR=new ProofBlock("processor",2);
    @SubscribeEvent public static void blocks(RegistryEvent.Register<Block> event) {
        event.getRegistry().registerAll(GENERATOR,PIPE,PROCESSOR);
        GameRegistry.registerTileEntity(GeneratorTile.class,new ResourceLocation(CraftingProof.ID,"generator"));
        GameRegistry.registerTileEntity(PipeTile.class,new ResourceLocation(CraftingProof.ID,"pipe"));
        GameRegistry.registerTileEntity(ProcessorTile.class,new ResourceLocation(CraftingProof.ID,"processor"));
    }
    @SubscribeEvent public static void items(RegistryEvent.Register<Item> event) {
        for(ProofBlock block:new ProofBlock[]{GENERATOR,PIPE,PROCESSOR})event.getRegistry().register(new ItemBlock(block).setRegistryName(block.getRegistryName()));
    }
}
