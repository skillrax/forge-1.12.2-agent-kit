package com.example.rubymod;

import net.minecraftforge.common.config.Config;
import net.minecraftforge.common.config.ConfigManager;
import net.minecraftforge.fml.client.event.ConfigChangedEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

/** Saved as config/rubymod.cfg and editable in-game (Mods > Config). Read the static fields anywhere. */
@Config(modid = RubymodMod.MODID)
public final class ModConfig {
    @Config.Comment("Chance (0-1) that gravel also drops a ruby")
    @Config.RangeDouble(min = 0, max = 1)
    public static double gravelRubyChance = 0.1;

    @Config.Comment("Ruby zombie spawn weight")
    @Config.RangeInt(min = 0, max = 100)
    @Config.RequiresMcRestart
    public static int zombieWeight = 20;

    @Mod.EventBusSubscriber(modid = RubymodMod.MODID)
    public static final class Sync {
        @SubscribeEvent
        public static void onChanged(ConfigChangedEvent.OnConfigChangedEvent event) {
            if (event.getModID().equals(RubymodMod.MODID)) ConfigManager.sync(RubymodMod.MODID, Config.Type.INSTANCE);
        }
    }

    private ModConfig() {}
}
