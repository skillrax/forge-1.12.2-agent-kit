package dev.agentic.routing;

import net.minecraft.block.Block;
import net.minecraft.client.renderer.block.model.ModelResourceLocation;
import net.minecraft.item.Item;
import net.minecraftforge.client.event.ModelRegistryEvent;
import net.minecraftforge.client.model.ModelLoader;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;

@Mod.EventBusSubscriber(modid=RoutingProof.ID,value=Side.CLIENT)
public final class ClientHooks {
    @SubscribeEvent public static void models(ModelRegistryEvent e) {
        for(Block b:new Block[]{RoutingProof.EXTRACTOR,RoutingProof.SPLITTER})ModelLoader.setCustomModelResourceLocation(Item.getItemFromBlock(b),0,new ModelResourceLocation(b.getRegistryName(),"inventory"));
    }
}
