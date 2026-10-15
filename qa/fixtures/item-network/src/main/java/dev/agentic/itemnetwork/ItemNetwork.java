package dev.agentic.itemnetwork;

import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.SidedProxy;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.network.NetworkRegistry;

@Mod(modid=ItemNetwork.ID,name="Item Network Proof",version="1.0.0",acceptedMinecraftVersions="[1.12.2]")
public final class ItemNetwork {
    public static final String ID="itemnetwork";
    @Mod.Instance(ID) public static ItemNetwork instance;
    @SidedProxy(clientSide="dev.agentic.itemnetwork.ClientProxy",serverSide="dev.agentic.itemnetwork.CommonProxy")
    public static CommonProxy proxy;
    @Mod.EventHandler public void preInit(FMLPreInitializationEvent event) {
        NetworkRegistry.INSTANCE.registerGuiHandler(this,new MenuHandler());
    }
}
