package dev.agentic.crafting;

import net.minecraft.item.Item;
import net.minecraft.client.renderer.block.model.ModelResourceLocation;
import net.minecraftforge.client.event.ModelRegistryEvent;
import net.minecraftforge.client.model.ModelLoader;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;

@Mod.EventBusSubscriber(modid=CraftingProof.ID,value=Side.CLIENT)
public final class ClientModels {
    @SubscribeEvent public static void models(ModelRegistryEvent event) {
        for(ProofBlock block:new ProofBlock[]{Content.GENERATOR,Content.PIPE,Content.PROCESSOR})
            ModelLoader.setCustomModelResourceLocation(Item.getItemFromBlock(block),0,new ModelResourceLocation(block.getRegistryName(),"inventory"));
    }
}
