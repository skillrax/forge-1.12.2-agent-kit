package com.example.rubymod;

import net.minecraft.creativetab.CreativeTabs;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLServerStartingEvent;
import net.minecraftforge.fml.common.network.NetworkRegistry;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import net.minecraftforge.fml.common.registry.GameRegistry;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

@Mod(modid = RubymodMod.MODID, name = RubymodMod.NAME, version = RubymodMod.VERSION, acceptedMinecraftVersions = "[1.12.2]")
public class RubymodMod {
    public static final String MODID = "rubymod";
    public static final String NAME = "Ruby Mod";
    /** Keep equal to build.gradle and mcmod.info; use agent.ps1 version <x.y.z> to change all three. */
    public static final String VERSION = "1.1.0";
    public static final Logger LOGGER = LogManager.getLogger(MODID);

    /** Own creative tab. Lang: itemGroup.rubymod=Ruby Mod */
    public static final CreativeTabs TAB = new CreativeTabs(MODID) {
        @SideOnly(Side.CLIENT)
        @Override
        public ItemStack createIcon() { return new ItemStack(ModItems.RUBY); }
    };

    @Mod.Instance(MODID)
    public static RubymodMod INSTANCE;

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        GameRegistry.registerWorldGenerator(new ModWorldGen(), 0);
        ModNetwork.init();
        NetworkRegistry.INSTANCE.registerGuiHandler(INSTANCE, new ModGuiHandler());
        // kit:preinit
    }

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        ModItems.RUBY_TOOLS.setRepairItem(new ItemStack(ModItems.RUBY));
        ModItems.RUBY_ARMOR.setRepairItem(new ItemStack(ModItems.RUBY));
        GameRegistry.addSmelting(new ItemStack(Item.getItemFromBlock(ModBlocks.RUBY_ORE)), new ItemStack(ModItems.RUBY, 1), 1.0F); // smelting: ruby
        // kit:init
    }

    @Mod.EventHandler
    public void serverStarting(FMLServerStartingEvent event) {
        event.registerServerCommand(new RubyCommand());
    }
}