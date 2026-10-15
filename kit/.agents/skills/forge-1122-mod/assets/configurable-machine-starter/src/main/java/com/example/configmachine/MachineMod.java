package com.example.configmachine;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.SidedProxy;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.network.NetworkRegistry;

@Mod(modid=MachineMod.ID,name="Configurable Machine Starter",version="1.0.0",acceptedMinecraftVersions="[1.12.2]")
public final class MachineMod {
    public static final String ID="configmachine";
    @Mod.Instance(ID) public static MachineMod instance;
    @SidedProxy(clientSide="com.example.configmachine.ClientProxy",serverSide="com.example.configmachine.CommonProxy")
    public static CommonProxy proxy;
    @Mod.EventHandler public void preInit(FMLPreInitializationEvent event){NetworkRegistry.INSTANCE.registerGuiHandler(this,new MenuHandler());}
}
