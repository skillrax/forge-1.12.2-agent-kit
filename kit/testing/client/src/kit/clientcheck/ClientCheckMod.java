package kit.clientcheck;

import com.google.gson.*;
import java.io.*;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import net.minecraft.block.Block;
import net.minecraft.block.BlockLiquid;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiMainMenu;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.gui.inventory.GuiInventory;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.IBakedModel;
import net.minecraft.client.renderer.block.model.ModelManager;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.I18n;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.inventory.ClickType;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.Slot;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumBlockRenderType;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.NonNullList;
import net.minecraft.util.ScreenShotHelper;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameType;
import net.minecraft.world.WorldServer;
import net.minecraft.world.WorldSettings;
import net.minecraft.world.WorldType;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fluids.IFluidBlock;
import net.minecraftforge.fml.common.registry.ForgeRegistries;
import net.minecraftforge.items.SlotItemHandler;
import org.lwjgl.input.Mouse;

/**
 * Check-only client mod added by `agent.ps1 check client` (never packaged). Opens a flat creative world, checks
 * that every block state and item of the project has a model and no missing texture, takes screenshots of the
 * blocks and items for a person to look at, then right-clicks every project block and item that handles a right-click
 * with the real player and compares each container it opens on the client with the server's, writes its report and
 * quits. Game tests cannot see client/server container desync: their FakePlayers have no connection and no client.
 */
@Mod(modid = "kitclientcheck", name = "Kit client check", version = "1", clientSideOnly = true)
public final class ClientCheckMod {
    private static final String OUT = System.getProperty("kit.client.out");
    private static final Set<String> NOT_PROJECT = new HashSet<>(Arrays.asList("minecraft", "forge", "fml", "mcp", "kitharness", "kitclientcheck"));
    private static final int PER_ROW = 8, BLOCK_ROWS = 4, ITEMS_PER_PAGE = 36;
    private final JsonArray problems = new JsonArray();
    private final JsonArray screenshots = new JsonArray();
    private final List<Block> blocks = new ArrayList<>();
    private final List<Block> placed = new ArrayList<>();
    private final List<ItemStack> items = new ArrayList<>();
    private boolean launched;
    /** Where the camera stays while a block page is shown (x, y, z, yaw, pitch); null on item pages. */
    private double[] camera;
    private int ticks, step;
    // Container sync: subjects are blocks (placed at USE_AT) and item stacks (held), right-clicked one at a time.
    private static final BlockPos USE_AT = new BlockPos(0, 4, -12);
    private static final int EMPTY_PLAYER_SLOT = 35;   // left empty by the fill, so a click has somewhere to put a stack
    private static final Item[] FILL = {Items.IRON_INGOT, Items.COAL, Items.REDSTONE, Items.STICK, Items.APPLE, Items.WHEAT, Items.DIAMOND,
        Items.PAPER, Items.BONE, Items.STRING, Items.GOLD_INGOT, Items.BUCKET, Item.getItemFromBlock(Blocks.COBBLESTONE), Item.getItemFromBlock(Blocks.LOG)};
    private final List<Object> subjects = new ArrayList<>();
    private final JsonArray tests = new JsonArray();
    private final List<int[]> clicks = new ArrayList<>();   // slot, ClickType ordinal
    private boolean syncing, viewWanted;
    private int subject = -1, syncStep, afterCompare, wait, subjectTicks, compared, retried, clicked, guis;
    private String phase;
    private volatile View serverView;

    @Mod.EventHandler public void preInit(FMLPreInitializationEvent event) {
        if (OUT != null) MinecraftForge.EVENT_BUS.register(this);
    }

    @SubscribeEvent public void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getMinecraft();
        try {
            if (!launched && mc.currentScreen instanceof GuiMainMenu) {
                launched = true;
                mc.gameSettings.pauseOnLostFocus = false;
                mc.gameSettings.renderDistanceChunks = 4;
                mc.launchIntegratedServer("kit-client-check", "Kit client check", new WorldSettings(0L, GameType.CREATIVE, false, false, WorldType.FLAT));
                return;
            }
            if (mc.world == null || mc.player == null || mc.getIntegratedServer() == null) return;
            ticks++;
            if (syncing) { syncTick(mc); return; }
            if (ticks == 20) {
                checkModels(mc);
                // Survival (creative opens the creative menu instead of the inventory), allowed to hover over the grid.
                mc.getIntegratedServer().addScheduledTask(() -> {
                    EntityPlayerMP player = mc.getIntegratedServer().getPlayerList().getPlayers().get(0);
                    player.setGameType(GameType.SURVIVAL);
                    player.capabilities.allowFlying = true;
                    player.capabilities.disableDamage = true;
                    player.capabilities.isFlying = true;
                    player.sendPlayerAbilities();
                });
            }
            // Blocks: pages of 32 on the grass, seen from above and in front; then items: pages of 36 in the inventory.
            int blockPages = (placed.size() + PER_ROW * BLOCK_ROWS - 1) / (PER_ROW * BLOCK_ROWS);
            int itemPages = (items.size() + ITEMS_PER_PAGE - 1) / ITEMS_PER_PAGE;
            if (step > 0) mc.getToastGui().clear();   // "new recipes" and tutorial pop-ups cover the inventory
            if (camera != null) {
                // Held in place every tick, as gravity and server corrections would move it.
                mc.player.setPosition(camera[0], camera[1], camera[2]);
                mc.player.motionX = mc.player.motionY = mc.player.motionZ = 0;
                mc.player.rotationYaw = mc.player.prevRotationYaw = (float) camera[3];
                mc.player.rotationPitch = mc.player.prevRotationPitch = (float) camera[4];
            }
            if (ticks < 30 || (ticks - 30) % 40 != 0) return;
            if (step > 0) {
                String name = step <= blockPages ? "blocks-" + step + ".png" : "items-" + (step - blockPages) + ".png";
                ScreenShotHelper.saveScreenshot(mc.gameDir, name, mc.displayWidth, mc.displayHeight, mc.getFramebuffer());
                screenshots.add(new File(new File(mc.gameDir, "screenshots"), name).getCanonicalPath());
            }
            if (step < blockPages) showBlocks(mc, step);
            else if (step < blockPages + itemPages) showItems(mc, step - blockPages);
            else { startSync(mc); return; }
            step++;
        } catch (Throwable error) {
            problem("ERROR", "client check", error.toString());
            finish(mc);
        }
    }

    /** The real client's view of every project block state and item: missing model, missing texture, raw name. */
    private void checkModels(Minecraft mc) {
        ModelManager models = mc.getBlockRendererDispatcher().getBlockModelShapes().getModelManager();
        IBakedModel missing = models.getMissingModel();
        for (Block block : ForgeRegistries.BLOCKS) {
            if (NOT_PROJECT.contains(block.getRegistryName().getNamespace())) continue;
            blocks.add(block);
            // Fluids spread over the grid: their model is checked, they are not placed.
            if (!(block instanceof IFluidBlock) && !(block instanceof BlockLiquid)) placed.add(block);
            for (IBlockState state : block.getBlockState().getValidStates()) {
                if (block.getRenderType(state) != EnumBlockRenderType.MODEL) continue;
                IBakedModel model = mc.getBlockRendererDispatcher().getModelForState(state);
                String subject = "block " + state;
                if (model == missing) { problem("ERROR", subject, "no model: the blockstate file lacks this variant or its model failed to load (log: 'Exception loading model')"); break; }
                if (usesMissingTexture(model, state)) { problem("ERROR", subject, "renders purple-black: a texture in its model is missing, or the blockstate lacks this variant (log lines above say which)"); break; }
            }
        }
        for (Item item : ForgeRegistries.ITEMS) {
            if (NOT_PROJECT.contains(item.getRegistryName().getNamespace())) continue;
            NonNullList<ItemStack> variants = NonNullList.create();
            if (item.getCreativeTab() != null) item.getSubItems(item.getCreativeTab(), variants);
            if (variants.isEmpty()) variants.add(new ItemStack(item));
            for (ItemStack stack : variants) {
                items.add(stack);
                String subject = "item " + item.getRegistryName() + (stack.getMetadata() != 0 ? "@" + stack.getMetadata() : "");
                IBakedModel model = mc.getRenderItem().getItemModelWithOverrides(stack, null, null);
                if (model == missing) problem("ERROR", subject, "no item model: models/item/" + item.getRegistryName().getPath() + ".json missing or failed to load");
                else if (!model.isBuiltInRenderer() && usesMissingTexture(model, null)) problem("ERROR", subject, "missing texture (purple-black) in its item model");
                if (!I18n.hasKey(stack.getTranslationKey() + ".name")) problem("WARN", subject, "no translation: shows as " + stack.getTranslationKey() + ".name");
            }
        }
    }

    private static boolean usesMissingTexture(IBakedModel model, IBlockState state) {
        String missing = Minecraft.getMinecraft().getTextureMapBlocks().getMissingSprite().getIconName();
        if (missing.equals(model.getParticleTexture().getIconName())) return true;
        List<EnumFacing> sides = new ArrayList<>(Arrays.asList(EnumFacing.values()));
        sides.add(null);
        for (EnumFacing side : sides) {
            for (BakedQuad quad : model.getQuads(state, side, 0L)) {
                TextureAtlasSprite sprite = quad.getSprite();
                if (sprite != null && missing.equals(sprite.getIconName())) return true;
            }
        }
        return false;
    }

    private void showBlocks(Minecraft mc, int page) {
        WorldServer world = mc.getIntegratedServer().getWorld(0);
        List<Block> shown = placed.subList(page * PER_ROW * BLOCK_ROWS, Math.min(placed.size(), (page + 1) * PER_ROW * BLOCK_ROWS));
        int rows = (shown.size() + PER_ROW - 1) / PER_ROW;
        int columns = Math.min(PER_ROW, shown.size());
        double away = Math.max(3, columns * 0.9);
        mc.gameSettings.hideGUI = true;
        camera = new double[]{columns - 0.5, 4 + away, rows - 0.5 + away, 180, 45};
        mc.displayGuiScreen(null);
        mc.getIntegratedServer().addScheduledTask(() -> {
            for (int x = -1; x <= PER_ROW * 2; x++) for (int z = -1; z <= BLOCK_ROWS * 2; z++) world.setBlockToAir(new BlockPos(x, 4, z));
            for (int i = 0; i < shown.size(); i++)
                world.setBlockState(new BlockPos((i % PER_ROW) * 2, 4, (i / PER_ROW) * 2), shown.get(i).getDefaultState(), 2);   // 2: no neighbor updates, nothing pops off
            EntityPlayerMP player = mc.getIntegratedServer().getPlayerList().getPlayers().get(0);
            // Back and up from the grid's middle, far enough that a full row fits the default field of view.
            int cols = Math.min(PER_ROW, shown.size());
            double distance = Math.max(4, cols * 1.3);
            player.connection.setPlayerLocation(cols - 0.5, 4 + distance, rows - 0.5 + distance, 180, 45);
        });
    }

    private void showItems(Minecraft mc, int page) {
        camera = null;
        mc.gameSettings.hideGUI = false;
        List<ItemStack> shown = items.subList(page * ITEMS_PER_PAGE, Math.min(items.size(), (page + 1) * ITEMS_PER_PAGE));
        mc.getIntegratedServer().addScheduledTask(() -> {
            EntityPlayerMP player = mc.getIntegratedServer().getPlayerList().getPlayers().get(0);
            player.inventory.clear();
            for (int i = 0; i < shown.size(); i++) player.inventory.setInventorySlotContents(i, shown.get(i).copy());
            player.inventoryContainer.detectAndSendChanges();
        });
        Mouse.setCursorPosition(0, 0);   // no tooltip over the slots
        mc.displayGuiScreen(new GuiInventory(mc.player));
    }

    /** Every project block and item that overrides the right-click hook: the ways a player opens a mod's GUI. */
    private void startSync(Minecraft mc) {
        syncing = true;
        camera = null;
        mc.gameSettings.hideGUI = false;
        mc.displayGuiScreen(null);
        Method useBlock = baseMethod(Block.class, 9), useItem = baseMethod(Item.class, 3);
        for (Block block : placed) if (overrides(block.getClass(), useBlock)) subjects.add(block);
        for (ItemStack stack : items) if (overrides(stack.getItem().getClass(), useItem)) subjects.add(stack);
    }

    /** Block.onBlockActivated and Item.onItemRightClick, found by signature so MCP and SRG names both work. */
    private static Method baseMethod(Class<?> type, int parameters) {
        for (Method method : type.getDeclaredMethods()) {
            Class<?>[] p = method.getParameterTypes();
            if (p.length != parameters || p[0] != net.minecraft.world.World.class) continue;
            if (parameters == 9 && p[1] == BlockPos.class && p[3] == EntityPlayer.class && p[4] == EnumHand.class && p[5] == EnumFacing.class) return method;
            if (parameters == 3 && p[1] == EntityPlayer.class && p[2] == EnumHand.class && method.getReturnType() != void.class) return method;
        }
        throw new IllegalStateException("no right-click method on " + type.getName());
    }

    private static boolean overrides(Class<?> type, Method base) {
        try { return type.getMethod(base.getName(), base.getParameterTypes()).getDeclaringClass() != base.getDeclaringClass(); }
        catch (NoSuchMethodException e) { return false; }
    }

    /**
     * One subject at a time: place or hold it, right-click it through the real client (packets to the integrated
     * server), and when both sides opened a container, compare them after opening, after the server put items in
     * its slots, and after real slot clicks. A difference is compared once more 10 ticks later before it counts.
     */
    private void syncTick(Minecraft mc) {
        subjectTicks++;
        if (wait > 0) { wait--; return; }
        try {
            switch (syncStep) {
                case 0:   // next subject
                    if (++subject >= subjects.size()) { finish(mc); return; }
                    subjectTicks = compared = retried = clicked = 0;
                    clicks.clear();
                    prepare(mc, subjects.get(subject));
                    syncStep = 1; wait = 5;
                    return;
                case 1:   // right-click
                    Object used = subjects.get(subject);
                    if (used instanceof Block) mc.playerController.processRightClickBlock(mc.player, mc.world, USE_AT, EnumFacing.UP, new Vec3d(USE_AT).add(0.5, 1, 0.5), EnumHand.MAIN_HAND);
                    else mc.playerController.processRightClick(mc.player, mc.world, EnumHand.MAIN_HAND);
                    syncStep = 2; wait = 10;
                    return;
                case 2:
                    requestServerView(mc);
                    syncStep = 3;
                    return;
                case 3:   // did it open a container, and on which side?
                    if (!serverView.fresh) { if (subjectTicks > 400) fail("the server did not answer within 20 s"); return; }
                    boolean clientOpen = mc.player.openContainer != mc.player.inventoryContainer;
                    String screen = mc.currentScreen == null ? "no screen" : mc.currentScreen.getClass().getName();
                    if (!serverView.open && !clientOpen) { close(mc); return; }
                    phase = "open";
                    if (!clientOpen) { fail("the server opened " + serverView.container + " (window " + serverView.window + ") but the client shows " + screen + ": getClientGuiElement must return a GuiContainer for this id and position"); return; }
                    if (!serverView.open) { fail("the client opened " + screen + " but the server has no container open: open it on the server (player.openGui when !world.isRemote); a client-only container cannot move items"); return; }
                    if (mc.isGamePaused()) { fail(screen + " pauses the game (doesGuiPauseGame returns true): in single player the server then ignores slot clicks until it closes; a GuiContainer must return false"); return; }
                    guis++;
                    compare("after opening", 4);
                    return;
                case 4:   // the server fills the player inventory and the container's own slots
                    mc.getIntegratedServer().addScheduledTask(() -> fill(mc.getIntegratedServer().getPlayerList().getPlayers().get(0)));
                    wait = 6;
                    compare("after the server put items in its slots", 5);
                    return;
                case 5:   // screenshot, then plan clicks from what the client shows
                    Mouse.setCursorPosition(0, 0);
                    String name = "gui-" + guis + ".png";
                    ScreenShotHelper.saveScreenshot(mc.gameDir, name, mc.displayWidth, mc.displayHeight, mc.getFramebuffer());
                    screenshots.add(new File(new File(mc.gameDir, "screenshots"), name).getCanonicalPath());
                    planClicks(mc.player.openContainer);
                    syncStep = 6;
                    return;
                case 6:   // one real click per step, compared after each
                    if (clicked >= clicks.size()) { pass(); close(mc); return; }
                    int[] click = clicks.get(clicked++);
                    ClickType type = ClickType.values()[click[1]];
                    mc.playerController.windowClick(mc.player.openContainer.windowId, click[0], 0, type, mc.player);
                    wait = 6;
                    compare("after click " + clicked + " (" + type + " on slot " + click[0] + ")", 6);
                    return;
                case 7:   // compare once the wait is over (afterCompare is the next step)
                    if (viewWanted) { viewWanted = false; requestServerView(mc); return; }
                    if (!serverView.fresh) { if (subjectTicks > 400) fail("the server did not answer within 20 s"); return; }
                    View client = view(mc.player);
                    List<String> found = differences(client, serverView);
                    if (!found.isEmpty() && retried++ == 0) { wait = 10; viewWanted = true; return; }
                    if (!found.isEmpty()) {
                        int shown = Math.min(2, found.size());
                        fail(String.join("; ", found.subList(0, shown)) + (found.size() > shown ? "; " + (found.size() - shown) + " more" : "")
                            + ". The client builds its own container from client-side data: build the same slots in the same order on both sides, and send the server state they depend on before the screen opens (or rebuild both together)");
                        return;
                    }
                    positions(client, serverView);
                    compared++;
                    retried = 0;
                    syncStep = afterCompare;
                    return;
                default:   // 8: the cleanup ran
                    syncStep = 0;
            }
        } catch (Throwable error) {
            if (phase == null) phase = "open";
            fail("error while testing: " + error);
        }
    }

    private void prepare(Minecraft mc, Object used) {
        mc.player.inventory.currentItem = 0;
        mc.getIntegratedServer().addScheduledTask(() -> {
            EntityPlayerMP player = mc.getIntegratedServer().getPlayerList().getPlayers().get(0);
            WorldServer world = mc.getIntegratedServer().getWorld(0);
            clearArea(world, player);
            player.connection.setPlayerLocation(0.5, 5, -14.5, 0, 30);
            player.inventory.currentItem = 0;
            if (used instanceof Block) {
                IBlockState state = ((Block) used).getDefaultState();
                world.setBlockState(USE_AT, state, 3);
                ((Block) used).onBlockPlacedBy(world, USE_AT, state, player, new ItemStack((Block) used));
            } else player.inventory.setInventorySlotContents(0, ((ItemStack) used).copy());
            player.inventoryContainer.detectAndSendChanges();
        });
    }

    private static void clearArea(WorldServer world, EntityPlayerMP player) {
        if (player.openContainer != player.inventoryContainer) player.closeScreen();
        player.inventory.setItemStack(ItemStack.EMPTY);
        player.inventory.clear();
        for (BlockPos pos : BlockPos.getAllInBox(USE_AT.add(-1, 0, -1), USE_AT.add(1, 2, 1))) {
            world.removeTileEntity(pos);   // no contents dropped
            world.setBlockState(pos, Blocks.AIR.getDefaultState(), 2);
        }
        for (EntityItem item : world.getEntitiesWithinAABB(EntityItem.class, new AxisAlignedBB(USE_AT).grow(8))) item.setDead();
        player.inventoryContainer.detectAndSendChanges();
    }

    /** Distinct stacks in the player inventory (except the held slot and EMPTY_PLAYER_SLOT), one valid item per empty container slot. */
    private void fill(EntityPlayerMP player) {
        for (int i = 0; i < 36; i++) {
            if (i == EMPTY_PLAYER_SLOT || !player.inventory.getStackInSlot(i).isEmpty()) continue;
            Item item = FILL[i % FILL.length];
            player.inventory.setInventorySlotContents(i, new ItemStack(item, Math.min(1 + i % 8, item.getItemStackLimit())));
        }
        List<ItemStack> candidates = new ArrayList<>(items);
        for (Item item : FILL) candidates.add(new ItemStack(item));
        for (Slot slot : player.openContainer.inventorySlots) {
            if (slot.inventory instanceof InventoryPlayer || slot.getHasStack()) continue;
            for (ItemStack candidate : candidates) {
                ItemStack one = candidate.copy();
                one.setCount(1);
                if (slot.isItemValid(one)) { slot.putStack(one); break; }
            }
        }
        player.openContainer.detectAndSendChanges();
    }

    /** Take a stack out of the container into the empty player slot, then shift-click it back; or shift-click a player stack in. */
    private void planClicks(Container container) {
        int from = -1, empty = -1, full = -1;
        for (Slot slot : container.inventorySlots) {
            boolean player = slot.inventory instanceof InventoryPlayer;
            if (!player && from < 0 && slot.getHasStack()) from = slot.slotNumber;
            if (player && slot.getSlotIndex() == EMPTY_PLAYER_SLOT && !slot.getHasStack()) empty = slot.slotNumber;
            if (player && full < 0 && slot.getSlotIndex() >= 9 && slot.getHasStack()) full = slot.slotNumber;
        }
        if (from >= 0 && empty >= 0) {
            clicks.add(new int[]{from, ClickType.PICKUP.ordinal()});
            clicks.add(new int[]{empty, ClickType.PICKUP.ordinal()});
            clicks.add(new int[]{empty, ClickType.QUICK_MOVE.ordinal()});
        } else if (full >= 0) clicks.add(new int[]{full, ClickType.QUICK_MOVE.ordinal()});
    }

    private void compare(String when, int next) {
        phase = when;
        afterCompare = next;
        retried = 0;
        syncStep = 7;
        viewWanted = true;
    }

    /** The server's view, taken on the server thread; until it arrives serverView is a placeholder that is not fresh. */
    private void requestServerView(Minecraft mc) {
        serverView = new View();
        mc.getIntegratedServer().addScheduledTask(() -> {
            View view = view(mc.getIntegratedServer().getPlayerList().getPlayers().get(0));
            view.fresh = true;
            serverView = view;
        });
    }

    /** What a side's open container holds: which inventory slot each slot index stands for, its stack, the cursor. */
    private static final class View {
        boolean fresh, open;
        String container;
        int window;
        final List<String> slots = new ArrayList<>(), names = new ArrayList<>(), stacks = new ArrayList<>(), positions = new ArrayList<>();
        String cursor;
    }

    private static View view(EntityPlayer player) {
        Container container = player.openContainer;
        View view = new View();
        view.open = container != player.inventoryContainer;
        view.container = container.getClass().getName();
        view.window = container.windowId;
        for (Slot slot : container.inventorySlots) {
            boolean own = slot.inventory instanceof InventoryPlayer;
            Object of = slot instanceof SlotItemHandler ? ((SlotItemHandler) slot).getItemHandler() : slot.inventory;
            view.slots.add((own ? "player inventory #" : "container slot #") + slot.getSlotIndex());
            view.names.add(of.getClass().getName());
            view.stacks.add(describe(slot.getStack()));
            view.positions.add(slot.xPos + "," + slot.yPos);
        }
        view.cursor = describe(player.inventory.getItemStack());
        return view;
    }

    private static String describe(ItemStack stack) {
        if (stack.isEmpty()) return "nothing";
        return stack.getCount() + " " + stack.getItem().getRegistryName() + (stack.getHasSubtypes() || stack.getMetadata() != 0 ? "@" + stack.getMetadata() : "");
    }

    private static List<String> differences(View client, View server) {
        List<String> found = new ArrayList<>();
        if (!client.container.equals(server.container) || client.window != server.window)
            found.add("the client has " + client.container + " (window " + client.window + "), the server " + server.container + " (window " + server.window + ")");
        if (client.slots.size() != server.slots.size()) found.add("the client has " + client.slots.size() + " slots, the server " + server.slots.size());
        for (int i = 0; i < Math.min(client.slots.size(), server.slots.size()); i++) {
            if (!client.slots.get(i).equals(server.slots.get(i))) {
                boolean same = client.names.get(i).equals(server.names.get(i));
                found.add("slot " + i + " is " + client.slots.get(i) + (same ? "" : " (" + client.names.get(i) + ")") + " on the client but " + server.slots.get(i) + (same ? "" : " (" + server.names.get(i) + ")") + " on the server");
            }
            else if (!client.stacks.get(i).equals(server.stacks.get(i)))
                found.add("slot " + i + " shows " + client.stacks.get(i) + " on the client but holds " + server.stacks.get(i) + " on the server");
        }
        if (!client.cursor.equals(server.cursor)) found.add("the cursor holds " + client.cursor + " on the client, " + server.cursor + " on the server");
        return found;
    }

    /** Slot positions only matter on the client, so a difference is a WARN: fine when the screen moves slots (tabs, scrolling). */
    private void positions(View client, View server) {
        for (int i = 0; i < Math.min(client.positions.size(), server.positions.size()); i++) {
            if (client.positions.get(i).equals(server.positions.get(i))) continue;
            problem("WARN", subjectName(), "slot " + i + " is at " + client.positions.get(i) + " on the client but " + server.positions.get(i) + " on the server (" + phase + "): fine if the screen moves slots itself, otherwise the two sides built different layouts");
            return;
        }
    }

    private String subjectName() {
        Object used = subjects.get(subject);
        if (used instanceof Block) return "gui block " + ((Block) used).getRegistryName();
        ItemStack stack = (ItemStack) used;
        return "gui item " + stack.getItem().getRegistryName() + (stack.getMetadata() != 0 ? "@" + stack.getMetadata() : "");
    }

    private void fail(String message) { result("FAIL", message); close(Minecraft.getMinecraft()); }

    private void pass() { result("PASS", null); }

    private void result(String status, String message) {
        JsonObject test = new JsonObject();
        test.addProperty("id", subjectName());
        test.addProperty("status", status);
        if (message != null) { test.addProperty("phase", phase); test.addProperty("message", message); }
        test.add("stack", new JsonArray());
        test.add("area", new JsonArray());
        test.addProperty("assertions", compared);
        test.addProperty("ticks", subjectTicks);
        tests.add(test);
    }

    /** Closes whatever opened, removes the subject and everything it dropped, then moves to the next subject. */
    private void close(Minecraft mc) {
        if (mc.player.openContainer != mc.player.inventoryContainer) mc.player.closeScreen();
        else if (mc.currentScreen != null) mc.displayGuiScreen(null);
        mc.getIntegratedServer().addScheduledTask(() -> clearArea(mc.getIntegratedServer().getWorld(0), mc.getIntegratedServer().getPlayerList().getPlayers().get(0)));
        phase = null;
        syncStep = 8; wait = 4;
    }

    private void problem(String level, String subject, String message) {
        JsonObject entry = new JsonObject();
        entry.addProperty("level", level);
        entry.addProperty("subject", subject);
        entry.addProperty("message", message);
        problems.add(entry);
    }

    private void finish(Minecraft mc) {
        JsonObject report = new JsonObject();
        report.add("lint", problems);
        report.add("tests", tests);
        report.add("screenshots", screenshots);
        JsonObject counted = new JsonObject();
        counted.addProperty("blocks", blocks.size());
        counted.addProperty("items", items.size());
        counted.addProperty("guis", guis);
        report.add("checked", counted);
        try {
            Path destination = Paths.get(OUT);
            Files.createDirectories(destination.getParent());
            try (Writer writer = Files.newBufferedWriter(destination, StandardCharsets.UTF_8)) {
                new GsonBuilder().setPrettyPrinting().create().toJson(report, writer);
            }
        } catch (IOException error) {
            System.out.println("KIT_CLIENT_ERROR could not write " + OUT + ": " + error);
        }
        System.out.println("KIT_CLIENT_DONE");
        MinecraftForge.EVENT_BUS.unregister(this);
        mc.shutdown();
    }
}
