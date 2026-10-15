package dev.agentic.workshop;

import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.SidedProxy;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.network.NetworkRegistry;

@Mod(modid=Workshop.ID, name="Kit Capability Workshop", version="0.7.0", acceptedMinecraftVersions="[1.12.2]")
public final class Workshop {
    public static final String ID="kitworkshop";
    @Mod.Instance(ID) public static Workshop instance;
    @SidedProxy(clientSide="dev.agentic.workshop.ClientProxy",serverSide="dev.agentic.workshop.CommonProxy")
    public static CommonProxy proxy;
    @Mod.EventHandler public void preInit(FMLPreInitializationEvent event) {
        net.minecraftforge.fml.common.registry.EntityRegistry.registerModEntity(
            new net.minecraft.util.ResourceLocation(ID,"test_trader"),TraderNpc.class,"test_trader",1,this,64,3,true,0xB0B0B0,0x306030);
        net.minecraftforge.fml.common.registry.EntityRegistry.registerModEntity(
            new net.minecraft.util.ResourceLocation(ID,"test_projectile"),TestProjectile.class,"test_projectile",2,this,64,1,true);
        TrialDimension.register(new java.io.File(event.getModConfigurationDirectory(),"kitworkshop.cfg"));
        proxy.init();
        NetworkRegistry.INSTANCE.registerGuiHandler(this,new MenuHandler());
        net.minecraftforge.fml.common.registry.GameRegistry.registerWorldGenerator(new TrialWorldGen(),0);
        net.minecraft.world.storage.loot.LootTableList.register(TrialWorldGen.LOOT);
    }
    @Mod.EventHandler public void serverStarting(net.minecraftforge.fml.common.event.FMLServerStartingEvent event) {
        event.registerServerCommand(new ResetQuestCommand());
        event.registerServerCommand(new WorldSiteCommand());
    }
}
