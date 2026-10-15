package dev.agentic.energy;

import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.SidedProxy;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.network.NetworkRegistry;

@Mod(modid=EnergyProof.ID,name="Energy Capability Proof",version="1.1.0",acceptedMinecraftVersions="[1.12.2]")
public final class EnergyProof {
    public static final String ID="energyproof";
    @Mod.Instance(ID) public static EnergyProof instance;
    @SidedProxy(clientSide="dev.agentic.energy.ClientProxy",serverSide="dev.agentic.energy.CommonProxy")
    public static CommonProxy proxy;
    @Mod.EventHandler public void preInit(FMLPreInitializationEvent event) {
        NetworkRegistry.INSTANCE.registerGuiHandler(this,new MenuHandler());
    }
}
