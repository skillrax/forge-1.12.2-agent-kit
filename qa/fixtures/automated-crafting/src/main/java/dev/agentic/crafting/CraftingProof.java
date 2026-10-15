package dev.agentic.crafting;

import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.SidedProxy;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.network.NetworkRegistry;

@Mod(modid=CraftingProof.ID,name="Automated Crafting Proof",version="1.0.2",acceptedMinecraftVersions="[1.12.2]")
public final class CraftingProof {
    public static final String ID="craftingproof";
    @Mod.Instance(ID) public static CraftingProof instance;
    @SidedProxy(clientSide="dev.agentic.crafting.ClientProxy",serverSide="dev.agentic.crafting.CommonProxy")
    public static CommonProxy proxy;
    @Mod.EventHandler public void preInit(FMLPreInitializationEvent event) {
        NetworkRegistry.INSTANCE.registerGuiHandler(this,new MenuHandler());
    }
}
