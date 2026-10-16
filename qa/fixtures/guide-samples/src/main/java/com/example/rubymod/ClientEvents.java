package com.example.rubymod;

import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.KeyBinding;
import net.minecraftforge.client.settings.KeyConflictContext;
import net.minecraftforge.fml.client.registry.ClientRegistry;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.lwjgl.input.Keyboard;
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

    public static final KeyBinding OPEN = new KeyBinding("key.rubymod.open", KeyConflictContext.IN_GAME, Keyboard.KEY_N, "key.categories.rubymod");

    @SubscribeEvent
    public static void models(ModelRegistryEvent event) {
        for (Item item : ForgeRegistries.ITEMS) {
            if (!RubymodMod.MODID.equals(item.getRegistryName().getNamespace()) || item instanceof CustomModel) continue;
            ModelLoader.setCustomModelResourceLocation(item, 0, new ModelResourceLocation(item.getRegistryName(), "inventory"));
        }
        RenderingRegistry.registerEntityRenderingHandler(RubyZombie.class, RenderZombie::new);
        ClientRegistry.registerKeyBinding(OPEN);
        // kit:models
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        boolean pressed = false;
        while (OPEN.isPressed()) pressed = true;                       // drain presses
        Minecraft mc = Minecraft.getMinecraft();
        if (pressed && mc.player != null && mc.currentScreen == null) mc.displayGuiScreen(new RubyInfoScreen());
    }

    /** Called from ModNetwork.CountReply.Handler (network thread): schedule onto the client thread. */
    public static void onCountReply(int count) {
        Minecraft mc = Minecraft.getMinecraft();
        mc.addScheduledTask(() -> mc.player.sendStatusMessage(new TextComponentString("count: " + count), true));
    }

    private ClientEvents() {}
}