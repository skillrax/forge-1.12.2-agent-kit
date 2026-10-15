package dev.agentic.smelter;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.SidedProxy;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.network.NetworkRegistry;

@Mod(modid=SmelterProof.ID,name="Configurable Smelter Proof",version="1.0.1",acceptedMinecraftVersions="[1.12.2]")
public final class SmelterProof {
    public static final String ID="smelterproof";
    @Mod.Instance(ID) public static SmelterProof instance;
    @SidedProxy(clientSide="dev.agentic.smelter.ClientProxy",serverSide="dev.agentic.smelter.CommonProxy")
    public static CommonProxy proxy;
    @Mod.EventHandler public void preInit(FMLPreInitializationEvent event){NetworkRegistry.INSTANCE.registerGuiHandler(this,new MenuHandler());}
}
