package kit.gametest;

import com.google.gson.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.DimensionManager;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.discovery.ASMDataTable;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.event.FMLServerStartedEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

/**
 * Check-only mod added to the development server by agent.ps1 (never packaged).
 * Lints registered content against its assets, then runs requested game tests and stops the server.
 */
@Mod(modid = "kitharness", name = "Kit check harness", version = "1", acceptableRemoteVersions = "*")
public final class HarnessMod {
    private static final String OUT = System.getProperty("kit.check.out");
    private static final String FILTER = System.getProperty("kit.gametests", "");
    /** Phase 2 runs afterRestart() of the tests listed in the phase-1 report, on the same saved world. */
    private static final String RESTART_FROM = System.getProperty("kit.restart.from", "");
    private final JsonArray restart = new JsonArray();
    private String[] currentEntry;
    private final List<String[]> pending = new ArrayList<>();
    private final JsonArray results = new JsonArray();
    private JsonObject report;
    private MinecraftServer server;
    private TestContext current;
    private String currentId;
    private static final int AREAS = 36;
    private int started, restartAreas, restartTotal;
    private boolean done;

    @Mod.EventHandler public void preInit(FMLPreInitializationEvent event) {
        if (OUT == null) return;
        if (!RESTART_FROM.isEmpty()) {
            try (Reader reader = Files.newBufferedReader(Paths.get(RESTART_FROM), StandardCharsets.UTF_8)) {
                JsonObject first = new JsonParser().parse(reader).getAsJsonObject();
                for (JsonElement element : first.getAsJsonArray("restart")) {
                    JsonObject entry = element.getAsJsonObject();
                    JsonArray origin = entry.getAsJsonArray("origin");
                    pending.add(new String[]{entry.get("id").getAsString(), entry.get("class").getAsString(), entry.get("maxTicks").getAsString(),
                            origin.get(0).getAsString(), origin.get(1).getAsString(), origin.get(2).getAsString()});
                }
            } catch (IOException | RuntimeException error) {
                throw new IllegalStateException("Cannot read the phase-1 report " + RESTART_FROM, error);
            }
        } else if (!FILTER.isEmpty()) {
            Set<String> wanted = new HashSet<>(Arrays.asList(FILTER.split(",")));
            for (ASMDataTable.ASMData data : event.getAsmData().getAll(GameTest.class.getName())) {
                Object id = data.getAnnotationInfo().get("value");
                Object max = data.getAnnotationInfo().get("maxTicks");
                if (id != null && (wanted.contains("*") || wanted.contains(id.toString())))
                    pending.add(new String[]{id.toString(), data.getClassName(), max == null ? "1200" : max.toString()});
            }
            pending.sort(Comparator.comparing(entry -> entry[0]));
        }
        MinecraftForge.EVENT_BUS.register(this);
    }

    @Mod.EventHandler public void started(FMLServerStartedEvent event) {
        if (OUT == null) return;
        server = FMLCommonHandler.instance().getMinecraftServerInstance();
        report = new JsonObject();
        if (RESTART_FROM.isEmpty()) {
            try { report = new AssetLint(server).run(); }
            catch (Throwable error) { report.addProperty("lintError", error.toString()); }
        }
        report.add("tests", results);
        report.add("restart", restart);
        if (RESTART_FROM.isEmpty()) for (String[] entry : pending) if (declaresRestart(entry[1])) restartTotal++;
        if (restartTotal > AREAS) report.addProperty("testError", restartTotal + " restart tests but only " + AREAS
                + " test areas: they would overwrite each other before the save. Merge restart checks into fewer tests");
        if (RESTART_FROM.isEmpty() && !FILTER.isEmpty() && pending.isEmpty()) report.addProperty("testError", "*".equals(FILTER)
                ? "No game tests yet: write one per requested behavior with agent.ps1 new test <id> (agent.ps1 check server only compiles and lints)"
                : "No @GameTest scenarios matched '" + FILTER + "' in src/gametest/java");
        server.getWorld(0).getGameRules().setOrCreateGameRule("doMobSpawning", "false");
        server.getWorld(0).getGameRules().setOrCreateGameRule("doDaylightCycle", "false");
        if (pending.isEmpty()) finish();
    }

    @SubscribeEvent public void tick(TickEvent.ServerTickEvent event) {
        if (done || report == null) return;
        if (event.phase == TickEvent.Phase.START) {
            // Vanilla stops ticking entities/tile entities after 300 ticks without players; tests need them.
            for (WorldServer world : DimensionManager.getWorlds()) world.resetUpdateEntityTick();
            return;
        }
        try {
            if (current == null) {
                if (pending.isEmpty()) { finish(); return; }
                String[] next = pending.remove(0);
                currentId = next[0];
                currentEntry = next;
                Scenario scenario = (Scenario) Class.forName(next[1]).getConstructor().newInstance();
                if (next.length > 3) {
                    // Phase 2: the area as the first server saved it.
                    BlockPos origin = new BlockPos(Integer.parseInt(next[3]), Integer.parseInt(next[4]), Integer.parseInt(next[5]));
                    current = new TestContext(server, origin, Integer.parseInt(next[2]));
                    scenario.afterRestart(current);
                } else {
                    BlockPos spawn = server.getWorld(0).getSpawnPoint();
                    // Separate 16x16 areas, all inside the spawn chunks that stay loaded.
                    int slot = areaSlot(next[1]);
                    BlockPos origin = new BlockPos(spawn.getX() + 8 + (slot % 6) * 20, 4, spawn.getZ() - 52 + (slot / 6) * 20);
                    started++;
                    current = new TestContext(server, origin, Integer.parseInt(next[2]));
                    current.prepareArea();
                    current.checksAfterRestart = declaresRestart(next[1]);
                    scenario.plan(current);
                }
            }
            if (current.tick()) {
                record(true, current.phase(), "ok", null);
                if (currentEntry.length == 3 && declaresRestart(currentEntry[1])) {
                    JsonObject entry = new JsonObject();
                    entry.addProperty("id", currentEntry[0]);
                    entry.addProperty("class", currentEntry[1]);
                    entry.addProperty("maxTicks", currentEntry[2]);
                    JsonArray origin = new JsonArray();
                    origin.add(current.origin().getX()); origin.add(current.origin().getY()); origin.add(current.origin().getZ());
                    entry.add("origin", origin);
                    restart.add(entry);
                }
                current.cleanup();
                current = null;
            }
        } catch (Throwable error) {
            Throwable cause = error;
            for (int depth = 0; depth < 8 && cause.getCause() != null; depth++) cause = cause.getCause();
            // Assertions carry their own text; other exceptions need their type (an NPE has no message at all).
            String message = cause instanceof AssertionError && cause.getMessage() != null ? cause.getMessage()
                    : cause.getClass().getName() + (cause.getMessage() == null ? "" : ": " + cause.getMessage());
            record(false, current == null ? "setup" : current.phase(), message, cause);
            if (current != null) { try { current.cleanup(); } catch (Throwable ignored) { } }
            current = null;
        }
    }

    // Past 36 tests areas are reused (each test clears its area first), but a restart test keeps its own
    // area until the save: a later test clearing it would look like contents lost across the restart.
    private int areaSlot(String className) {
        if (declaresRestart(className)) return restartAreas++ % AREAS;
        int shared = Math.max(1, AREAS - restartTotal);
        return Math.min(restartTotal, AREAS - 1) + (started - restartAreas) % shared;
    }

    private static boolean declaresRestart(String className) {
        try { return Class.forName(className).getMethod("afterRestart", TestContext.class).getDeclaringClass() != Scenario.class; }
        catch (ReflectiveOperationException error) { return false; }
    }

    private void record(boolean passed, String phase, String message, Throwable error) {
        JsonObject result = new JsonObject();
        result.addProperty("id", currentId);
        result.addProperty("status", passed ? "PASS" : "FAIL");
        result.addProperty("phase", RESTART_FROM.isEmpty() ? phase : "after restart: " + phase);
        result.addProperty("assertions", current == null ? 0 : current.assertions());
        result.addProperty("ticks", current == null ? 0 : current.ticks());
        result.addProperty("message", message.length() > 600 ? message.substring(0, 600) : message);
        if (error != null) {
            result.addProperty("type", error.getClass().getName());
            JsonArray stack = new JsonArray();
            for (StackTraceElement frame : error.getStackTrace()) {
                String text = frame.toString();
                // Project frames are what matter; skip harness, JDK and game internals.
                if (text.startsWith("kit.gametest.") || text.startsWith("java.") || text.startsWith("sun.") || text.startsWith("net.minecraft")) continue;
                stack.add(new JsonPrimitive(text));
                if (stack.size() >= 4) break;
            }
            result.add("stack", stack);
        }
        if (!passed && current != null) {
            JsonArray area = new JsonArray();
            for (String line : current.snapshot()) area.add(new JsonPrimitive(line));
            result.add("area", area);
        }
        results.add(result);
        System.out.println("KIT_TEST " + (passed ? "PASS " : "FAIL ") + currentId + (passed ? "" : " at " + phase + ": " + message));
    }

    private void finish() {
        if (done) return;
        done = true;
        try {
            Path destination = Paths.get(OUT);
            Files.createDirectories(destination.getParent());
            try (Writer writer = Files.newBufferedWriter(destination, StandardCharsets.UTF_8)) {
                new GsonBuilder().setPrettyPrinting().create().toJson(report, writer);
            }
        } catch (IOException error) {
            System.out.println("KIT_HARNESS_ERROR could not write " + OUT + ": " + error);
        }
        System.out.println("KIT_HARNESS_DONE");
        server.initiateShutdown();
    }
}
