package acceptance;

import java.util.*;
import kit.gametest.GameTest;
import kit.gametest.Scenario;
import kit.gametest.TestContext;
import net.minecraft.block.Block;
import net.minecraft.block.BlockLever;
import net.minecraft.block.BlockRedstoneComparator;
import net.minecraft.block.BlockRedstoneWire;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.item.crafting.Ingredient;
import net.minecraft.item.crafting.CraftingManager;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.tileentity.TileEntityComparator;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;

/**
 * S2 "Pulse counter block", from the request only. Layout in every test: the player places the counter at C
 * standing west of it and facing east, so (like a repeater) the back is west and the front is east. A lever
 * on the floor at the back is the input; redstone dust on the other sides shows what comes out.
 */
public final class S2Acceptance {
    static final String COUNTER = "pulsecounter:pulse_counter";

    static BlockPos counter(TestContext t) { return t.origin().add(6, 0, 6); }

    static final class Rig {
        final TestContext t; final BlockPos c; final Accept.Listener p;
        Rig(TestContext t, boolean dust) {
            this.t = t; c = counter(t);
            p = new Accept.Listener(t.world(), "Builder");
            p.setLocationAndAngles(c.getX() - 2 + 0.5, c.getY(), c.getZ() + 0.5, 270, 0);   // yaw 270: facing east
            p.setHeldItem(EnumHand.MAIN_HAND, t.stack(COUNTER, 1));
            t.useOnBlock(p, c.down(), EnumFacing.UP, EnumHand.MAIN_HAND);
            t.require(t.state(c).getBlock() == t.block(COUNTER), "placing " + COUNTER + " from the item failed: " + t.state(c));
            t.place(c.west(), Blocks.LEVER.getDefaultState().withProperty(BlockLever.FACING, BlockLever.EnumOrientation.UP_X));
            if (dust) for (EnumFacing side : new EnumFacing[]{EnumFacing.EAST, EnumFacing.NORTH, EnumFacing.SOUTH}) t.place(c.offset(side), Blocks.REDSTONE_WIRE.getDefaultState());
            p.setHeldItem(EnumHand.MAIN_HAND, ItemStack.EMPTY);
        }
        void lever() { p.setSneaking(false); p.setHeldItem(EnumHand.MAIN_HAND, ItemStack.EMPTY); t.useOnBlock(p, c.west(), EnumFacing.UP, EnumHand.MAIN_HAND); }
        boolean leverOn() { return t.state(c.west()).getValue(BlockLever.POWERED); }
        int dust(EnumFacing side) { IBlockState s = t.state(c.offset(side)); return s.getBlock() == Blocks.REDSTONE_WIRE ? s.getValue(BlockRedstoneWire.POWER) : -1; }
        /** Power the counter sends into its neighbor on that side. */
        int out(EnumFacing side) { return t.world().getRedstonePower(c, side.getOpposite()); }
        String sides() { return "front(east) " + out(EnumFacing.EAST) + ", back(west) " + out(EnumFacing.WEST) + ", north " + out(EnumFacing.NORTH) + ", south " + out(EnumFacing.SOUTH)
                + "; dust east " + dust(EnumFacing.EAST) + ", north " + dust(EnumFacing.NORTH) + ", south " + dust(EnumFacing.SOUTH) + "; counter " + t.state(c); }
    }
    /** Declares n on-off lever pulses, 4 ticks each way. */
    static void pulses(TestContext t, Rig[] rig, int n) {
        for (int i = 0; i < n; i++) {
            t.then("lever on", () -> rig[0].lever()); t.waitTicks(4);
            t.then("lever off", () -> rig[0].lever()); t.waitTicks(4);
        }
    }

    @GameTest(value = "acceptance-s2-counts-rising-edges")
    public static class Counts implements Scenario {
        @Override public void plan(TestContext t) {
            Rig[] rig = new Rig[1];
            t.then("place counter, lever behind, dust on the other sides", () -> rig[0] = new Rig(t, true));
            t.waitTicks(4);
            t.check("starts-at-zero", () -> rig[0].dust(EnumFacing.EAST) == 0, () -> rig[0].sides());
            pulses(t, rig, 3);
            t.check("three-pulses-front-3", () -> rig[0].dust(EnumFacing.EAST) == 3 && rig[0].out(EnumFacing.EAST) == 3, () -> rig[0].sides());
            t.check("only-the-front-outputs", () -> rig[0].dust(EnumFacing.NORTH) == 0 && rig[0].dust(EnumFacing.SOUTH) == 0 && rig[0].out(EnumFacing.WEST) == 0, () -> rig[0].sides());
            t.then("lever on and held", () -> rig[0].lever());
            t.waitTicks(60);
            t.check("held-on-counts-once", () -> rig[0].leverOn() && rig[0].dust(EnumFacing.EAST) == 4, () -> "after 60 ticks held on: " + rig[0].sides());
        }
    }

    @GameTest(value = "acceptance-s2-wraps-at-16", maxTicks = 2400)
    public static class Wraps implements Scenario {
        @Override public void plan(TestContext t) {
            Rig[] rig = new Rig[1];
            t.then("place counter", () -> rig[0] = new Rig(t, true));
            t.waitTicks(4);
            pulses(t, rig, 15);
            t.check("fifteen-pulses-15", () -> rig[0].out(EnumFacing.EAST) == 15 && rig[0].dust(EnumFacing.EAST) == 15, () -> rig[0].sides());
            pulses(t, rig, 1);
            t.check("sixteenth-wraps-to-0", () -> rig[0].out(EnumFacing.EAST) == 0 && rig[0].dust(EnumFacing.EAST) == 0, () -> rig[0].sides());
            pulses(t, rig, 1);
            t.check("then-counts-again", () -> rig[0].out(EnumFacing.EAST) == 1, () -> rig[0].sides());
        }
    }

    @GameTest(value = "acceptance-s2-chat-and-reset")
    public static class ChatReset implements Scenario {
        @Override public void plan(TestContext t) {
            Rig[] rig = new Rig[1];
            int[] before = new int[1];
            t.then("place counter", () -> rig[0] = new Rig(t, true));
            t.waitTicks(4);
            pulses(t, rig, 3);
            t.then("right-click with an empty hand", () -> {
                before[0] = rig[0].p.received.size();
                rig[0].p.setSneaking(false);
                t.useOnBlock(rig[0].p, rig[0].c, EnumFacing.UP, EnumHand.MAIN_HAND);
            });
            t.check("tells-the-count", () -> rig[0].p.received.size() > before[0] && Accept.numbers(String.join(" ", rig[0].p.received.subList(before[0], rig[0].p.received.size()))).contains(3),
                    () -> "messages: " + rig[0].p.received);
            t.check("looking-does-not-change-it", () -> rig[0].out(EnumFacing.EAST) == 3, () -> rig[0].sides());
            t.then("sneak-right-click", () -> { rig[0].p.setSneaking(true); t.useOnBlock(rig[0].p, rig[0].c, EnumFacing.UP, EnumHand.MAIN_HAND); rig[0].p.setSneaking(false); });
            t.waitTicks(4);
            t.check("reset-to-0", () -> rig[0].out(EnumFacing.EAST) == 0 && rig[0].dust(EnumFacing.EAST) == 0, () -> rig[0].sides());
            pulses(t, rig, 1);
            t.check("counts-from-0-again", () -> rig[0].out(EnumFacing.EAST) == 1, () -> rig[0].sides());
        }
    }

    @GameTest(value = "acceptance-s2-comparator")
    public static class Comparator implements Scenario {
        @Override public void plan(TestContext t) {
            Rig[] rig = new Rig[1];
            BlockPos[] comparator = new BlockPos[1];
            t.then("place counter, comparator on the north side reading from it", () -> {
                rig[0] = new Rig(t, false);
                comparator[0] = rig[0].c.north();
                // A comparator's input is the block its FACING points to.
                t.place(comparator[0], Blocks.UNPOWERED_COMPARATOR.getDefaultState().withProperty(BlockRedstoneComparator.FACING, EnumFacing.SOUTH));
            });
            t.waitTicks(4);
            pulses(t, rig, 5);
            t.waitTicks(4);
            t.check("comparator-shows-5", () -> {
                TileEntity te = t.world().getTileEntity(comparator[0]);
                return te instanceof TileEntityComparator && ((TileEntityComparator) te).getOutputSignal() == 5;
            }, () -> {
                TileEntity te = t.world().getTileEntity(comparator[0]);
                IBlockState s = t.state(rig[0].c);
                return "comparator output " + (te instanceof TileEntityComparator ? ((TileEntityComparator) te).getOutputSignal() : -1) + "; counter has comparator override "
                        + s.hasComparatorInputOverride() + (s.hasComparatorInputOverride() ? " = " + s.getComparatorInputOverride(t.world(), rig[0].c) : "") + "; " + rig[0].sides();
            });
        }
    }

    @GameTest(value = "acceptance-s2-survives-reload")
    public static class Reload implements Scenario {
        @Override public void plan(TestContext t) {
            Rig[] rig = new Rig[1];
            t.then("place counter", () -> rig[0] = new Rig(t, true));
            t.waitTicks(4);
            pulses(t, rig, 3);
            t.then("save and load the block as a chunk reload does (metadata and tile NBT)", () -> {
                BlockPos c = rig[0].c;
                IBlockState state = t.state(c);
                Block block = state.getBlock();
                IBlockState loaded = block.getStateFromMeta(block.getMetaFromState(state));
                TileEntity te = t.world().getTileEntity(c);
                NBTTagCompound tag = te == null ? null : te.writeToNBT(new NBTTagCompound());
                t.world().removeTileEntity(c);
                t.world().setBlockState(c, Blocks.AIR.getDefaultState(), 2);
                t.world().setBlockState(c, loaded, 2);
                if (tag != null) {
                    TileEntity fresh = t.world().getTileEntity(c);
                    t.require(fresh != null, "no tile entity after placing the saved state " + loaded + " back");
                    fresh.readFromNBT(tag);
                }
                t.world().notifyNeighborsOfStateChange(c, block, false);
            });
            t.waitTicks(4);
            t.check("count-still-3", () -> rig[0].out(EnumFacing.EAST) == 3, () -> "after reload: " + rig[0].sides());
            pulses(t, rig, 1);
            t.check("keeps-counting-after-reload", () -> rig[0].out(EnumFacing.EAST) == 4, () -> rig[0].sides());
        }
    }

    @GameTest(value = "acceptance-s2-recipe")
    public static class Recipe implements Scenario {
        @Override public void plan(TestContext t) {
            List<String> found = new ArrayList<>();
            t.check("craftable-from-repeater-comparator-stone", () -> {
                for (IRecipe recipe : CraftingManager.REGISTRY) {
                    if (recipe.getRecipeOutput().getItem() != t.item(COUNTER)) continue;
                    boolean repeater = false, comparator = false, stone = false;
                    for (Ingredient ing : recipe.getIngredients()) {
                        repeater |= ing.apply(new ItemStack(Items.REPEATER));
                        comparator |= ing.apply(new ItemStack(Items.COMPARATOR));
                        stone |= ing.apply(new ItemStack(Blocks.STONE)) || ing.apply(new ItemStack(Blocks.COBBLESTONE));
                    }
                    found.add(recipe.getRegistryName() + ": repeater " + repeater + ", comparator " + comparator + ", stone " + stone);
                    if (repeater && comparator && stone) return true;
                }
                return false;
            }, () -> found.isEmpty() ? "no crafting recipe makes " + COUNTER : String.join("; ", found));
        }
    }
}
