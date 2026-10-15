package dev.agentic.clockworkbottle;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.SidedProxy;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.event.FMLServerStoppedEvent;
@Mod(modid=ClockworkBottle.MOD_ID, name="Clockwork Bottle", version="1.2.1", acceptedMinecraftVersions="[1.12.2]")
public final class ClockworkBottle {
    public static final String MOD_ID="clockworkbottle";
    @SidedProxy(clientSide="dev.agentic.clockworkbottle.ClientProxy", serverSide="dev.agentic.clockworkbottle.CommonProxy")
    public static CommonProxy proxy;
    @Mod.EventHandler public void preInit(FMLPreInitializationEvent event) {BoostNetwork.init();}
    @Mod.EventHandler public void stopped(FMLServerStoppedEvent event) {FurnaceBoosts.clear();}
}
