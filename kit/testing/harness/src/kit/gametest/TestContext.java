package kit.gametest;

import com.mojang.authlib.GameProfile;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.*;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityList;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.network.EnumPacketDirection;
import net.minecraft.network.NetHandlerPlayServer;
import net.minecraft.network.NetworkManager;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.GameType;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.util.FakePlayer;

/**
 * Step sequencing, fixtures and assertions for one {@link Scenario}.
 * Declare steps in plan(); they run later, in order, on server END ticks.
 * Minecraft/Forge APIs stay available for anything the helpers do not cover.
 */
public final class TestContext {
    @FunctionalInterface public interface Action { void run() throws Exception; }
    private interface Step { boolean poll() throws Exception; }

    private final MinecraftServer server;
    private final BlockPos origin;
    private final int maxTicks;
    private final List<Step> steps = new ArrayList<>();
    private final List<Invariant> invariants = new ArrayList<>();
    private final Set<String> names = new HashSet<>();
    private int cursor, assertionCount;
    private long ticks;
    private boolean running;
    private String phase = "plan";

    TestContext(MinecraftServer server, BlockPos origin, int maxTicks) {
        this.server = server; this.origin = origin; this.maxTicks = maxTicks;
    }

    // ---- world and fixtures -------------------------------------------------------------

    public MinecraftServer server() { return server; }
    public WorldServer world() { return server.getWorld(0); }
    /** This scenario's own area on the flat test world: ground level, inside always-loaded spawn chunks. Use a 16x16 footprint. */
    public BlockPos origin() { return origin; }
    public long ticks() { return ticks; }

    public Block block(String id) {
        ResourceLocation key = new ResourceLocation(id);
        if (!Block.REGISTRY.containsKey(key)) throw new IllegalArgumentException("Unregistered block: " + id);
        return Block.REGISTRY.getObject(key);
    }
    public Item item(String id) {
        ResourceLocation key = new ResourceLocation(id);
        if (!Item.REGISTRY.containsKey(key)) throw new IllegalArgumentException("Unregistered item: " + id);
        return Item.REGISTRY.getObject(key);
    }
    public ItemStack stack(String id, int count) { return new ItemStack(item(id), count); }
    public void place(BlockPos pos, String id) { place(pos, block(id).getDefaultState()); }
    public void place(BlockPos pos, IBlockState state) {
        if (!world().setBlockState(pos, state, 3) && world().getBlockState(pos) != state)
            throw new IllegalStateException("Could not place " + state + " at " + pos);
    }
    public IBlockState state(BlockPos pos) { return world().getBlockState(pos); }
    public <T> T tile(BlockPos pos, Class<T> type) {
        TileEntity tile = world().getTileEntity(pos);
        if (!type.isInstance(tile)) throw new IllegalStateException("Expected " + type.getSimpleName() + " at " + pos + ", got " + tile);
        return type.cast(tile);
    }
    public IInventory inventory(BlockPos pos) { return tile(pos, IInventory.class); }
    /** Spawns an entity by registry ID (e.g. "minecraft:zombie") centered on a block position. */
    public Entity spawn(String id, BlockPos pos) {
        Entity entity = EntityList.createEntityByIDFromName(new ResourceLocation(id), world());
        if (entity == null) throw new IllegalArgumentException("Unknown entity: " + id);
        entity.setLocationAndAngles(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, 0, 0);
        if (!world().spawnEntity(entity)) throw new IllegalStateException("Could not spawn " + id);
        return entity;
    }

    // ---- simulated player ---------------------------------------------------------------

    /** A survival FakePlayer standing at origin with a working (offline) network handler. */
    public FakePlayer player(String name) {
        FakePlayer player = new FakePlayer(world(), new GameProfile(UUID.nameUUIDFromBytes(name.getBytes(StandardCharsets.UTF_8)), name));
        new NetHandlerPlayServer(server, new NetworkManager(EnumPacketDirection.SERVERBOUND), player);
        player.interactionManager.setGameType(GameType.SURVIVAL);
        player.setLocationAndAngles(origin.getX() + 0.5, origin.getY(), origin.getZ() + 0.5, 0, 0);
        return player;
    }
    /** Right-click with the held item in the air (Item.onItemRightClick path). */
    public EnumActionResult useItem(EntityPlayerMP player, EnumHand hand) {
        return player.interactionManager.processRightClick(player, world(), player.getHeldItem(hand), hand);
    }
    /** Completes a timed use (food, potion, bow release is different) as if the use duration elapsed. */
    public ItemStack finishUsingItem(EntityPlayerMP player, EnumHand hand) {
        ItemStack held = player.getHeldItem(hand);
        ItemStack result = held.getItem().onItemUseFinish(held, world(), player);
        player.setHeldItem(hand, result);
        return result;
    }
    /** Right-click a block face with the held item (Block.onBlockActivated / Item.onItemUse path). */
    public EnumActionResult useOnBlock(EntityPlayerMP player, BlockPos pos, EnumFacing face, EnumHand hand) {
        return player.interactionManager.processRightClickBlock(player, world(), player.getHeldItem(hand), hand, pos, face, 0.5F, 0.5F, 0.5F);
    }
    /** Survival block break with the held tool, including drops and tool damage. */
    public boolean breakBlock(EntityPlayerMP player, BlockPos pos) {
        return player.interactionManager.tryHarvestBlock(pos);
    }
    public void attack(EntityPlayerMP player, Entity target) { player.attackTargetEntityWithCurrentItem(target); }

    // ---- steps --------------------------------------------------------------------------

    /** Run an action as the next step. Use {@link #require} inside it for immediate assertions. */
    public void then(String label, Action action) {
        if (label == null || label.isEmpty() || label.length() > 96) throw new IllegalArgumentException("Step label must be 1..96 characters");
        add(() -> { phase = label; action.run(); return true; });
    }
    /** Named assertion evaluated as the next step. */
    public void check(String name, BooleanSupplier condition, Supplier<String> detail) {
        name(name);
        add(() -> { phase = name; record(name, condition.getAsBoolean(), detail.get()); return true; });
    }
    /** Immediate assertion for use inside then(...). */
    public void require(boolean condition, String message) {
        assertionCount++;
        if (!condition) throw new AssertionError(message);
    }
    public void waitTicks(int count) {
        if (count < 1 || count > maxTicks) throw new IllegalArgumentException("waitTicks outside the scenario tick budget");
        add(new Step() { long start = -1;
            public boolean poll() { phase = "wait-" + count; if (start < 0) start = ticks; return ticks - start >= count; } });
    }
    /** Waits until the condition holds; fails after timeoutTicks. */
    public void waitUntil(String name, int timeoutTicks, BooleanSupplier condition, Supplier<String> detail) {
        name(name);
        if (timeoutTicks < 1 || timeoutTicks > maxTicks) throw new IllegalArgumentException("waitUntil outside the scenario tick budget");
        add(new Step() { long start = -1;
            public boolean poll() {
                phase = name; if (start < 0) start = ticks;
                if (condition.getAsBoolean()) { record(name, true, detail.get()); return true; }
                if (ticks - start >= timeoutTicks) record(name, false, "timed out after " + timeoutTicks + " ticks: " + detail.get());
                return false;
            } });
    }
    /** From this point on, checked every tick until the scenario ends. */
    public void invariant(String name, BooleanSupplier condition, Supplier<String> detail) {
        name(name);
        add(() -> { invariants.add(new Invariant(name, condition, detail)); return true; });
    }

    // ---- harness internals --------------------------------------------------------------

    private void add(Step step) {
        if (running) throw new IllegalStateException("Declare steps in plan(), not while steps run");
        if (steps.size() >= 256) throw new IllegalStateException("At most 256 steps per scenario");
        steps.add(step);
    }
    private void name(String name) {
        if (name == null || !name.matches("[a-z][a-z0-9.-]{0,95}") || !names.add(name))
            throw new IllegalArgumentException("Assertion names must be unique lowercase ids: " + name);
    }
    private void record(String name, boolean passed, String detail) {
        assertionCount++;
        if (!passed) throw new AssertionError(detail);
    }
    private final class Invariant {
        final String name; final BooleanSupplier condition; final Supplier<String> detail;
        Invariant(String name, BooleanSupplier condition, Supplier<String> detail) { this.name = name; this.condition = condition; this.detail = detail; }
    }
    /** Returns true once every step has run. */
    boolean tick() throws Exception {
        running = true; ticks++;
        if (ticks > maxTicks) throw new AssertionError("exceeded " + maxTicks + " ticks during " + phase);
        int budget = 64;
        while (cursor < steps.size() && budget-- > 0) { if (!steps.get(cursor).poll()) break; cursor++; }
        for (Invariant invariant : invariants) {
            if (!invariant.condition.getAsBoolean()) { phase = invariant.name; record(invariant.name, false, "broken at tick " + ticks + ": " + invariant.detail.get()); }
        }
        if (cursor < steps.size()) return false;
        assertionCount += invariants.size();
        phase = "complete";
        if (assertionCount == 0) throw new AssertionError("scenario finished without any assertions");
        return true;
    }
    String phase() { return phase; }
    int assertions() { return assertionCount; }

    /** Clears a 16x16x16 area at origin to air above a stone floor. */
    void prepareArea() {
        WorldServer world = world();
        for (int x = 0; x < 16; x++) for (int z = 0; z < 16; z++) {
            world.setBlockState(origin.add(x, -1, z), Blocks.STONE.getDefaultState(), 2);
            for (int y = 0; y < 16; y++) world.setBlockState(origin.add(x, y, z), Blocks.AIR.getDefaultState(), 2);
        }
    }
}
