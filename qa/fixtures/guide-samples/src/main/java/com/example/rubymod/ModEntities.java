package com.example.rubymod;

import net.minecraft.entity.EnumCreatureType;
import net.minecraft.init.Biomes;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.registry.EntityEntry;
import net.minecraftforge.fml.common.registry.EntityEntryBuilder;

@Mod.EventBusSubscriber(modid = RubymodMod.MODID)
public final class ModEntities {
    private static int nextId = 0;   // network id, unique within this mod

    @SubscribeEvent
    public static void register(RegistryEvent.Register<EntityEntry> event) {
        event.getRegistry().register(EntityEntryBuilder.create()
                .entity(RubyZombie.class)
                .id(new ResourceLocation(RubymodMod.MODID, "ruby_zombie"), nextId++)
                .name(RubymodMod.MODID + ".ruby_zombie")          // lang: entity.rubymod.ruby_zombie.name
                .tracker(64, 3, true)                               // tracking range, update interval, send velocity
                .egg(0x3a5f2e, 0xc01f3f)                            // spawn egg colors
                .spawn(EnumCreatureType.MONSTER, ModConfig.zombieWeight, 1, 3, Biomes.PLAINS, Biomes.DESERT)
                .build());
    }

    private ModEntities() {}
}
