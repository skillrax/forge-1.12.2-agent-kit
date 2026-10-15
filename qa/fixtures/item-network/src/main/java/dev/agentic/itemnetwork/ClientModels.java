package dev.agentic.itemnetwork;

import net.minecraft.item.Item;
import net.minecraft.client.renderer.block.model.ModelResourceLocation;
import net.minecraftforge.client.event.ModelRegistryEvent;
import net.minecraftforge.client.model.ModelLoader;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;

@Mod.EventBusSubscriber(modid=ItemNetwork.ID,value=Side.CLIENT)
public final class ClientModels {
    @SubscribeEvent public static void models(ModelRegistryEvent event) {
        for(DuctBlock block:new DuctBlock[]{Content.DUCT})
            ModelLoader.setCustomModelResourceLocation(Item.getItemFromBlock(block),0,new ModelResourceLocation(block.getRegistryName(),"inventory"));
    }
}
