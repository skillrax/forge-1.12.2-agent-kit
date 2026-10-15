package com.example.rubymod;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.model.ModelResourceLocation;
import net.minecraft.client.renderer.entity.RenderZombie;
import net.minecraft.util.text.TextComponentString;
import net.minecraftforge.fml.client.registry.RenderingRegistry;
import net.minecraft.item.Item;
import net.minecraftforge.client.event.ModelRegistryEvent;
import net.minecraftforge.client.model.ModelLoader;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.registry.ForgeRegistries;
import net.minecraftforge.fml.relauncher.Side;

/** Client-only (never loaded on a dedicated server). Keep all rendering/model/GUI code in client classes like this. */
@Mod.EventBusSubscriber(modid = RubymodMod.MODID, value = Side.CLIENT)
public final class ClientEvents {
    /** Items implementing this register their own models in models(). */
    public interface CustomModel {}

    @SubscribeEvent
    public static void models(ModelRegistryEvent event) {
        for (Item item : ForgeRegistries.ITEMS) {
            if (!RubymodMod.MODID.equals(item.getRegistryName().getNamespace()) || item instanceof CustomModel) continue;
            ModelLoader.setCustomModelResourceLocation(item, 0, new ModelResourceLocation(item.getRegistryName(), "inventory"));
        }
        RenderingRegistry.registerEntityRenderingHandler(RubyZombie.class, RenderZombie::new);
        // kit:models
    }

    /** Called from ModNetwork.CountReply.Handler (network thread): schedule onto the client thread. */
    public static void onCountReply(int count) {
        Minecraft mc = Minecraft.getMinecraft();
        mc.addScheduledTask(() -> mc.player.sendStatusMessage(new TextComponentString("count: " + count), true));
    }

    private ClientEvents() {}
}