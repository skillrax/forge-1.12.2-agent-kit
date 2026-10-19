package acceptance;

import com.mojang.authlib.GameProfile;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import kit.gametest.TestContext;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.network.EnumPacketDirection;
import net.minecraft.network.NetHandlerPlayServer;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.Packet;
import net.minecraft.network.play.server.SPacketChat;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.DamageSource;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.world.GameType;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.ForgeHooks;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.entity.player.PlayerDropsEvent;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.relauncher.ReflectionHelper;

/**
 * Helpers for the hidden acceptance tests (qa/acceptance). Tests use registry ids only, never the mod's classes,
 * so they apply to any implementation of the same request. Copied next to the tests by Run-Series.ps1.
 */
public final class Accept {
    private Accept() {}

    /** Component text plus translation keys and their arguments: the test server may not translate mod keys. */
    public static String text(ITextComponent component) {
        StringBuilder out = new StringBuilder();
        flatten(component, out);
        return out.toString();
    }
    private static void flatten(Object part, StringBuilder out) {
        if (part instanceof TextComponentTranslation) {
            TextComponentTranslation translation = (TextComponentTranslation) part;
            out.append(translation.getKey()).append(' ');
            try { out.append(translation.getUnformattedComponentText()).append(' '); } catch (RuntimeException ignored) { }
            for (Object arg : translation.getFormatArgs()) { flatten(arg, out); out.append(' '); }
        } else if (part instanceof ITextComponent) {
            out.append(((ITextComponent) part).getUnformattedComponentText());
        } else {
            out.append(part);
            return;
        }
        for (ITextComponent sibling : ((ITextComponent) part).getSiblings()) flatten(sibling, out);
    }

    /** Whole numbers in a text ("x: -48, y 4" -> [-48, 4]). */
    public static List<Integer> numbers(String text) {
        List<Integer> found = new ArrayList<>();
        Matcher m = Pattern.compile("-?\\d+").matcher(text);
        while (m.find()) { try { found.add(Integer.parseInt(m.group())); } catch (NumberFormatException ignored) { } }
        return found;
    }

    /**
     * A survival player that records every message it gets, chat broadcasts included (TestContext.player misses
     * PlayerList broadcasts), and reports its real position (FakePlayer's getPositionVector is always 0,0,0).
     * Like any FakePlayer it is not an operator: canUseCommand is false.
     */
    public static class Listener extends FakePlayer {
        public final List<String> received = new ArrayList<>();
        public Listener(WorldServer world, String name) {
            super(world, new GameProfile(UUID.nameUUIDFromBytes(("acceptance:" + name).getBytes(StandardCharsets.UTF_8)), name));
            new Handler(world.getMinecraftServer(), this);
            interactionManager.setGameType(GameType.SURVIVAL);
        }
        @Override public void sendMessage(ITextComponent component) { received.add(text(component)); }
        @Override public void sendStatusMessage(ITextComponent component, boolean actionBar) { received.add(text(component)); }
        @Override public Vec3d getPositionVector() { return new Vec3d(posX, posY, posZ); }
        // Multi-line on purpose: the kit's client-only lint only counts multi-line declarations as the test's own names.
        public String all() {
            return String.join("\n", received);
        }
    }
    private static final class Handler extends NetHandlerPlayServer {
        Handler(MinecraftServer server, Listener player) { super(server, new NetworkManager(EnumPacketDirection.SERVERBOUND), player); }
        @Override public void sendPacket(Packet<?> packet) {
            if (packet instanceof SPacketChat) {
                ITextComponent component = ReflectionHelper.getPrivateValue(SPacketChat.class, (SPacketChat) packet, "chatComponent", "field_148919_a");
                ((Listener) player).received.add(text(component));
            }
        }
        @Override public void disconnect(ITextComponent reason) { }
    }

    /** Live item entities within radius of a block centre. */
    public static List<EntityItem> items(TestContext t, BlockPos center, double radius) {
        List<EntityItem> found = new ArrayList<>();
        for (EntityItem e : t.world().getEntitiesWithinAABB(EntityItem.class, new AxisAlignedBB(center).grow(radius))) if (!e.isDead) found.add(e);
        return found;
    }
    public static int countEntities(List<EntityItem> items, Item item) {
        int total = 0;
        for (EntityItem e : items) if (e.getItem().getItem() == item) total += e.getItem().getCount();
        return total;
    }
    /** Count of an item anywhere in a player's inventory (main, armor, offhand). */
    public static int countInventory(EntityPlayerMP player, Item item) {
        int total = 0;
        for (List<ItemStack> part : Arrays.asList(player.inventory.mainInventory, player.inventory.armorInventory, player.inventory.offHandInventory))
            for (ItemStack s : part) if (s.getItem() == item) total += s.getCount();
        return total;
    }
    /**
     * A player's death as EntityPlayerMP.onDeath does it (test players never die): LivingDeathEvent, then, unless
     * keepInventory, every inventory stack captured as a drop and PlayerDropsEvent. Returns the drops that would
     * land in the world (none when the event is cancelled or keepInventory is on).
     */
    public static List<EntityItem> die(EntityPlayerMP player, DamageSource cause) {
        List<EntityItem> landed = new ArrayList<>();
        if (ForgeHooks.onLivingDeath(player, cause)) return landed;
        if (!player.world.getGameRules().getBoolean("keepInventory")) {
            player.captureDrops = true;
            player.capturedDrops.clear();
            player.inventory.dropAllItems();
            player.captureDrops = false;
            if (!MinecraftForge.EVENT_BUS.post(new PlayerDropsEvent(player, cause, player.capturedDrops, false))) landed.addAll(player.capturedDrops);
        }
        return landed;
    }
    /** The respawn copy as PlayerList.recreatePlayerEntity makes it: copyFrom (fires PlayerEvent.Clone), then PlayerRespawnEvent. */
    public static void respawn(EntityPlayerMP old, EntityPlayerMP fresh, boolean keepEverything) {
        fresh.copyFrom(old, keepEverything);
        FMLCommonHandler.instance().firePlayerRespawnEvent(fresh, keepEverything);
    }

    public static String describe(ItemStack stack) {
        return stack.isEmpty() ? "empty" : stack.getCount() + "x " + stack.getItem().getRegistryName() + (stack.hasTagCompound() ? stack.getTagCompound().toString() : "");
    }
}
