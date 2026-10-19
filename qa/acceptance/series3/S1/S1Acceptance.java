package acceptance;

import java.util.*;
import kit.gametest.GameTest;
import kit.gametest.Scenario;
import kit.gametest.TestContext;
import net.minecraft.block.Block;
import net.minecraft.block.BlockFarmland;
import net.minecraft.block.BlockTallGrass;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.init.MobEffects;
import net.minecraft.item.ItemFood;
import net.minecraft.item.ItemStack;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.EnumSkyBlock;

/** S1 "Glowberry crop", from the request only. */
public final class S1Acceptance {
    static final String SEEDS = "glowberries:glowberry_seeds";
    static final String BERRY = "glowberries:glowberry";
    static final String CROP = "glowberries:glowberry_bush";

    static void farmland(TestContext t, BlockPos pos) {
        t.place(pos, Blocks.FARMLAND.getDefaultState().withProperty(BlockFarmland.MOISTURE, 7));
    }
    /** Plants seeds by right-clicking the top of the farmland at pos, as a player does. */
    static void plant(TestContext t, Accept.Listener p, BlockPos farmland) {
        p.setHeldItem(EnumHand.MAIN_HAND, t.stack(SEEDS, 1));
        t.useOnBlock(p, farmland, EnumFacing.UP, EnumHand.MAIN_HAND);
        t.require(t.state(farmland.up()).getBlock() == t.block(CROP), "right-clicking farmland with " + SEEDS + " did not plant " + CROP + " (got " + t.state(farmland.up()) + ")");
    }
    /** One bonemeal use on the crop; true if it was used up. */
    static boolean bonemeal(TestContext t, Accept.Listener p, BlockPos crop) {
        p.setHeldItem(EnumHand.MAIN_HAND, new ItemStack(Items.DYE, 64, 15));
        t.useOnBlock(p, crop, EnumFacing.UP, EnumHand.MAIN_HAND);
        return p.getHeldItemMainhand().getCount() < 64;
    }
    /** Random ticks (as the world gives them) until the crop stops changing; returns the distinct states, planted first. */
    static List<IBlockState> growFully(TestContext t, BlockPos crop) {
        List<IBlockState> seen = new ArrayList<>();
        seen.add(t.state(crop));
        int lastChange = 0;
        for (int i = 1; i < 6000 && i - lastChange < 500; i++) {
            IBlockState before = t.state(crop);
            before.getBlock().randomTick(t.world(), crop, before, t.world().rand);
            IBlockState after = t.state(crop);
            if (after != before) { lastChange = i; if (!seen.contains(after)) seen.add(after); }
        }
        return seen;
    }
    static Accept.Listener farmer(TestContext t, String name) {
        Accept.Listener p = new Accept.Listener(t.world(), name);
        p.setLocationAndAngles(t.origin().getX() + 0.5, t.origin().getY(), t.origin().getZ() + 0.5, 0, 0);
        return p;
    }

    @GameTest(value = "acceptance-s1-grass-seeds")
    public static class GrassSeeds implements Scenario {
        @Override public void plan(TestContext t) {
            int[] found = new int[2];
            t.check("seeds-from-breaking-tall-grass", () -> {
                BlockPos pos = t.origin().add(4, 0, 4);
                t.place(pos.down(), Blocks.GRASS.getDefaultState());
                Accept.Listener p = farmer(t, "Mower");
                for (int i = 0; i < 800 && found[0] == 0; i++) {
                    t.place(pos, Blocks.TALLGRASS.getDefaultState().withProperty(BlockTallGrass.TYPE, BlockTallGrass.EnumType.GRASS));
                    p.setHeldItem(EnumHand.MAIN_HAND, ItemStack.EMPTY);
                    t.require(t.breakBlock(p, pos), "could not break tall grass");
                    List<EntityItem> drops = Accept.items(t, pos, 1);
                    found[0] += Accept.countEntities(drops, t.item(SEEDS));
                    found[1] += Accept.countEntities(drops, Items.WHEAT_SEEDS);
                    for (EntityItem e : drops) e.setDead();
                }
                return found[0] > 0;
            }, () -> "800 tall grass broken by hand: " + found[1] + " wheat seeds, no " + SEEDS);
        }
    }

    @GameTest(value = "acceptance-s1-plant-on-farmland")
    public static class Plant implements Scenario {
        @Override public void plan(TestContext t) {
            BlockPos farm = t.origin().add(3, 0, 3), dirt = t.origin().add(7, 0, 3);
            Accept.Listener[] p = new Accept.Listener[1];
            t.then("farmland and dirt", () -> { farmland(t, farm); t.place(dirt, Blocks.DIRT.getDefaultState()); p[0] = farmer(t, "Farmer"); });
            t.then("plant on farmland", () -> plant(t, p[0], farm));
            t.then("try on dirt", () -> { p[0].setHeldItem(EnumHand.MAIN_HAND, t.stack(SEEDS, 1)); t.useOnBlock(p[0], dirt, EnumFacing.UP, EnumHand.MAIN_HAND); });
            t.check("not-on-dirt", () -> t.state(dirt.up()).getBlock() != t.block(CROP), () -> "seeds planted on plain dirt");
        }
    }

    @GameTest(value = "acceptance-s1-four-stages")
    public static class Stages implements Scenario {
        @Override public void plan(TestContext t) {
            BlockPos farm = t.origin().add(3, 0, 3);
            List<IBlockState> seen = new ArrayList<>();
            t.then("plant and let it grow in daylight", () -> {
                farmland(t, farm);
                plant(t, farmer(t, "Farmer"), farm);
                seen.addAll(growFully(t, farm.up()));
            });
            t.check("at-least-four-stages", () -> seen.size() >= 4 && seen.stream().allMatch(s -> s.getBlock() == t.block(CROP)), () -> "states from planting to fully grown: " + seen);
        }
    }

    @GameTest(value = "acceptance-s1-bonemeal")
    public static class Bonemeal implements Scenario {
        @Override public void plan(TestContext t) {
            BlockPos farm = t.origin().add(3, 0, 3), control = t.origin().add(8, 0, 3);
            IBlockState[] states = new IBlockState[3];
            t.then("plant two, bonemeal one, grow the other", () -> {
                farmland(t, farm); farmland(t, control);
                Accept.Listener p = farmer(t, "Farmer");
                plant(t, p, farm); plant(t, p, control);
                states[0] = t.state(farm.up());
                bonemeal(t, p, farm.up());
                states[1] = t.state(farm.up());
                for (int i = 0; i < 40 && bonemeal(t, p, farm.up()); i++) { }
                List<IBlockState> grown = growFully(t, control.up());
                states[2] = grown.get(grown.size() - 1);
            });
            t.check("bonemeal-grows-it", () -> states[1] != states[0] && states[1].getBlock() == t.block(CROP), () -> "one bonemeal: " + states[0] + " -> " + states[1]);
            t.check("bonemeal-stops-at-full-size", () -> t.state(farm.up()) == states[2], () -> "after bonemeal until refused: " + t.state(farm.up()) + ", fully grown by itself: " + states[2]);
        }
    }

    @GameTest(value = "acceptance-s1-needs-light")
    public static class NeedsLight implements Scenario {
        @Override public void plan(TestContext t) {
            BlockPos dark = t.origin().add(3, 0, 3), lit = t.origin().add(10, 0, 10);
            IBlockState[] planted = new IBlockState[2];
            t.then("one crop sealed in stone, one in daylight", () -> {
                for (int x = -1; x <= 1; x++) for (int z = -1; z <= 1; z++) for (int y = 1; y <= 3; y++) t.place(dark.add(x, y, z), Blocks.STONE.getDefaultState());
                t.place(dark.up(2), Blocks.AIR.getDefaultState());
                farmland(t, dark); farmland(t, lit);
                t.place(dark.up(), t.block(CROP).getDefaultState());
                t.place(lit.up(), t.block(CROP).getDefaultState());
                planted[0] = t.state(dark.up()); planted[1] = t.state(lit.up());
            });
            t.waitTicks(5);
            t.check("room-is-dark", () -> t.world().getLightFromNeighbors(dark.up(2)) < 9, () -> "light above the dark crop is " + t.world().getLightFromNeighbors(dark.up(2)));
            boolean[] grewInDark = new boolean[1];
            t.then("400 random ticks each", () -> {
                for (int i = 0; i < 400; i++) {
                    IBlockState d = t.state(dark.up());
                    if (d.getBlock() == t.block(CROP)) {
                        d.getBlock().randomTick(t.world(), dark.up(), d, t.world().rand);
                        IBlockState after = t.state(dark.up());
                        if (after.getBlock() == t.block(CROP) && after != planted[0]) grewInDark[0] = true;
                    }
                    IBlockState l = t.state(lit.up()); l.getBlock().randomTick(t.world(), lit.up(), l, t.world().rand);
                }
            });
            // Vanilla crops pop off in the dark (canBlockStay needs light 8 or sky), so "gone" also counts as not growing.
            t.check("no-growth-in-the-dark", () -> !grewInDark[0], () -> "grew in light " + t.world().getLightFromNeighbors(dark.up(2)) + ": " + planted[0] + " -> " + t.state(dark.up()));
            t.check("grows-in-daylight", () -> t.state(lit.up()).getBlock() == t.block(CROP) && t.state(lit.up()) != planted[1], () -> "400 random ticks in daylight left " + t.state(lit.up()));
        }
    }

    @GameTest(value = "acceptance-s1-glows-when-grown")
    public static class Glows implements Scenario {
        @Override public void plan(TestContext t) {
            BlockPos farm = t.origin().add(3, 0, 3);
            int[] light = new int[2];
            t.then("plant", () -> { farmland(t, farm); plant(t, farmer(t, "Farmer"), farm); light[0] = t.state(farm.up()).getLightValue(t.world(), farm.up()); });
            t.check("young-crop-is-dark", () -> light[0] == 0, () -> "a just-planted crop gives light " + light[0]);
            t.then("grow to full size", () -> { growFully(t, farm.up()); light[1] = t.state(farm.up()).getLightValue(t.world(), farm.up()); });
            t.waitTicks(2);
            t.check("grown-crop-glows-dimly", () -> light[1] >= 1 && light[1] <= 13, () -> "fully grown light value " + light[1] + " (a dim torch: 1-13)");
            t.check("light-reaches-the-world", () -> t.world().getLightFor(EnumSkyBlock.BLOCK, farm.up()) >= 1, () -> "block light at the grown crop is " + t.world().getLightFor(EnumSkyBlock.BLOCK, farm.up()));
        }
    }

    @GameTest(value = "acceptance-s1-drops")
    public static class Drops implements Scenario {
        @Override public void plan(TestContext t) {
            List<String> problems = new ArrayList<>();
            t.then("break 8 grown and 4 young crops", () -> {
                Accept.Listener p = farmer(t, "Farmer");
                for (int i = 0; i < 12; i++) {
                    BlockPos farm = t.origin().add(1 + (i % 6) * 2, 0, 2 + (i / 6) * 4);
                    farmland(t, farm);
                    plant(t, p, farm);
                    boolean grown = i < 8;
                    if (grown) growFully(t, farm.up());
                    p.setHeldItem(EnumHand.MAIN_HAND, ItemStack.EMPTY);
                    t.require(t.breakBlock(p, farm.up()), "could not break the crop");
                    List<EntityItem> drops = Accept.items(t, farm.up(), 0.6);
                    int berries = Accept.countEntities(drops, t.item(BERRY)), seeds = Accept.countEntities(drops, t.item(SEEDS));
                    for (EntityItem e : drops) e.setDead();
                    if (grown && (berries < 1 || berries > 3 || seeds < 1 || seeds > 2)) problems.add("grown: " + berries + " berries, " + seeds + " seeds");
                    if (!grown && (berries != 0 || seeds != 1)) problems.add("young: " + berries + " berries, " + seeds + " seeds");
                }
            });
            t.check("grown-1-3-berries-1-2-seeds-young-1-seed", problems::isEmpty, () -> String.join("; ", problems));
        }
    }

    @GameTest(value = "acceptance-s1-eat-night-vision")
    public static class Eat implements Scenario {
        @Override public void plan(TestContext t) {
            Accept.Listener[] p = new Accept.Listener[1];
            int[] food = new int[2];
            t.check("berry-is-small-food", () -> t.item(BERRY) instanceof ItemFood && ((ItemFood) t.item(BERRY)).getHealAmount(t.stack(BERRY, 1)) >= 1
                    && ((ItemFood) t.item(BERRY)).getHealAmount(t.stack(BERRY, 1)) <= 4,
                    () -> t.item(BERRY) instanceof ItemFood ? "heals " + ((ItemFood) t.item(BERRY)).getHealAmount(t.stack(BERRY, 1)) + " (a snack: 1-4)" : BERRY + " is not food");
            t.then("eat one", () -> {
                p[0] = farmer(t, "Eater");
                p[0].getFoodStats().setFoodLevel(10);
                food[0] = p[0].getFoodStats().getFoodLevel();
                p[0].setHeldItem(EnumHand.MAIN_HAND, t.stack(BERRY, 1));
                t.finishUsingItem(p[0], EnumHand.MAIN_HAND);
                food[1] = p[0].getFoodStats().getFoodLevel();
            });
            t.check("fills-hunger", () -> food[1] > food[0], () -> "food level " + food[0] + " -> " + food[1]);
            t.check("few-seconds-night-vision", () -> {
                PotionEffect e = p[0].getActivePotionEffect(MobEffects.NIGHT_VISION);
                return e != null && e.getDuration() >= 40 && e.getDuration() <= 1200;
            }, () -> "night vision after eating: " + p[0].getActivePotionEffect(MobEffects.NIGHT_VISION) + " (want 2-60 s)");
        }
    }

    @GameTest(value = "acceptance-s1-trampled")
    public static class Trampled implements Scenario {
        @Override public void plan(TestContext t) {
            BlockPos farm = t.origin().add(3, 0, 3);
            t.then("plant, then jump on the farmland", () -> {
                farmland(t, farm);
                Accept.Listener p = farmer(t, "Jumper");
                plant(t, p, farm);
                Block farmland = t.state(farm).getBlock();
                farmland.onFallenUpon(t.world(), farm, p, 10.0F);
            });
            t.waitTicks(2);
            t.check("crop-breaks", () -> t.state(farm.up()).getBlock() != t.block(CROP), () -> "the crop is still there on " + t.state(farm));
            t.check("drops-its-seed", () -> Accept.countEntities(Accept.items(t, farm.up(), 1.5), t.item(SEEDS)) >= 1, () -> "no seed item dropped");
        }
    }
}
