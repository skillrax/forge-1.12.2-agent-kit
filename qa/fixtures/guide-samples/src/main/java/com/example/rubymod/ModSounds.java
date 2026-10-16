package com.example.rubymod;

import net.minecraft.util.ResourceLocation;
import net.minecraft.util.SoundEvent;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

/** guides/sounds.md. sounds.json reuses a vanilla file, so the fixture needs no .ogg. */
@Mod.EventBusSubscriber(modid = RubymodMod.MODID)
public final class ModSounds {
    public static final SoundEvent ZAP = sound("zap");

    private static SoundEvent sound(String name) {
        ResourceLocation id = new ResourceLocation(RubymodMod.MODID, name);
        return new SoundEvent(id).setRegistryName(id);                     // registry name = sound name
    }

    @SubscribeEvent
    public static void register(RegistryEvent.Register<SoundEvent> event) { event.getRegistry().register(ZAP); }

    private ModSounds() {}
}
