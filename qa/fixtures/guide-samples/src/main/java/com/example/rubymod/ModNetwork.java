package com.example.rubymod;

import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.fml.common.network.NetworkRegistry;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;
import net.minecraftforge.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import net.minecraftforge.fml.relauncher.Side;

/** Packets. Call ModNetwork.init() in preInit. Client to server: CHANNEL.sendToServer(msg); server to client: CHANNEL.sendTo(msg, player). */
public final class ModNetwork {
    public static final SimpleNetworkWrapper CHANNEL = NetworkRegistry.INSTANCE.newSimpleChannel(RubymodMod.MODID);

    public static void init() {
        int id = 0;
        CHANNEL.registerMessage(CountRequest.Handler.class, CountRequest.class, id++, Side.SERVER);
        CHANNEL.registerMessage(CountReply.Handler.class, CountReply.class, id++, Side.CLIENT);
    }

    /** Client asks the server about a slot. Send what the player wants to do, never values to trust. */
    public static class CountRequest implements IMessage {
        int slot;
        public CountRequest() {}                       // required no-arg constructor
        public CountRequest(int slot) { this.slot = slot; }
        @Override public void fromBytes(ByteBuf buf) { slot = buf.readInt(); }
        @Override public void toBytes(ByteBuf buf) { buf.writeInt(slot); }

        public static class Handler implements IMessageHandler<CountRequest, IMessage> {
            @Override
            public IMessage onMessage(CountRequest msg, MessageContext ctx) {
                EntityPlayerMP player = ctx.getServerHandler().player;
                // Handlers run on the network thread: hop to the server thread before touching the world.
                player.getServerWorld().addScheduledTask(() -> {
                    if (msg.slot < 0 || msg.slot >= player.inventory.getSizeInventory()) return;   // validate input
                    CHANNEL.sendTo(new CountReply(player.inventory.getStackInSlot(msg.slot).getCount()), player);
                });
                return null;
            }
        }
    }

    public static class CountReply implements IMessage {
        int count;
        public CountReply() {}
        public CountReply(int count) { this.count = count; }
        @Override public void fromBytes(ByteBuf buf) { count = buf.readInt(); }
        @Override public void toBytes(ByteBuf buf) { buf.writeInt(count); }

        public static class Handler implements IMessageHandler<CountReply, IMessage> {
            @Override
            public IMessage onMessage(CountReply msg, MessageContext ctx) {
                ClientEvents.onCountReply(msg.count);    // client-only work lives in a client-only class
                return null;
            }
        }
    }

    private ModNetwork() {}
}
