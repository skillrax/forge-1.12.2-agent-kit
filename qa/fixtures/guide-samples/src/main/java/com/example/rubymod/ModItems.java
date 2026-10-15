package com.example.rubymod;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.init.Blocks;
import net.minecraft.item.Item;
import net.minecraft.item.ItemSeeds;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraft.init.MobEffects;
import net.minecraft.potion.PotionEffect;
import net.minecraft.item.ItemFood;
import net.minecraft.item.ItemHoe;
import net.minecraft.item.ItemSpade;
import net.minecraft.item.ItemSword;
import net.minecraftforge.common.util.EnumHelper;
import net.minecraft.init.SoundEvents;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.ItemArmor;

/**
 * Every item of this mod. Declare items as static fields created with item(...); they are
 * registered automatically and ClientEvents gives each the model models/item/<name>.json.
 */
@Mod.EventBusSubscriber(modid = RubymodMod.MODID)
public final class ModItems {
    public static final List<Item> ALL = new ArrayList<>();

    public static final Item RUBY = item("ruby", new Item()).setCreativeTab(CreativeTabs.MATERIALS);
    public static final Item RUBY_APPLE = item("ruby_apple", new ItemFood(6, 1.2F, false)).setAlwaysEdible().setPotionEffect(new PotionEffect(MobEffects.REGENERATION, 100, 1), 1.0F);
    /** Tool material: harvest level 2, 1200 uses, speed 8, damage bonus 3, enchantability 14. */
    public static final Item.ToolMaterial RUBY_TOOLS = EnumHelper.addToolMaterial("ruby", 2, 1200, 8.0F, 3.0F, 14);
    public static final Item RUBY_SWORD = item("ruby_sword", new ItemSword(RUBY_TOOLS));
    public static final Item RUBY_PICKAXE = item("ruby_pickaxe", new ToolItems.Pickaxe(RUBY_TOOLS));
    public static final Item RUBY_AXE = item("ruby_axe", new ToolItems.Axe(RUBY_TOOLS, 8.0F, -3.0F));
    public static final Item RUBY_SHOVEL = item("ruby_shovel", new ItemSpade(RUBY_TOOLS));
    public static final Item RUBY_HOE = item("ruby_hoe", new ItemHoe(RUBY_TOOLS));
    /** Armor material: durability factor 30, protection helmet/chest/legs/boots 3,8,6,3, toughness 2. Texture: textures/models/armor/ruby_layer_1/2.png */
    public static final ItemArmor.ArmorMaterial RUBY_ARMOR = EnumHelper.addArmorMaterial("ruby", RubymodMod.MODID + ":ruby", 30, new int[] {3, 6, 8, 3}, 14, SoundEvents.ITEM_ARMOR_EQUIP_IRON, 2.0F);
    public static final Item RUBY_HELMET = item("ruby_helmet", new ItemArmor(RUBY_ARMOR, 0, EntityEquipmentSlot.HEAD));
    public static final Item RUBY_CHESTPLATE = item("ruby_chestplate", new ItemArmor(RUBY_ARMOR, 0, EntityEquipmentSlot.CHEST));
    public static final Item RUBY_LEGGINGS = item("ruby_leggings", new ItemArmor(RUBY_ARMOR, 0, EntityEquipmentSlot.LEGS));
    public static final Item RUBY_BOOTS = item("ruby_boots", new ItemArmor(RUBY_ARMOR, 0, EntityEquipmentSlot.FEET));
    public static final Item WAND = item("wand", new WandItem());
    public static final Item RUBY_SEEDS = item("ruby_seeds", new ItemSeeds(ModBlocks.RUBY_CROP, Blocks.FARMLAND)).setCreativeTab(RubymodMod.TAB);
    // kit:items

    /** Sets registry name modid:name and translation key item.modid.name (lang: item.modid.name.name=...). */
    static <T extends Item> T item(String name, T item) {
        item.setRegistryName(RubymodMod.MODID, name);
        item.setTranslationKey(RubymodMod.MODID + "." + name);
        ALL.add(item);
        return item;
    }

    @SubscribeEvent
    public static void register(RegistryEvent.Register<Item> event) {
        for (Item item : ALL) event.getRegistry().register(item);
        // kit:after-items
    }

    private ModItems() {}
}