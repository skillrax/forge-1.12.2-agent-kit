# Packets (SimpleNetworkWrapper)

Use packets when the client must ask the server to do something (button with data, keybind action), or the server must tell the client something that tile sync, container properties or vanilla packets don't already carry.

```java
public final class ModNetwork {
    public static final SimpleNetworkWrapper CHANNEL = NetworkRegistry.INSTANCE.newSimpleChannel(MyMod.MODID);

    public static void init() {                                      // call from preInit
        int id = 0;
        CHANNEL.registerMessage(CountRequest.Handler.class, CountRequest.class, id++, Side.SERVER);   // handled on the server
        CHANNEL.registerMessage(CountReply.Handler.class, CountReply.class, id++, Side.CLIENT);       // handled on the client
    }

    public static class CountRequest implements IMessage {
        int slot;
        public CountRequest() {}                                     // required
        public CountRequest(int slot) { this.slot = slot; }
        @Override public void fromBytes(ByteBuf buf) { slot = buf.readInt(); }
        @Override public void toBytes(ByteBuf buf) { buf.writeInt(slot); }

        public static class Handler implements IMessageHandler<CountRequest, IMessage> {
            @Override public IMessage onMessage(CountRequest msg, MessageContext ctx) {
                EntityPlayerMP player = ctx.getServerHandler().player;
                player.getServerWorld().addScheduledTask(() -> {     // network thread -> server thread
                    if (msg.slot < 0 || msg.slot >= player.inventory.getSizeInventory()) return;   // validate everything
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
            @Override public IMessage onMessage(CountReply msg, MessageContext ctx) {
                ClientEvents.onCountReply(msg.count);                // client code stays in a client-only class
                return null;
            }
        }
    }
}

// In ClientEvents (client only):
public static void onCountReply(int count) {
    Minecraft mc = Minecraft.getMinecraft();
    mc.addScheduledTask(() -> mc.player.sendStatusMessage(new TextComponentString("count: " + count), true));
}
```

Sending: client `ModNetwork.CHANNEL.sendToServer(new CountRequest(3))`; server `sendTo(msg, playerMP)`, `sendToAllAround(msg, new NetworkRegistry.TargetPoint(dim, x, y, z, range))`, `sendToAll(msg)`.

## Rules

- Handlers run on the network thread: always `addScheduledTask` before touching the world, players or GUIs.
- The server trusts nothing from the client: send an action (button id, slot), not results; check ranges, distance to the block, that the right container is open (`player.openContainer`), and permissions.
- Act only for the sending player (`ctx.getServerHandler().player`), never a player named in the packet.
- Register the same message ids in the same order on both sides (the `init()` above runs on both).
- The channel name is at most 20 characters (`check server` fails on longer ones): a longer mod id needs a shorter name. Single player never sends it, so only LAN or dedicated-server clients would disconnect.
- Size: a client-to-server packet holds at most 32767 bytes, and `ByteBufUtils.readTag` refuses NBT over 2 MB. Send large inventories in parts, or only what changed.
- `ByteBuf` read order must match write order exactly. Strings: `ByteBufUtils.writeUTF8String`/`readUTF8String`; NBT: `ByteBufUtils.writeTag`/`readTag`.

## Test

Packets need a real client connection; game tests cannot send them. Put the server logic in a plain method (`handleCount(EntityPlayerMP, int)`) and test that method directly; check the client side by hand.
