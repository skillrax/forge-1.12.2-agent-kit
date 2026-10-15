package com.example.skillprobe;

import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.item.Item;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

@Mod.EventBusSubscriber(modid = SkillProbeMod.MOD_ID)
public final class ModItems {
    public static Item PROBE_TOKEN;

    private ModItems() {}

    @SubscribeEvent
    public static void registerItems(RegistryEvent.Register<Item> event) {
        PROBE_TOKEN = new Item()
            .setRegistryName(SkillProbeMod.MOD_ID, "probe_token")
            .setTranslationKey(SkillProbeMod.MOD_ID + ".probe_token")
            .setCreativeTab(CreativeTabs.MATERIALS)
            .setMaxStackSize(16);
        event.getRegistry().register(PROBE_TOKEN);
    }
}
