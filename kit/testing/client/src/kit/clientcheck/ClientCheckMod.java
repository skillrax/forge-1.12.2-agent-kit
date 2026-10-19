package kit.clientcheck;

import com.google.gson.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import net.minecraft.block.Block;
import net.minecraft.block.BlockLiquid;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiMainMenu;
import net.minecraft.client.gui.inventory.GuiInventory;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.IBakedModel;
import net.minecraft.client.renderer.block.model.ModelManager;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.I18n;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumBlockRenderType;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.NonNullList;
import net.minecraft.util.ScreenShotHelper;
import net.minecraft.util.math.BlockPos;
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
import org.lwjgl.input.Mouse;

/**
 * Check-only client mod added by `agent.ps1 check client` (never packaged). Opens a flat creative world, checks
 * that every block state and item of the project has a model and no missing texture, takes screenshots of the
 * blocks and items for a person to look at, writes its report and quits.
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
            else { finish(mc); return; }
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
        report.add("tests", new JsonArray());
        report.add("screenshots", screenshots);
        JsonObject counted = new JsonObject();
        counted.addProperty("blocks", blocks.size());
        counted.addProperty("items", items.size());
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
