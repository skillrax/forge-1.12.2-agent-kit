package acceptance;

import io.netty.channel.ChannelHandler;
import io.netty.util.Attribute;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.*;
import java.util.function.Supplier;
import kit.gametest.GameTest;
import kit.gametest.Scenario;
import kit.gametest.TestContext;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.INetHandler;
import net.minecraft.util.DamageSource;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.network.FMLEmbeddedChannel;
import net.minecraftforge.fml.common.network.FMLIndexedMessageToMessageCodec;
import net.minecraftforge.fml.common.network.NetworkRegistry;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.SimpleChannelHandlerWrapper;
import net.minecraftforge.fml.common.network.simpleimpl.SimpleIndexedCodec;
import net.minecraftforge.fml.relauncher.ReflectionHelper;
import net.minecraftforge.fml.relauncher.Side;

/**
 * S6 "Tool belt with hotkey", from the request only (mod id toolbelt). The key press is client-side; what the
 * server sees is the mod's client-to-server message. These tests find that message on the mod's SimpleNetworkWrapper
 * channel (the server-bound type with a handler) and deliver it as if it came from the player's connection, so the
 * swap must be done by the server. Presses are 5 ticks apart (mods may ignore repeats within one tick). Hand A/B/C = diamond sword / pickaxe / axe.
 */
public final class S6Acceptance {
    static final String MOD = "toolbelt";
    static final Item A = Items.DIAMOND_SWORD, B = Items.DIAMOND_PICKAXE, C = Items.DIAMOND_AXE;

    static final class Wire {
        final FMLEmbeddedChannel channel; final Class<?> type;
        Wire(FMLEmbeddedChannel channel, Class<?> type) { this.channel = channel; this.type = type; }
        @Override public String toString() { return type.getName(); }
    }
    static List<Wire> serverBound() {
        try { return findServerBound(); } catch (Exception e) { throw new IllegalStateException("reading the mod's network channels failed", e); }
    }
    private static List<Wire> findServerBound() throws Exception {
        List<Wire> found = new ArrayList<>();
        for (String name : NetworkRegistry.INSTANCE.channelNamesFor(Side.SERVER)) {
            FMLEmbeddedChannel channel = NetworkRegistry.INSTANCE.getChannel(name, Side.SERVER);
            if (channel == null || channel.pipeline().get(SimpleIndexedCodec.class) == null) continue;
            Map<?, ?> discriminators = ReflectionHelper.getPrivateValue(FMLIndexedMessageToMessageCodec.class, channel.pipeline().get(SimpleIndexedCodec.class), "discriminators");
            for (Object type : discriminators.values()) {
                IMessage message = create((Class<?>) type);
                if (message == null) continue;
                for (Map.Entry<String, ChannelHandler> handler : channel.pipeline())
                    if (handler.getValue() instanceof SimpleChannelHandlerWrapper && ((SimpleChannelHandlerWrapper<?, ?>) handler.getValue()).acceptInboundMessage(message)) { found.add(new Wire(channel, (Class<?>) type)); break; }
            }
        }
        return found;
    }
    static IMessage create(Class<?> type) {
        try { Constructor<?> c = type.getDeclaredConstructor(); c.setAccessible(true); return (IMessage) c.newInstance(); }
        catch (ReflectiveOperationException | ClassCastException e) { return null; }
    }
    /** The one server-bound message, or the one whose name says cycle/swap/rotate/key when there are several. */
    static Wire cycleMessage(TestContext t) {
        List<Wire> all = serverBound();
        if (all.size() == 1) return all.get(0);
        List<Wire> named = new ArrayList<>();
        for (Wire w : all) if (w.type.getSimpleName().toLowerCase(Locale.ROOT).matches(".*(cycle|swap|rotate|key|press).*")) named.add(w);
        t.require(named.size() == 1, "cannot tell which client-to-server message is the key press: " + all);
        return named.get(0);
    }
    /** Delivers a fresh key-press message as if it came from the player's connection (handlers run at once on the server thread). */
    static void press(TestContext t, EntityPlayerMP player) throws Exception {
        Wire wire = cycleMessage(t);
        player.ticksExisted += 5;   // test players never tick; a real one has lived through the ticks between presses
        Attribute<INetHandler> from = wire.channel.attr(NetworkRegistry.NET_HANDLER);
        INetHandler previous = from.get();
        from.set(player.connection);
        try { wire.channel.writeInbound(create(wire.type)); }
        finally { from.set(previous); wire.channel.outboundMessages().clear(); wire.channel.inboundMessages().clear(); }
    }
    static Accept.Listener player(TestContext t, String name) {
        Accept.Listener p = new Accept.Listener(t.world(), name);
        p.setLocationAndAngles(t.origin().getX() + 4.5, t.origin().getY(), t.origin().getZ() + 4.5, 0, 0);
        p.inventory.currentItem = 0;
        return p;
    }
    static Item hand(EntityPlayerMP p) { ItemStack s = p.getHeldItemMainhand(); return s.isEmpty() ? null : s.getItem(); }
    static String name(Item i) { return i == null ? "empty" : i.getRegistryName().getPath(); }
    /** Declares: A, B, C put in by cycling (hand A, press, hand B, press, hand C, press). Belt is then [C, B, A], hand empty. */
    static void loadBelt(TestContext t, Supplier<EntityPlayerMP> p) {
        for (Item item : new Item[]{A, B, C}) {
            t.then("hold " + name(item) + " and press the key", () -> {
                p.get().setHeldItem(EnumHand.MAIN_HAND, new ItemStack(item));
                press(t, p.get());
                t.require(hand(p.get()) == null, "after putting " + name(item) + " in by pressing the key, the hand holds " + name(hand(p.get())) + " (expected empty: slot 3 was empty)");
            });
            t.waitTicks(5);
        }
    }
    /** Declares n presses, 5 ticks apart, recording the hand after each. */
    static void presses(TestContext t, Supplier<EntityPlayerMP> p, int n, List<String> hands) {
        for (int i = 0; i < n; i++) {
            t.then("press the key", () -> { press(t, p.get()); hands.add(name(hand(p.get()))); });
            t.waitTicks(5);
        }
    }
    /** Hands after 4 presses on a belt [C, B, A] with an empty hand: A, B, C, empty. */
    static final List<String> FULL_CYCLE = Arrays.asList(name(A), name(B), name(C), "empty");

    @GameTest(value = "acceptance-s6-cycles-four-ways")
    public static class Cycles implements Scenario {
        @Override public void plan(TestContext t) {
            List<String> hands = new ArrayList<>();
            EntityPlayerMP[] p = new EntityPlayerMP[1];
            t.then("a player", () -> p[0] = player(t, "Belter"));
            loadBelt(t, () -> p[0]);
            presses(t, () -> p[0], 5, hands);
            t.check("hand-1-2-3-cycle", () -> hands.equals(Arrays.asList(name(A), name(B), name(C), "empty", name(A))), () -> "hand after each press: " + hands + ", expected " + name(A) + ", " + name(B) + ", " + name(C) + ", empty, " + name(A));
        }
    }

    @GameTest(value = "acceptance-s6-key-message-carries-no-items")
    public static class NoItemsOnTheWire implements Scenario {
        @Override public void plan(TestContext t) {
            List<String> fields = new ArrayList<>();
            t.check("server-decides-what-moves", () -> {
                Wire w = cycleMessage(t);
                for (Class<?> c = w.type; c != null && c != Object.class; c = c.getSuperclass())
                    for (Field f : c.getDeclaredFields()) {
                        if (Modifier.isStatic(f.getModifiers())) continue;
                        Class<?> type = f.getType();
                        if (ItemStack.class.isAssignableFrom(type) || NBTBase.class.isAssignableFrom(type) || Item.class.isAssignableFrom(type) || Collection.class.isAssignableFrom(type) || type.isArray())
                            fields.add(c.getSimpleName() + "." + f.getName() + ": " + type.getSimpleName());
                    }
                return fields.isEmpty();
            }, () -> "the key-press message carries item data a modified client could fake: " + fields);
        }
    }

    @GameTest(value = "acceptance-s6-survives-relog")
    public static class Relog implements Scenario {
        @Override public void plan(TestContext t) {
            List<String> hands = new ArrayList<>();
            EntityPlayerMP[] p = new EntityPlayerMP[2];
            t.then("a player", () -> p[0] = player(t, "Relogger"));
            loadBelt(t, () -> p[0]);
            t.then("save the player, load it as a new player object", () -> {
                NBTTagCompound saved = p[0].writeToNBT(new NBTTagCompound());
                p[1] = player(t, "Relogger");
                p[1].readFromNBT(saved);
                p[1].inventory.currentItem = 0;
            });
            presses(t, () -> p[1], 4, hands);
            t.check("belt-back-after-relog", () -> hands.equals(FULL_CYCLE), () -> "hand after each press after relog: " + hands + ", expected " + FULL_CYCLE);
        }
    }

    @GameTest(value = "acceptance-s6-survives-dimension-change")
    public static class Dimension implements Scenario {
        @Override public void plan(TestContext t) {
            List<String> nether = new ArrayList<>(), end = new ArrayList<>();
            EntityPlayerMP[] p = new EntityPlayerMP[3];
            t.then("two players", () -> { p[0] = player(t, "Traveller"); p[1] = player(t, "EndWalker"); });
            loadBelt(t, () -> p[0]);
            loadBelt(t, () -> p[1]);
            t.then("Nether portal and back (same player object); leaving the End (copied player)", () -> {
                FMLCommonHandler.instance().firePlayerChangedDimensionEvent(p[0], 0, -1);
                FMLCommonHandler.instance().firePlayerChangedDimensionEvent(p[0], -1, 0);
                p[2] = player(t, "EndWalker");
                Accept.respawn(p[1], p[2], true);   // copyFrom(keepEverything), Clone with wasDeath false
                p[2].inventory.currentItem = 0;
            });
            presses(t, () -> p[0], 4, nether);
            presses(t, () -> p[2], 4, end);
            t.check("belt-kept-through-the-nether", () -> nether.equals(FULL_CYCLE), () -> "after Nether and back: " + nether + ", expected " + FULL_CYCLE);
            t.check("belt-kept-leaving-the-end", () -> end.equals(FULL_CYCLE), () -> "after the End return copy: " + end + ", expected " + FULL_CYCLE);
        }
    }

    @GameTest(value = "acceptance-s6-death-keepinventory")
    public static class KeepInventory implements Scenario {
        @Override public void plan(TestContext t) {
            List<String> hands = new ArrayList<>();
            List<EntityItem> landed = new ArrayList<>();
            EntityPlayerMP[] p = new EntityPlayerMP[2];
            t.then("a player", () -> p[0] = player(t, "Keeper"));
            loadBelt(t, () -> p[0]);
            t.then("die with keepInventory on and respawn", () -> {
                t.world().getGameRules().setOrCreateGameRule("keepInventory", "true");
                try {
                    landed.addAll(Accept.die(p[0], DamageSource.LAVA));
                    p[1] = player(t, "Keeper");
                    Accept.respawn(p[0], p[1], false);
                    p[1].inventory.currentItem = 0;
                } finally { t.world().getGameRules().setOrCreateGameRule("keepInventory", "false"); }
            });
            presses(t, () -> p[1], 4, hands);
            t.check("belt-kept-once", () -> hands.equals(FULL_CYCLE) && landed.isEmpty(), () -> "after respawn: " + hands + ", expected " + FULL_CYCLE + "; dropped " + landed.size());
        }
    }

    @GameTest(value = "acceptance-s6-death-drops-belt")
    public static class DropsOnDeath implements Scenario {
        @Override public void plan(TestContext t) {
            List<String> hands = new ArrayList<>();
            int[] onGround = new int[3];
            EntityPlayerMP[] p = new EntityPlayerMP[2];
            t.then("a player", () -> p[0] = player(t, "Loser"));
            loadBelt(t, () -> p[0]);
            t.then("die without keepInventory and respawn", () -> {
                t.world().getGameRules().setOrCreateGameRule("keepInventory", "false");
                List<EntityItem> landed = Accept.die(p[0], DamageSource.LAVA);
                // Drops in the event list, or spawned into the world by the mod itself.
                List<EntityItem> around = Accept.items(t, new BlockPos(p[0]), 4);
                Item[] belt = {A, B, C};
                for (int i = 0; i < 3; i++) onGround[i] = Accept.countEntities(landed, belt[i]) + Accept.countEntities(around, belt[i]);
                p[1] = player(t, "Loser");
                Accept.respawn(p[0], p[1], false);
                p[1].inventory.currentItem = 0;
            });
            presses(t, () -> p[1], 4, hands);
            t.check("belt-items-drop", () -> onGround[0] == 1 && onGround[1] == 1 && onGround[2] == 1, () -> "dropped: " + name(A) + " " + onGround[0] + ", " + name(B) + " " + onGround[1] + ", " + name(C) + " " + onGround[2]);
            t.check("belt-empty-after-respawn", () -> hands.equals(Arrays.asList("empty", "empty", "empty", "empty")), () -> "hand after each press after respawn: " + hands);
        }
    }
}
