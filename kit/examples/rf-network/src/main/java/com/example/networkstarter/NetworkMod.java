package com.example.networkstarter;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.SidedProxy;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.network.NetworkRegistry;
@Mod(modid=NetworkMod.ID,name="RF Network Starter",version="1.0.0",acceptedMinecraftVersions="[1.12.2]")
public final class NetworkMod {
    public static final String ID="networkstarter";
    @Mod.Instance(ID) public static NetworkMod instance;
    @SidedProxy(clientSide="com.example.networkstarter.ClientProxy",serverSide="com.example.networkstarter.CommonProxy") public static CommonProxy proxy;
    @Mod.EventHandler public void preInit(FMLPreInitializationEvent e){NetworkRegistry.INSTANCE.registerGuiHandler(this,new MenuHandler());ControlPacket.register();}
}
