package com.example.skillprobe;

import net.minecraft.client.renderer.block.model.ModelResourceLocation;
import net.minecraftforge.client.event.ModelRegistryEvent;
import net.minecraftforge.client.model.ModelLoader;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;

@Mod.EventBusSubscriber(modid = SkillProbeMod.MOD_ID, value = Side.CLIENT)
public final class ClientModels {
    private ClientModels() {}

    @SubscribeEvent
    public static void registerModels(ModelRegistryEvent event) {
        ModelLoader.setCustomModelResourceLocation(ModItems.PROBE_TOKEN, 0,
            new ModelResourceLocation(ModItems.PROBE_TOKEN.getRegistryName(), "inventory"));
    }
}
