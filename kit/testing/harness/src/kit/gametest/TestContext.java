package kit.gametest;

import com.mojang.authlib.GameProfile;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.*;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityList;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.crafting.CraftingManager;
import net.minecraft.item.crafting.FurnaceRecipes;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraftforge.oredict.OreDictionary;
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
import net.minecraft.util.DamageSource;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.storage.loot.LootContext;
import net.minecraft.world.storage.loot.LootTable;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.world.GameType;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.fml.common.ObfuscationReflectionHelper;
import net.minecraftforge.items.CapabilityItemHandler;
import net.minecraftforge.items.IItemHandler;

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
    private final Set<BlockPos> placed = new LinkedHashSet<>();
    private final Map<FakePlayer, List<String>> messages = new IdentityHashMap<>();
    private final List<FakePlayer> targets = new ArrayList<>();
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
    /** "modid:name", "modid:name@meta" or "ore:oreDictName". */
    public ItemStack stack(String id, int count) {
        if (id.startsWith("ore:")) {
            List<ItemStack> ores = OreDictionary.getOres(id.substring(4));
            if (ores.isEmpty()) throw new IllegalArgumentException("No ore dictionary entries for " + id);
            ItemStack ore = ores.get(0).copy();
            if (ore.getMetadata() == OreDictionary.WILDCARD_VALUE) ore.setItemDamage(0);
            ore.setCount(count);
            return ore;
        }
        int at = id.indexOf('@');
        return at < 0 ? new ItemStack(item(id), count) : new ItemStack(item(id.substring(0, at)), count, Integer.parseInt(id.substring(at + 1)));
    }
    /** Result of the crafting grid, row-major 3x3; "" or null = empty cell. Fewer than 9 cells fill from the top-left. */
    public ItemStack craft(String... cells) {
        if (cells.length > 9) throw new IllegalArgumentException("A crafting grid has 9 cells");
        InventoryCrafting grid = new InventoryCrafting(new Container() {
            @Override public boolean canInteractWith(EntityPlayer player) { return false; }
        }, 3, 3);
        for (int i = 0; i < cells.length; i++) if (cells[i] != null && !cells[i].isEmpty()) grid.setInventorySlotContents(i, stack(cells[i], 1));
        return CraftingManager.findMatchingResult(grid, world());
    }
    /** Furnace result for one item (ItemStack.EMPTY if none). */
    public ItemStack smelt(String id) { return FurnaceRecipes.instance().getSmeltingResult(stack(id, 1)); }
    private int dropSlot;
    /** Places the block in a free spot, mines it with the tool (null/"" = hand) and returns the dropped stacks. */
    public List<ItemStack> drops(String blockId, String toolId) {
        int slot = dropSlot++ % 49;
        BlockPos pos = origin.add(1 + (slot % 7) * 2, 0, 1 + (slot / 7) * 2);
        place(pos, blockId);
        FakePlayer miner = player("drops-" + slot);
        miner.setHeldItem(EnumHand.MAIN_HAND, toolId == null || toolId.isEmpty() ? ItemStack.EMPTY : stack(toolId, 1));
        if (!breakBlock(miner, pos)) throw new AssertionError("could not break " + blockId + " with " + toolId);
        List<ItemStack> result = new ArrayList<>();
        for (EntityItem drop : world().getEntitiesWithinAABB(EntityItem.class, new AxisAlignedBB(pos).grow(1.5))) {
            result.add(drop.getItem().copy());
            drop.setDead();
        }
        return result;
    }
    /** Total count of one item in a list of stacks. */
    public int count(List<ItemStack> stacks, String id) {
        Item wanted = item(id); int total = 0;
        for (ItemStack s : stacks) if (s.getItem() == wanted) total += s.getCount();
        return total;
    }
    public void place(BlockPos pos, String id) { place(pos, block(id).getDefaultState()); }
    public void place(BlockPos pos, IBlockState state) {
        placed.add(pos.toImmutable());
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
    public FakePlayer player(String name) { return newPlayer(name, false); }
    /**
     * A player that mob AI can target and hurt. player() is not in world.playerEntities, the only list
     * World.getNearestAttackablePlayer searches, and FakePlayer ignores damage; this one is added to the world,
     * takes damage, and is removed when the test ends. Still never ticks (see TESTING.md).
     */
    public FakePlayer targetPlayer(String name) {
        FakePlayer player = newPlayer(name, true);
        player.capabilities.disableDamage = false;
        // EntityPlayerMP starts with 60 ticks of respawn invulnerability that only onUpdate counts down; FakePlayer never ticks.
        ObfuscationReflectionHelper.setPrivateValue(EntityPlayerMP.class, player, 0, "field_147101_bU");
        if (!world().spawnEntity(player)) throw new IllegalStateException("Could not add target player " + name + " (name already used?)");
        targets.add(player);
        return player;
    }
    private FakePlayer newPlayer(String name, boolean vulnerable) {
        List<String> received = new ArrayList<>();
        // Forge's FakePlayer drops chat and action-bar messages; these are recorded for messages(player).
        FakePlayer player = new FakePlayer(world(), new GameProfile(UUID.nameUUIDFromBytes(name.getBytes(StandardCharsets.UTF_8)), name)) {
            @Override public void sendMessage(ITextComponent component) { received.add(text(component)); }
            @Override public void sendStatusMessage(ITextComponent component, boolean actionBar) { received.add((actionBar ? "actionbar: " : "") + text(component)); }
            @Override public boolean isEntityInvulnerable(DamageSource source) { return !vulnerable && super.isEntityInvulnerable(source); }
        };
        messages.put(player, received);
        new NetHandlerPlayServer(server, new NetworkManager(EnumPacketDirection.SERVERBOUND), player);
        player.interactionManager.setGameType(GameType.SURVIVAL);
        player.setLocationAndAngles(origin.getX() + 0.5, origin.getY(), origin.getZ() + 0.5, 0, 0);
        return player;
    }
    /**
     * Chat and action-bar messages sent to a player from player(), oldest first. Action-bar entries start with
     * "actionbar: "; translated messages end with " [translation.key]", since the server may not translate mod keys.
     */
    public List<String> messages(FakePlayer player) {
        List<String> received = messages.get(player);
        if (received == null) throw new IllegalArgumentException("messages() needs a player made by t.player(name)");
        return Collections.unmodifiableList(received);
    }
    private static String text(ITextComponent component) {
        String text = component.getUnformattedText();
        return component instanceof TextComponentTranslation ? text + " [" + ((TextComponentTranslation) component).getKey() + "]" : text;
    }
    /** One roll of a loot table such as "modid:entities/name". Throws if the table does not exist. */
    public List<ItemStack> loot(String table) { return loot(table, null); }
    /** One roll with a looted entity in the context (tables with entity conditions). */
    public List<ItemStack> loot(String table, Entity looted) {
        LootTable loot = world().getLootTableManager().getLootTableFromLocation(new ResourceLocation(table));
        if (loot == LootTable.EMPTY_LOOT_TABLE) throw new IllegalArgumentException("No loot table " + table + " (file: assets/<modid>/loot_tables/<path>.json)");
        LootContext.Builder context = new LootContext.Builder(world());
        if (looted != null) context.withLootedEntity(looted);
        return loot.generateLootForPools(world().rand, context.build());
    }
    /** Removes target players; called by the harness when the test ends, passed or failed. */
    void cleanup() {
        for (FakePlayer player : targets) world().removeEntityDangerously(player);
        targets.clear();
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

    /**
     * Failure report context: the blocks the test placed, then other non-air blocks in its area
     * (positions relative to origin()), with redstone power received and inventory contents,
     * then entity counts. Bounded, so a failure costs a few lines, not a log dive.
     */
    List<String> snapshot() {
        List<String> lines = new ArrayList<>();
        try {
            WorldServer world = world();
            Set<BlockPos> order = new LinkedHashSet<>(placed);
            for (int y = -1; y < 16; y++) for (int x = 0; x < 16; x++) for (int z = 0; z < 16; z++) {
                BlockPos pos = origin.add(x, y, z);
                Block block = world.getBlockState(pos).getBlock();
                if (block != Blocks.AIR && !(y == -1 && block == Blocks.STONE)) order.add(pos);
            }
            int shown = 0;
            for (BlockPos pos : order) {
                if (shown++ == 12) { lines.add("... " + (order.size() - 12) + " more blocks"); break; }
                lines.add(describe(world, pos));
            }
            Map<String, Integer> entities = new TreeMap<>();
            for (Entity entity : world.getEntitiesWithinAABB(Entity.class, new AxisAlignedBB(origin.add(0, -1, 0), origin.add(16, 16, 16)))) {
                ResourceLocation key = EntityList.getKey(entity);
                String name = entity instanceof EntityItem ? "item " + id(((EntityItem) entity).getItem())
                        : entity instanceof EntityPlayer ? "player " + entity.getName()
                        : key == null ? entity.getClass().getSimpleName() : shortId(key.toString());
                entities.merge(name, 1, Integer::sum);
            }
            if (!entities.isEmpty()) {
                StringBuilder line = new StringBuilder("entities:");
                for (Map.Entry<String, Integer> e : entities.entrySet()) line.append(' ').append(e.getValue()).append("x ").append(e.getKey()).append(',');
                lines.add(cut(line.substring(0, line.length() - 1)));
            }
        } catch (Throwable error) {
            lines.add("area snapshot failed: " + error);
        }
        return lines;
    }
    private String describe(WorldServer world, BlockPos pos) {
        BlockPos rel = pos.subtract(origin);
        StringBuilder line = new StringBuilder("(" + rel.getX() + "," + rel.getY() + "," + rel.getZ() + ") " + shortId(String.valueOf(world.getBlockState(pos))));
        int power = world.getRedstonePowerFromNeighbors(pos);
        if (power > 0) line.append(" receives redstone ").append(power);
        TileEntity tile = world.getTileEntity(pos);
        if (tile != null) {
            IItemHandler items = tile.hasCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, null)
                    ? tile.getCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, null) : null;
            if (items == null) line.append(" tile ").append(tile.getClass().getSimpleName());
            else {
                List<String> stacks = new ArrayList<>();
                for (int slot = 0; slot < items.getSlots(); slot++) {
                    ItemStack stack = items.getStackInSlot(slot);
                    if (!stack.isEmpty()) stacks.add(stack.getCount() + "x " + id(stack));
                }
                line.append(stacks.isEmpty() ? " inventory empty" : " inventory " + String.join(", ", stacks.subList(0, Math.min(4, stacks.size())))
                        + (stacks.size() > 4 ? " +" + (stacks.size() - 4) + " stacks" : ""));
            }
        }
        return cut(line.toString());
    }
    private static String id(ItemStack stack) {
        ResourceLocation key = stack.getItem().getRegistryName();
        return (key == null ? "?" : shortId(key.toString())) + (stack.getHasSubtypes() && stack.getMetadata() != 0 ? "@" + stack.getMetadata() : "");
    }
    private static String shortId(String text) { return text.replace("minecraft:", ""); }
    private static String cut(String text) { return text.length() > 200 ? text.substring(0, 200) + "..." : text; }
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
