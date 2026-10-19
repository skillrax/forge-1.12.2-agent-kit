package acceptance;

import java.util.*;
import kit.gametest.GameTest;
import kit.gametest.Scenario;
import kit.gametest.TestContext;
import net.minecraft.command.ICommand;
import net.minecraft.command.ICommandSender;
import net.minecraft.init.Blocks;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.world.World;

/** S0 "Where am I command", from the request only. */
public final class S0Acceptance {
    static final String COMMAND = "whereami";

    static void run(TestContext t, ICommandSender sender, String args) {
        t.server().getCommandManager().executeCommand(sender, "/" + COMMAND + (args.isEmpty() ? "" : " " + args));
    }
    static boolean hasCoordinates(String text, BlockPos pos) {
        List<Integer> n = Accept.numbers(text);
        return n.contains(pos.getX()) && n.contains(pos.getY()) && n.contains(pos.getZ());
    }
    static boolean hasWord(String text, String word) {
        return text.toLowerCase(Locale.ROOT).matches("(?s).*(^|[^a-z])" + word + "([^a-z]|$).*");
    }
    /** A player standing at a block, slightly off its corner so that floor and round agree. */
    static Accept.Listener standAt(World world, String name, BlockPos feet) {
        Accept.Listener p = new Accept.Listener((net.minecraft.world.WorldServer) world, name);
        p.setLocationAndAngles(feet.getX() + 0.2, feet.getY(), feet.getZ() + 0.2, 0, 0);
        return p;
    }

    @GameTest(value = "acceptance-s0-info")
    public static class Info implements Scenario {
        @Override public void plan(TestContext t) {
            BlockPos feet = t.origin().add(4, 0, 4);
            Accept.Listener[] p = new Accept.Listener[1];
            t.then("sealed stone room, glowstone two blocks from the player", () -> {
                for (int x = -1; x <= 3; x++) for (int y = 0; y <= 2; y++) for (int z = -1; z <= 1; z++) t.place(feet.add(x, y, z), Blocks.STONE.getDefaultState());
                for (int x = 0; x <= 1; x++) for (int y = 0; y <= 1; y++) t.place(feet.add(x, y, 0), Blocks.AIR.getDefaultState());
                t.place(feet.add(2, 0, 0), Blocks.GLOWSTONE.getDefaultState());
            });
            t.waitTicks(5);
            t.then("run /" + COMMAND, () -> { p[0] = standAt(t.world(), "Explorer", feet); run(t, p[0], ""); });
            t.check("coordinates-whole-blocks", () -> hasCoordinates(p[0].all(), feet), () -> "expected " + feet.getX() + " " + feet.getY() + " " + feet.getZ() + " in: " + p[0].all());
            t.check("dimension-overworld", () -> hasWord(p[0].all(), "overworld"), () -> "no 'Overworld' in: " + p[0].all());
            t.check("biome", () -> {
                String biome = t.world().getBiome(feet).getRegistryName().getPath();
                String text = p[0].all().toLowerCase(Locale.ROOT);
                return text.contains(biome) || text.contains(biome.replace('_', ' '));
            }, () -> "no biome '" + t.world().getBiome(feet).getRegistryName() + "' in: " + p[0].all());
            t.check("brightness", () -> {
                List<Integer> n = Accept.numbers(p[0].all());
                return n.contains(t.world().getLightFromNeighbors(feet)) || n.contains(t.world().getLightFromNeighbors(feet.up()));
            }, () -> "expected light " + t.world().getLightFromNeighbors(feet) + " (feet) or " + t.world().getLightFromNeighbors(feet.up()) + " (eyes) in: " + p[0].all());
        }
    }

    @GameTest(value = "acceptance-s0-dimensions")
    public static class Dimensions implements Scenario {
        @Override public void plan(TestContext t) {
            Accept.Listener[] p = new Accept.Listener[2];
            t.then("run in the Nether and the End", () -> {
                p[0] = standAt(t.server().getWorld(-1), "NetherWalker", new BlockPos(8, 40, 8));
                run(t, p[0], "");
                p[1] = standAt(t.server().getWorld(1), "EndWalker", new BlockPos(8, 60, 8));
                run(t, p[1], "");
            });
            t.check("names-the-nether", () -> hasWord(p[0].all(), "nether") || hasWord(p[0].all(), "the_nether"), () -> "no 'Nether' in: " + p[0].all());
            t.check("names-the-end", () -> hasWord(p[1].all(), "end"), () -> "no 'End' in: " + p[1].all());
        }
    }

    @GameTest(value = "acceptance-s0-not-just-ops")
    public static class NotJustOps implements Scenario {
        @Override public void plan(TestContext t) {
            Accept.Listener[] p = new Accept.Listener[1];
            t.check("command-registered", () -> t.server().getCommandManager().getCommands().containsKey(COMMAND), () -> "no /" + COMMAND + " command registered");
            t.check("non-op-may-use-it", () -> {
                ICommand command = t.server().getCommandManager().getCommands().get(COMMAND);
                p[0] = standAt(t.world(), "Visitor", t.origin().add(2, 0, 2));   // like every FakePlayer: not an operator
                return command.checkPermission(t.server(), p[0]);
            }, () -> "checkPermission is false for a player who is not an operator (getRequiredPermissionLevel 0 alone is not enough in 1.12.2)");
            t.then("non-op runs it", () -> run(t, p[0], ""));
            t.check("non-op-gets-the-info", () -> !p[0].all().contains("commands.generic.permission") && hasCoordinates(p[0].all(), t.origin().add(2, 0, 2)), () -> "got: " + p[0].all());
        }
    }

    @GameTest(value = "acceptance-s0-share")
    public static class Share implements Scenario {
        @Override public void plan(TestContext t) {
            BlockPos feet = t.origin().add(6, 0, 3);
            Accept.Listener[] p = new Accept.Listener[2];
            t.then("two players online, one runs share", () -> {
                p[0] = standAt(t.world(), "Explorer", feet);
                p[1] = standAt(t.world(), "Friend", t.origin().add(12, 0, 12));
                List<net.minecraft.entity.player.EntityPlayerMP> online = t.server().getPlayerList().getPlayers();
                online.add(p[0]); online.add(p[1]);
                try { run(t, p[0], "share"); }
                finally { online.remove(p[0]); online.remove(p[1]); }
            });
            t.check("friend-sees-name-and-coordinates", () -> p[1].all().contains("Explorer") && hasCoordinates(p[1].all(), feet),
                    () -> "the other player got: " + (p[1].received.isEmpty() ? "nothing" : p[1].all()));
        }
    }

    @GameTest(value = "acceptance-s0-wrong-usage")
    public static class WrongUsage implements Scenario {
        @Override public void plan(TestContext t) {
            Accept.Listener[] p = new Accept.Listener[1];
            t.then("run /" + COMMAND + " foo", () -> { p[0] = standAt(t.world(), "Typo", t.origin().add(3, 0, 3)); run(t, p[0], "foo"); });
            t.check("tells-how-to-use-it", () -> {
                String text = p[0].all().toLowerCase(Locale.ROOT);
                return text.contains("usage") || text.contains("/" + COMMAND);
            }, () -> "no usage text in: " + (p[0].received.isEmpty() ? "nothing" : p[0].all()));
        }
    }

    @GameTest(value = "acceptance-s0-console")
    public static class Console implements Scenario {
        @Override public void plan(TestContext t) {
            List<String> received = new ArrayList<>();
            ICommandSender console = new ICommandSender() {
                @Override public String getName() { return "Server"; }
                @Override public boolean canUseCommand(int level, String command) { return true; }
                @Override public World getEntityWorld() { return t.world(); }
                @Override public MinecraftServer getServer() { return t.server(); }
                @Override public void sendMessage(ITextComponent component) { received.add(Accept.text(component)); }
            };
            t.then("run from the console", () -> run(t, console, ""));
            t.check("says-players-only-without-crashing", () -> !received.isEmpty() && received.stream().noneMatch(m -> m.contains("commands.generic.exception")),
                    () -> received.isEmpty() ? "no message to the console" : "the command threw (commands.generic.exception): " + String.join(" | ", received));
        }
    }
}
