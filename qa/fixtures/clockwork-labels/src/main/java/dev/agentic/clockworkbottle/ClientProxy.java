package dev.agentic.clockworkbottle;
import net.minecraft.client.Minecraft;
public final class ClientProxy extends CommonProxy {
    @Override public void receive(BoostSnapshot snapshot) {
        Minecraft minecraft = Minecraft.getMinecraft();
        minecraft.addScheduledTask(() -> BoostLabels.accept(snapshot));
    }
}
