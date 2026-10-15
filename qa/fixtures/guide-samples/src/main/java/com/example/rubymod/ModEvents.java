package com.example.rubymod;

import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.world.BlockEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

/** Forge event handlers: public static methods in a class annotated with @Mod.EventBusSubscriber. */
@Mod.EventBusSubscriber(modid = RubymodMod.MODID)
public final class ModEvents {
    /** Ruby Edge enchantment: +1.5 damage per level. LivingHurtEvent fires on the server. */
    @SubscribeEvent
    public static void onHurt(LivingHurtEvent event) {
        if (!(event.getSource().getTrueSource() instanceof EntityLivingBase)) return;
        EntityLivingBase attacker = (EntityLivingBase) event.getSource().getTrueSource();
        int level = EnchantmentHelper.getMaxEnchantmentLevel(ModEnchantments.RUBY_EDGE, attacker);
        if (level > 0) event.setAmount(event.getAmount() + 1.5F * level);
    }

    /** Gravel sometimes also drops a ruby (not with Silk Touch). */
    @SubscribeEvent
    public static void onDrops(BlockEvent.HarvestDropsEvent event) {
        if (event.getState().getBlock() == Blocks.GRAVEL && !event.isSilkTouching() && event.getWorld().rand.nextDouble() < ModConfig.gravelRubyChance)
            event.getDrops().add(new ItemStack(ModItems.RUBY));
    }

    /** Fires on both sides; act on the server, cancel on both to stop the normal interaction. */
    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        EntityPlayer player = event.getEntityPlayer();
        if (player.isSneaking() && event.getWorld().getBlockState(event.getPos()).getBlock() == ModBlocks.RUBY_BLOCK) {
            if (!event.getWorld().isRemote) player.sendStatusMessage(new TextComponentTranslation("message.rubymod.shiny"), true);
            event.setCanceled(true);
        }
    }

    private ModEvents() {}
}
