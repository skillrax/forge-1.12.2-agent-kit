package acceptance;

import java.util.*;
import kit.gametest.GameTest;
import kit.gametest.Scenario;
import kit.gametest.TestContext;
import net.minecraft.block.BlockHopper;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;

/**
 * S7 "Blast kiln multiblock", from the request only. A kiln is the 3x3x3 cube from base (its lowest corner),
 * built one block above the floor so a hopper fits under it; the controller sits in the middle of the wall on
 * side `wall`, placed by a player standing outside that wall facing the kiln (as a user builds it).
 */
public final class S7Acceptance {
    static final String BRICK = "blastkiln:kiln_brick";
    static final String CONTROLLER = "blastkiln:kiln_controller";

    static final class Kiln {
        final TestContext t; final BlockPos base, center, controller; final EnumFacing wall; final Accept.Listener p;
        Kiln(TestContext t, BlockPos base, EnumFacing wall) {
            this.t = t; this.base = base; this.wall = wall;
            center = base.add(1, 1, 1); controller = center.offset(wall);
            p = new Accept.Listener(t.world(), "Builder" + wall.getName());
            BlockPos stand = controller.offset(wall, 2);
            p.setLocationAndAngles(stand.getX() + 0.5, stand.getY(), stand.getZ() + 0.5, wall.getOpposite().getHorizontalAngle(), 0);
        }
        /** All 25 bricks except `skip`, then the controller placed from its item. */
        Kiln build(BlockPos skip) {
            for (int x = 0; x < 3; x++) for (int y = 0; y < 3; y++) for (int z = 0; z < 3; z++) {
                BlockPos pos = base.add(x, y, z);
                if (!pos.equals(center) && !pos.equals(controller) && !pos.equals(skip)) t.place(pos, BRICK);
            }
            p.setHeldItem(EnumHand.MAIN_HAND, t.stack(CONTROLLER, 1));
            t.useOnBlock(p, controller.down(), EnumFacing.UP, EnumHand.MAIN_HAND);
            t.require(t.state(controller).getBlock() == t.block(CONTROLLER), "placing the controller from its item failed: " + t.state(controller));
            return this;
        }
        /** Right-click the controller with an empty hand; returns what it said. */
        String rightClick() {
            int before = p.received.size();
            p.setHeldItem(EnumHand.MAIN_HAND, ItemStack.EMPTY);
            t.useOnBlock(p, controller, wall, EnumHand.MAIN_HAND);
            return String.join(" ", p.received.subList(before, p.received.size()));
        }
        BlockPos top(int x, int z) { return base.add(x, 2, z); }
        BlockPos middleRing() { return center.offset(wall.rotateY()); }
        BlockPos bottom(int x, int z) { return base.add(x, 0, z); }
        /** Hopper above a top brick; returns its position. */
        BlockPos oreHopper(int x, int z) { BlockPos h = top(x, z).up(); t.place(h, Blocks.HOPPER.getDefaultState().withProperty(BlockHopper.FACING, EnumFacing.DOWN)); return h; }
        /** Hopper outside the middle-ring brick beside the controller, pointing into it. */
        BlockPos fuelHopper() {
            EnumFacing side = wall.rotateY();
            BlockPos h = middleRing().offset(side);
            t.place(h, Blocks.HOPPER.getDefaultState().withProperty(BlockHopper.FACING, side.getOpposite()));
            return h;
        }
        /** Hopper under the bottom centre brick, pulling from it into a chest beside it; returns the chest. */
        BlockPos outputChest() {
            BlockPos h = bottom(1, 1).down();
            EnumFacing out = wall.rotateY();
            t.place(h, Blocks.HOPPER.getDefaultState().withProperty(BlockHopper.FACING, out));
            t.place(h.offset(out), Blocks.CHEST.getDefaultState());
            return h.offset(out);
        }
    }
    static void fill(TestContext t, BlockPos hopper, ItemStack stack) { t.inventory(hopper).setInventorySlotContents(0, stack); }
    static int count(TestContext t, BlockPos inventoryPos, Item item) {
        IInventory inv = t.inventory(inventoryPos); int n = 0;
        for (int i = 0; i < inv.getSizeInventory(); i++) if (inv.getStackInSlot(i).getItem() == item) n += inv.getStackInSlot(i).getCount();
        return n;
    }
    /** Ingots out: in the chest plus still passing through the output hopper beside it. */
    static int out(TestContext t, BlockPos chest, Item item) { return count(t, chest, item) + hopperBeside(t, chest, item); }
    static int hopperBeside(TestContext t, BlockPos chest, Item item) {
        int n = 0;
        for (EnumFacing f : EnumFacing.HORIZONTALS) if (t.state(chest.offset(f)).getBlock() == Blocks.HOPPER) n += count(t, chest.offset(f), item);
        return n;
    }
    static Item iron() { return Item.getItemFromBlock(Blocks.IRON_ORE); }

    @GameTest(value = "acceptance-s7-says-what-is-wrong")
    public static class Messages implements Scenario {
        @Override public void plan(TestContext t) {
            Kiln[] k = new Kiln[1];
            String[] said = new String[3];
            BlockPos[] missing = new BlockPos[1];
            t.then("kiln missing a top corner brick", () -> {
                k[0] = new Kiln(t, t.origin().add(5, 1, 5), EnumFacing.SOUTH);
                missing[0] = k[0].top(2, 0);
                k[0].build(missing[0]);
                said[0] = k[0].rightClick();
            });
            t.check("names-the-missing-position", () -> {
                List<Integer> n = Accept.numbers(said[0]);
                return n.contains(missing[0].getX()) && n.contains(missing[0].getY()) && n.contains(missing[0].getZ());
            }, () -> "missing brick at " + missing[0].getX() + " " + missing[0].getY() + " " + missing[0].getZ() + "; it said: " + said[0]);
            t.then("add the brick, fill the centre", () -> {
                t.place(missing[0], BRICK);
                t.place(k[0].center, Blocks.COBBLESTONE.getDefaultState());
                said[1] = k[0].rightClick();
            });
            t.check("says-the-middle-is-not-empty", () -> {
                String s = said[1].toLowerCase(Locale.ROOT);
                List<Integer> n = Accept.numbers(said[1]);
                boolean centreCoords = n.contains(k[0].center.getX()) && n.contains(k[0].center.getY()) && n.contains(k[0].center.getZ());
                return !s.trim().isEmpty() && !said[1].equals(said[0]) && (s.matches("(?s).*(cent|middle|empty|hollow|inside|interior|air).*") || centreCoords);
            }, () -> "with cobblestone in the centre it said: " + said[1]);
            t.then("empty the centre", () -> { t.place(k[0].center, Blocks.AIR.getDefaultState()); said[2] = k[0].rightClick(); });
            t.check("then-says-formed", () -> !said[2].trim().isEmpty() && !said[2].equals(said[0]) && !said[2].equals(said[1]) && said[2].toLowerCase(Locale.ROOT).contains("form"),
                    () -> "complete kiln said: " + said[2]);
        }
    }

    @GameTest(value = "acceptance-s7-smelts-two-ingots-per-ore", maxTicks = 3000)
    public static class Smelts implements Scenario {
        @Override public void plan(TestContext t) {
            Kiln[] k = new Kiln[1];
            BlockPos[] chest = new BlockPos[1];
            t.then("formed kiln, controller north; hoppers: ore on top, coal into the middle ring, output below", () -> {
                k[0] = new Kiln(t, t.origin().add(4, 1, 4), EnumFacing.NORTH).build(null);
                BlockPos ore = k[0].oreHopper(0, 2);
                BlockPos fuel = k[0].fuelHopper();
                chest[0] = k[0].outputChest();
                k[0].rightClick();
                fill(t, ore, new ItemStack(Blocks.IRON_ORE, 2));
                t.inventory(ore).setInventorySlotContents(1, new ItemStack(Blocks.GOLD_ORE, 1));
                fill(t, fuel, new ItemStack(Items.COAL, 2));
            });
            t.waitUntil("ingots-come-out", 2400, () -> out(t, chest[0], Items.IRON_INGOT) >= 4 && out(t, chest[0], Items.GOLD_INGOT) >= 2,
                    () -> "output so far: " + out(t, chest[0], Items.IRON_INGOT) + " iron ingots, " + out(t, chest[0], Items.GOLD_INGOT) + " gold ingots (from 2 iron ore, 1 gold ore)");
            t.waitTicks(40);
            t.check("exactly-two-per-ore", () -> out(t, chest[0], Items.IRON_INGOT) == 4 && out(t, chest[0], Items.GOLD_INGOT) == 2,
                    () -> out(t, chest[0], Items.IRON_INGOT) + " iron ingots from 2 ore, " + out(t, chest[0], Items.GOLD_INGOT) + " gold from 1");
        }
    }

    @GameTest(value = "acceptance-s7-any-facing")
    public static class AnyFacing implements Scenario {
        @Override public void plan(TestContext t) {
            Kiln[] k = new Kiln[2];
            BlockPos[] hopper = new BlockPos[2];
            t.then("two kilns, controllers west and south, an ore hopper on each", () -> {
                k[0] = new Kiln(t, t.origin().add(1, 1, 1), EnumFacing.WEST).build(null);
                k[1] = new Kiln(t, t.origin().add(10, 1, 9), EnumFacing.SOUTH).build(null);
                for (int i = 0; i < 2; i++) { k[i].rightClick(); hopper[i] = k[i].oreHopper(1, 1); fill(t, hopper[i], new ItemStack(Blocks.IRON_ORE, 1)); }
            });
            t.waitUntil("both-accept-ore", 200, () -> count(t, hopper[0], iron()) == 0 && count(t, hopper[1], iron()) == 0,
                    () -> "ore left in the hopper: west kiln " + count(t, hopper[0], iron()) + ", south kiln " + count(t, hopper[1], iron()));
        }
    }

    @GameTest(value = "acceptance-s7-broken-stops-rebuilt-resumes", maxTicks = 4000)
    public static class BrokenKiln implements Scenario {
        @Override public void plan(TestContext t) {
            Kiln[] k = new Kiln[1];
            BlockPos[] chest = new BlockPos[1], ore = new BlockPos[1];
            BlockPos[] broken = new BlockPos[1];
            t.then("formed kiln (controller east) smelts one ore", () -> {
                k[0] = new Kiln(t, t.origin().add(4, 1, 4), EnumFacing.EAST).build(null);
                ore[0] = k[0].oreHopper(1, 1);
                BlockPos fuel = k[0].fuelHopper();
                chest[0] = k[0].outputChest();
                k[0].rightClick();
                fill(t, ore[0], new ItemStack(Blocks.IRON_ORE, 1));
                fill(t, fuel, new ItemStack(Items.COAL, 2));
            });
            t.waitUntil("first-ore-smelted", 1200, () -> out(t, chest[0], Items.IRON_INGOT) >= 2, () -> "output " + out(t, chest[0], Items.IRON_INGOT));
            t.then("break a top corner brick, then offer more ore", () -> {
                broken[0] = k[0].top(0, 0);
                Accept.Listener miner = k[0].p;
                miner.setHeldItem(EnumHand.MAIN_HAND, new ItemStack(Items.DIAMOND_PICKAXE));
                t.require(t.breakBlock(miner, broken[0]), "could not break the brick");
                fill(t, ore[0], new ItemStack(Blocks.IRON_ORE, 1));
            });
            t.waitTicks(200);
            t.check("broken-kiln-takes-nothing", () -> count(t, ore[0], iron()) == 1 && out(t, chest[0], Items.IRON_INGOT) == 2,
                    () -> "with a brick missing: ore left in the hopper " + count(t, ore[0], iron()) + " (want 1), ingots out " + out(t, chest[0], Items.IRON_INGOT) + " (want 2)");
            t.then("rebuild it", () -> { t.place(broken[0], BRICK); k[0].rightClick(); });
            t.waitUntil("resumes-with-the-fuel-it-kept", 1200, () -> out(t, chest[0], Items.IRON_INGOT) >= 4,
                    () -> "after rebuilding (no new coal given): ingots out " + out(t, chest[0], Items.IRON_INGOT) + ", ore in hopper " + count(t, ore[0], iron()));
        }
    }

    @GameTest(value = "acceptance-s7-controller-drops-contents", maxTicks = 1200)
    public static class ControllerDrops implements Scenario {
        @Override public void plan(TestContext t) {
            Kiln[] k = new Kiln[1];
            BlockPos[] ore = new BlockPos[1], fuel = new BlockPos[1];
            int[] dropped = new int[3];
            t.then("formed kiln (controller south), 3 ore and 2 coal in, no output hopper", () -> {
                k[0] = new Kiln(t, t.origin().add(4, 1, 4), EnumFacing.SOUTH).build(null);
                ore[0] = k[0].oreHopper(1, 1);
                fuel[0] = k[0].fuelHopper();
                k[0].rightClick();
                fill(t, ore[0], new ItemStack(Blocks.IRON_ORE, 3));
                fill(t, fuel[0], new ItemStack(Items.COAL, 2));
            });
            t.waitUntil("hoppers-emptied-into-it", 200, () -> count(t, ore[0], iron()) == 0 && count(t, fuel[0], Items.COAL) == 0,
                    () -> "still in hoppers: ore " + count(t, ore[0], iron()) + ", coal " + count(t, fuel[0], Items.COAL));
            t.then("break the controller", () -> {
                Accept.Listener miner = k[0].p;
                miner.setHeldItem(EnumHand.MAIN_HAND, new ItemStack(Items.DIAMOND_PICKAXE));
                t.require(t.breakBlock(miner, k[0].controller), "could not break the controller");
                List<EntityItem> items = Accept.items(t, k[0].controller, 3);
                dropped[0] = Accept.countEntities(items, iron());
                dropped[1] = Accept.countEntities(items, Items.IRON_INGOT);
                dropped[2] = Accept.countEntities(items, Items.COAL);
            });
            t.check("drops-everything-inside", () -> dropped[0] + dropped[1] / 2 == 3 && dropped[2] >= 1,
                    () -> "dropped " + dropped[0] + " ore, " + dropped[1] + " ingots, " + dropped[2] + " coal (put in: 3 ore, 2 coal)");
        }
    }
}
