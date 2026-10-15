package dev.agentic.playermenu;

import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.SidedProxy;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.network.NetworkRegistry;
import net.minecraftforge.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import net.minecraftforge.fml.relauncher.Side;

@Mod(modid=PlayerMenu.ID,name="Personal Menu Proof",version="1.0.1",acceptedMinecraftVersions="[1.12.2]")
public final class PlayerMenu {
    public static final String ID="personalmenu";
    public static final SimpleNetworkWrapper NETWORK=NetworkRegistry.INSTANCE.newSimpleChannel(ID);
    @SidedProxy(clientSide="dev.agentic.playermenu.ClientProxy",serverSide="dev.agentic.playermenu.PlayerMenu$CommonProxy")
    public static CommonProxy proxy;
    public static class CommonProxy {
        public void init() {}
        public void receive(Packets.Snapshot snapshot) {}
    }
    @Mod.EventHandler public void preInit(FMLPreInitializationEvent event) {
        NETWORK.registerMessage(Packets.RequestHandler.class,Packets.Request.class,0,Side.SERVER);
        NETWORK.registerMessage(Packets.SnapshotHandler.class,Packets.Snapshot.class,1,Side.CLIENT);
        proxy.init();
    }
}
