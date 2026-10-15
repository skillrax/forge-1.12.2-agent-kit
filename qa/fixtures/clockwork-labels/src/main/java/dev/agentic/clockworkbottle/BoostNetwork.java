package dev.agentic.clockworkbottle;

import net.minecraftforge.fml.common.network.NetworkRegistry;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;
import net.minecraftforge.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import net.minecraftforge.fml.relauncher.Side;

public final class BoostNetwork {
    public static final SimpleNetworkWrapper CHANNEL = NetworkRegistry.INSTANCE.newSimpleChannel("clockworkboost");
    private BoostNetwork() {}
    public static void init() { CHANNEL.registerMessage(Handler.class, BoostSnapshot.class, 0, Side.CLIENT); }
    public static final class Handler implements IMessageHandler<BoostSnapshot, IMessage> {
        @Override public IMessage onMessage(BoostSnapshot message, MessageContext context) {
            ClockworkBottle.proxy.receive(message);
            return null;
        }
    }
}
