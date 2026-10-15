package com.example.rubymod;

import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnumEnchantmentType;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.potion.Potion;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/** Enchantments and potion effects are registry entries like items; what they do is code (see ModEvents). */
@Mod.EventBusSubscriber(modid = RubymodMod.MODID)
public final class ModEnchantments {
    public static final Enchantment RUBY_EDGE = new RubyEdge();
    public static final Potion SHINE = new Shine();

    @SubscribeEvent
    public static void enchantments(RegistryEvent.Register<Enchantment> event) { event.getRegistry().register(RUBY_EDGE); }

    @SubscribeEvent
    public static void potions(RegistryEvent.Register<Potion> event) { event.getRegistry().register(SHINE); }

    /** Lang: enchantment.rubymod.ruby_edge=Ruby Edge */
    static class RubyEdge extends Enchantment {
        RubyEdge() {
            super(Rarity.UNCOMMON, EnumEnchantmentType.WEAPON, new EntityEquipmentSlot[] {EntityEquipmentSlot.MAINHAND});
            setName(RubymodMod.MODID + ".ruby_edge");
            setRegistryName(RubymodMod.MODID, "ruby_edge");
        }
        @Override public int getMaxLevel() { return 3; }
        @Override public int getMinEnchantability(int level) { return 5 + (level - 1) * 10; }
        @Override public int getMaxEnchantability(int level) { return getMinEnchantability(level) + 20; }
    }

    /** Lang: effect.rubymod.shine=Shine. Heals 1 HP every 40 ticks (faster with higher amplifier). */
    static class Shine extends Potion {
        Shine() {
            super(false, 0xC01F3F);                       // isBadEffect, particle/liquid color
            setPotionName("effect." + RubymodMod.MODID + ".shine");
            setRegistryName(RubymodMod.MODID, "shine");
        }
        @Override public boolean isReady(int duration, int amplifier) { return duration % (40 >> Math.min(amplifier, 3)) == 0; }
        @Override public void performEffect(EntityLivingBase entity, int amplifier) {
            if (!entity.world.isRemote) entity.heal(1.0F);
        }
        @SideOnly(Side.CLIENT) @Override public boolean hasStatusIcon() { return false; }   // client-only method
    }

    private ModEnchantments() {}
}
