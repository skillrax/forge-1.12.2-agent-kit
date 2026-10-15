package dev.agentic.clockworkbottle;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLServerStoppedEvent;
@Mod(modid=ClockworkBottle.MOD_ID, name="Clockwork Bottle", version="1.1.0", acceptedMinecraftVersions="[1.12.2]")
public final class ClockworkBottle {
    public static final String MOD_ID="clockworkbottle";
    @Mod.EventHandler public void stopped(FMLServerStoppedEvent event) {FurnaceBoosts.clear();}
}
