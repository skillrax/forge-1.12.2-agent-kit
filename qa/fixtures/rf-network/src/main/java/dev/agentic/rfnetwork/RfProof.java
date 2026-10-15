package dev.agentic.rfnetwork;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.SidedProxy;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.network.NetworkRegistry;
@Mod(modid=RfProof.ID,name="RF Network Proof",version="1.0.1",acceptedMinecraftVersions="[1.12.2]")
public final class RfProof {
    public static final String ID="rfnetwork";
    @Mod.Instance(ID) public static RfProof instance;
    @SidedProxy(clientSide="dev.agentic.rfnetwork.ClientProxy",serverSide="dev.agentic.rfnetwork.CommonProxy") public static CommonProxy proxy;
    @Mod.EventHandler public void preInit(FMLPreInitializationEvent e){NetworkRegistry.INSTANCE.registerGuiHandler(this,new MenuHandler());ControlPacket.register();}
}
