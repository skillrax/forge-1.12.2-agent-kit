package acceptance;

import java.util.*;
import kit.gametest.GameTest;
import kit.gametest.Scenario;
import kit.gametest.TestContext;
import net.minecraft.block.Block;
import net.minecraft.block.BlockRedstoneComparator;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.ai.EntityAITasks;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.init.MobEffects;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.CraftingManager;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.item.crafting.Ingredient;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.tileentity.TileEntityComparator;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidUtil;
import net.minecraftforge.fluids.IFluidBlock;
import net.minecraftforge.fluids.capability.CapabilityFluidHandler;
import net.minecraftforge.fluids.capability.IFluidHandler;

/** S4 "Maple syrup fluid and tank", from the request only. */
public final class S4Acceptance {
    static final String MOD = "maplesyrup";
    static final String FLUID = "maple_syrup";
    static final String TANK = MOD + ":tank";

    static Fluid syrup(TestContext t) {
        Fluid f = FluidRegistry.getFluid(FLUID);
        t.require(f != null, "no fluid '" + FLUID + "' in FluidRegistry");
        return f;
    }
    static Block syrupBlock(TestContext t) {
        Fluid f = syrup(t);
        if (f.getBlock() != null) return f.getBlock();
        for (Block b : Block.REGISTRY) if (b instanceof IFluidBlock && ((IFluidBlock) b).getFluid() == f) return b;
        throw new AssertionError("fluid " + FLUID + " has no block, so it cannot be poured out");
    }
    /** The filled syrup bucket: Forge's universal bucket, or the mod's own bucket item. */
    static ItemStack syrupBucket(TestContext t) {
        Fluid f = syrup(t);
        ItemStack universal = FluidUtil.getFilledBucket(new FluidStack(f, Fluid.BUCKET_VOLUME));
        if (!universal.isEmpty()) return universal;
        for (Item item : Item.REGISTRY) {
            FluidStack in = FluidUtil.getFluidContained(new ItemStack(item));
            if (in != null && in.getFluid() == f) return new ItemStack(item);
        }
        for (Item item : Item.REGISTRY)
            if (item.getRegistryName().getNamespace().equals(MOD) && item.getRegistryName().getPath().contains("bucket")) return new ItemStack(item);
        throw new AssertionError("no bucket of " + FLUID + " (no universal bucket, no item containing it, no " + MOD + ":*bucket* item)");
    }
    static boolean isSyrupBucket(TestContext t, ItemStack stack) {
        if (stack.isEmpty()) return false;
        FluidStack in = FluidUtil.getFluidContained(stack);
        if (in != null) return in.getFluid() == syrup(t);
        ItemStack bucket = syrupBucket(t);
        return stack.getItem() == bucket.getItem() && stack.getMetadata() == bucket.getMetadata();
    }
    static Accept.Listener player(TestContext t, String name, BlockPos at, float pitch) {
        Accept.Listener p = new Accept.Listener(t.world(), name);
        p.setLocationAndAngles(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 0, pitch);
        return p;
    }
    static IFluidHandler handler(TestContext t, BlockPos pos, EnumFacing side) {
        TileEntity te = t.world().getTileEntity(pos);
        return te != null && te.hasCapability(CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY, side) ? te.getCapability(CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY, side) : null;
    }
    /** What the tank holds, read the way a pipe would (side null, else any side). */
    static FluidStack contents(TestContext t, BlockPos pos) {
        IFluidHandler h = handler(t, pos, null);
        for (EnumFacing side : EnumFacing.values()) if (h == null) h = handler(t, pos, side);
        if (h == null) return null;
        return h.drain(Integer.MAX_VALUE, false);
    }
    static String describe(FluidStack fs) { return fs == null ? "nothing" : fs.amount + " mB " + fs.getFluid().getName(); }
    static boolean holds(TestContext t, BlockPos pos, Fluid fluid, int amount) {
        FluidStack fs = contents(t, pos);
        return amount == 0 ? fs == null || fs.amount == 0 : fs != null && fs.getFluid() == fluid && fs.amount == amount;
    }
    static void click(TestContext t, Accept.Listener p, BlockPos pos, ItemStack held) {
        p.setHeldItem(EnumHand.MAIN_HAND, held);
        t.useOnBlock(p, pos, EnumFacing.UP, EnumHand.MAIN_HAND);
    }
    /** Farthest block of a fluid from its source along the floor (Manhattan distance, radius 8). */
    static int reach(TestContext t, BlockPos source, Block... blocks) {
        int best = -1;
        for (int x = -8; x <= 8; x++) for (int z = -8; z <= 8; z++) {
            Block b = t.state(source.add(x, 0, z)).getBlock();
            for (Block wanted : blocks) if (b == wanted) best = Math.max(best, Math.abs(x) + Math.abs(z));
        }
        return best;
    }

    @GameTest(value = "acceptance-s4-bucket-recipe")
    public static class BucketRecipe implements Scenario {
        @Override public void plan(TestContext t) {
            List<String> found = new ArrayList<>();
            t.check("bucket-from-bucket-sugar-logs", () -> {
                for (IRecipe recipe : CraftingManager.REGISTRY) {
                    if (!isSyrupBucket(t, recipe.getRecipeOutput())) continue;
                    boolean bucket = false, sugar = false, log = false;
                    for (Ingredient ing : recipe.getIngredients()) {
                        bucket |= ing.apply(new ItemStack(Items.BUCKET));
                        sugar |= ing.apply(new ItemStack(Items.SUGAR));
                        log |= ing.apply(new ItemStack(Blocks.LOG)) || ing.apply(new ItemStack(Blocks.LOG, 1, 2)) || ing.apply(new ItemStack(Blocks.LOG2));
                    }
                    found.add(recipe.getRegistryName() + ": bucket " + bucket + ", sugar " + sugar + ", log " + log);
                    if (bucket && sugar && log) return true;
                }
                return false;
            }, () -> found.isEmpty() ? "no crafting recipe makes a syrup bucket" : String.join("; ", found));
        }
    }

    @GameTest(value = "acceptance-s4-pour-out")
    public static class Pour implements Scenario {
        @Override public void plan(TestContext t) {
            BlockPos at = t.origin().add(5, 0, 5);
            Accept.Listener[] p = new Accept.Listener[1];
            t.then("right-click the floor with a syrup bucket, looking down", () -> {
                p[0] = player(t, "Pourer", at, 90);
                p[0].setHeldItem(EnumHand.MAIN_HAND, syrupBucket(t));
                t.useItem(p[0], EnumHand.MAIN_HAND);
            });
            t.check("syrup-in-the-world", () -> t.state(at).getBlock() == syrupBlock(t), () -> "at the player's feet: " + t.state(at));
            t.check("bucket-is-empty-after", () -> p[0].getHeldItemMainhand().getItem() == Items.BUCKET, () -> "in hand: " + Accept.describe(p[0].getHeldItemMainhand()));
        }
    }

    @GameTest(value = "acceptance-s4-flows-slower-shorter", maxTicks = 1200)
    public static class Flow implements Scenario {
        @Override public void plan(TestContext t) {
            BlockPos s = t.origin().add(3, 0, 3), w = t.origin().add(12, 0, 12);
            int[] at = new int[1];
            t.then("syrup and water sources far apart", () -> { t.place(s, syrupBlock(t).getDefaultState()); t.place(w, Blocks.FLOWING_WATER.getDefaultState()); });
            t.waitUntil("water-spreads-2", 100, () -> reach(t, w, Blocks.WATER, Blocks.FLOWING_WATER) >= 2, () -> "water reach " + reach(t, w, Blocks.WATER, Blocks.FLOWING_WATER));
            t.check("syrup-is-slower", () -> { at[0] = reach(t, s, syrupBlock(t)); return at[0] < 2; }, () -> "when water had spread 2 blocks, syrup had spread " + at[0]);
            t.waitTicks(800);
            t.check("syrup-flows", () -> reach(t, s, syrupBlock(t)) >= 1, () -> "syrup never left its source block");
            t.check("syrup-is-shorter", () -> reach(t, s, syrupBlock(t)) < reach(t, w, Blocks.WATER, Blocks.FLOWING_WATER),
                    () -> "syrup spread " + reach(t, s, syrupBlock(t)) + " blocks, water " + reach(t, w, Blocks.WATER, Blocks.FLOWING_WATER));
        }
    }

    @GameTest(value = "acceptance-s4-slows-mobs")
    public static class Slows implements Scenario {
        @Override public void plan(TestContext t) {
            BlockPos lane = t.origin().add(1, 0, 2), dry = t.origin().add(1, 0, 13);
            EntityLiving[] pig = new EntityLiving[2];
            double[] start = new double[2];
            int[] ticks = new int[1];
            t.then("a syrup lane and a dry lane, a pig at the start of each", () -> {
                for (int x = 0; x < 13; x++) t.place(lane.add(x, 0, 0), syrupBlock(t).getDefaultState());
                pig[0] = (EntityLiving) t.spawn("minecraft:pig", lane);
                pig[1] = (EntityLiving) t.spawn("minecraft:pig", dry);
                for (EntityLiving e : pig) {
                    for (EntityAITasks.EntityAITaskEntry task : new ArrayList<>(e.tasks.taskEntries)) e.tasks.removeTask(task.action);
                    for (EntityAITasks.EntityAITaskEntry task : new ArrayList<>(e.targetTasks.taskEntries)) e.targetTasks.removeTask(task.action);
                }
            });
            t.waitTicks(10);
            t.then("note start", () -> { start[0] = pig[0].posX; start[1] = pig[1].posX; });
            t.waitUntil("walk-east-40-ticks", 60, () -> {
                pig[0].getMoveHelper().setMoveTo(lane.getX() + 12.5, lane.getY(), lane.getZ() + 0.5, 1.0);
                pig[1].getMoveHelper().setMoveTo(dry.getX() + 12.5, dry.getY(), dry.getZ() + 0.5, 1.0);
                return ++ticks[0] >= 40;
            }, () -> "");
            t.check("slower-in-syrup", () -> {
                double inSyrup = pig[0].posX - start[0], onStone = pig[1].posX - start[1];
                return onStone > 1.5 && (inSyrup < 0.75 * onStone || pig[0].isPotionActive(MobEffects.SLOWNESS));
            }, () -> String.format(Locale.ROOT, "in 40 ticks the pig walked %.2f blocks in syrup, %.2f on stone; slowness effect %s",
                    pig[0].posX - start[0], pig[1].posX - start[1], pig[0].getActivePotionEffect(MobEffects.SLOWNESS)));
        }
    }

    @GameTest(value = "acceptance-s4-tank-buckets")
    public static class TankBuckets implements Scenario {
        @Override public void plan(TestContext t) {
            BlockPos tank = t.origin().add(5, 0, 5);
            Accept.Listener[] p = new Accept.Listener[1];
            List<String> problems = new ArrayList<>();
            int[] before = new int[1];
            t.then("pour in 16 water buckets", () -> {
                t.place(tank, TANK);
                p[0] = player(t, "Filler", t.origin().add(5, 0, 3), 0);
                for (int i = 1; i <= 16; i++) {
                    click(t, p[0], tank, new ItemStack(Items.WATER_BUCKET));
                    if (p[0].getHeldItemMainhand().getItem() != Items.BUCKET && problems.isEmpty()) problems.add("bucket " + i + " left " + Accept.describe(p[0].getHeldItemMainhand()) + " in hand, tank " + describe(contents(t, tank)));
                }
            });
            t.check("holds-16-buckets", () -> problems.isEmpty() && holds(t, tank, FluidRegistry.WATER, 16000), () -> String.join("; ", problems) + " tank holds " + describe(contents(t, tank)));
            t.then("a 17th bucket", () -> click(t, p[0], tank, new ItemStack(Items.WATER_BUCKET)));
            t.check("seventeenth-refused-bucket-kept", () -> p[0].getHeldItemMainhand().getItem() == Items.WATER_BUCKET && holds(t, tank, FluidRegistry.WATER, 16000),
                    () -> "hand " + Accept.describe(p[0].getHeldItemMainhand()) + ", tank " + describe(contents(t, tank)));
            t.then("take one out with an empty bucket", () -> click(t, p[0], tank, new ItemStack(Items.BUCKET)));
            t.check("empty-bucket-takes-one", () -> p[0].getHeldItemMainhand().getItem() == Items.WATER_BUCKET && holds(t, tank, FluidRegistry.WATER, 15000),
                    () -> "hand " + Accept.describe(p[0].getHeldItemMainhand()) + ", tank " + describe(contents(t, tank)));
            t.then("empty hand", () -> { before[0] = p[0].received.size(); click(t, p[0], tank, ItemStack.EMPTY); });
            t.check("says-what-and-how-much", () -> {
                String said = String.join(" ", p[0].received.subList(before[0], p[0].received.size()));
                List<Integer> n = Accept.numbers(said);
                return said.toLowerCase(Locale.ROOT).contains("water") && (n.contains(15000) || n.contains(15));
            }, () -> "messages: " + p[0].received.subList(before[0], p[0].received.size()));
        }
    }

    @GameTest(value = "acceptance-s4-tank-any-liquid")
    public static class AnyLiquid implements Scenario {
        @Override public void plan(TestContext t) {
            BlockPos lava = t.origin().add(3, 0, 5), syrupTank = t.origin().add(9, 0, 5);
            t.then("lava into one tank, syrup into another", () -> {
                t.place(lava, TANK); t.place(syrupTank, TANK);
                Accept.Listener p = player(t, "Filler", t.origin().add(6, 0, 2), 0);
                click(t, p, lava, new ItemStack(Items.LAVA_BUCKET));
                click(t, p, syrupTank, syrupBucket(t));
            });
            t.check("holds-lava", () -> holds(t, lava, FluidRegistry.LAVA, 1000), () -> "lava tank holds " + describe(contents(t, lava)));
            t.check("holds-syrup", () -> holds(t, syrupTank, syrup(t), 1000), () -> "syrup tank holds " + describe(contents(t, syrupTank)));
        }
    }

    @GameTest(value = "acceptance-s4-tank-keeps-liquid")
    public static class KeepsLiquid implements Scenario {
        @Override public void plan(TestContext t) {
            BlockPos tank = t.origin().add(4, 0, 4), again = t.origin().add(10, 0, 10);
            Accept.Listener[] p = new Accept.Listener[1];
            ItemStack[] dropped = new ItemStack[1];
            t.then("3 buckets in, then break it with a pickaxe", () -> {
                t.place(tank, TANK);
                p[0] = player(t, "Mover", t.origin().add(7, 0, 7), 0);
                for (int i = 0; i < 3; i++) click(t, p[0], tank, new ItemStack(Items.WATER_BUCKET));
                t.require(holds(t, tank, FluidRegistry.WATER, 3000), "after 3 buckets the tank holds " + describe(contents(t, tank)));
                p[0].setHeldItem(EnumHand.MAIN_HAND, new ItemStack(Items.DIAMOND_PICKAXE));
                t.require(t.breakBlock(p[0], tank), "could not break the tank");
                for (EntityItem e : Accept.items(t, tank, 1.5)) if (Block.getBlockFromItem(e.getItem().getItem()) == t.block(TANK)) { dropped[0] = e.getItem().copy(); e.setDead(); }
            });
            t.check("tank-drops-itself", () -> dropped[0] != null, () -> "no tank item dropped");
            t.then("place it again", () -> {
                p[0].setHeldItem(EnumHand.MAIN_HAND, dropped[0]);
                t.useOnBlock(p[0], again.down(), EnumFacing.UP, EnumHand.MAIN_HAND);
            });
            t.check("liquid-still-inside", () -> t.state(again).getBlock() == t.block(TANK) && holds(t, again, FluidRegistry.WATER, 3000),
                    () -> "placed " + t.state(again) + " holding " + describe(contents(t, again)) + " (dropped stack " + Accept.describe(dropped[0]) + ")");
        }
    }

    @GameTest(value = "acceptance-s4-tank-pipes-comparator")
    public static class PipesComparator implements Scenario {
        @Override public void plan(TestContext t) {
            BlockPos tank = t.origin().add(6, 0, 6), comparator = tank.north();
            List<String> sides = new ArrayList<>();
            int[] level = new int[4];
            t.then("tank with a comparator reading it", () -> {
                t.place(tank, TANK);
                t.place(comparator, Blocks.UNPOWERED_COMPARATOR.getDefaultState().withProperty(BlockRedstoneComparator.FACING, EnumFacing.SOUTH));
            });
            t.check("fluid-capability-on-every-side", () -> {
                for (EnumFacing side : EnumFacing.values()) if (handler(t, tank, side) == null) sides.add(side.getName());
                return sides.isEmpty();
            }, () -> "no fluid handler capability (what pipes connect to) on: " + sides);
            t.then("a pipe pushes 1, 8, then 16 buckets in from the east", () -> {
                level[0] = t.state(tank).getComparatorInputOverride(t.world(), tank);
                handler(t, tank, EnumFacing.EAST).fill(new FluidStack(FluidRegistry.WATER, 1000), true);
                level[1] = t.state(tank).getComparatorInputOverride(t.world(), tank);
                handler(t, tank, EnumFacing.EAST).fill(new FluidStack(FluidRegistry.WATER, 7000), true);
                level[2] = t.state(tank).getComparatorInputOverride(t.world(), tank);
                handler(t, tank, EnumFacing.EAST).fill(new FluidStack(FluidRegistry.WATER, 8000), true);
                level[3] = t.state(tank).getComparatorInputOverride(t.world(), tank);
            });
            t.check("pipe-can-drain-from-below", () -> { FluidStack d = handler(t, tank, EnumFacing.DOWN).drain(1000, false); return d != null && d.amount == 1000; },
                    () -> "simulated drain of 1000 mB from below gave " + describe(handler(t, tank, EnumFacing.DOWN).drain(1000, false)));
            t.check("comparator-level-0-to-15", () -> t.state(tank).hasComparatorInputOverride() && level[0] == 0 && level[1] >= 1 && level[1] <= level[2] && level[2] >= 6 && level[2] <= 9 && level[3] == 15,
                    () -> "comparator override " + t.state(tank).hasComparatorInputOverride() + ", levels at 0/1/8/16 buckets: " + Arrays.toString(level));
            t.waitTicks(4);
            t.check("comparator-block-updates", () -> {
                TileEntity te = t.world().getTileEntity(comparator);
                return te instanceof TileEntityComparator && ((TileEntityComparator) te).getOutputSignal() == 15;
            }, () -> {
                TileEntity te = t.world().getTileEntity(comparator);
                return "a comparator next to the full tank outputs " + (te instanceof TileEntityComparator ? ((TileEntityComparator) te).getOutputSignal() : -1);
            });
        }
    }
}
