package acceptance;

import java.util.*;
import kit.gametest.GameTest;
import kit.gametest.Scenario;
import kit.gametest.TestContext;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityList;
import net.minecraft.entity.ai.EntityAISit;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.passive.EntityWolf;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.DamageSource;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.fml.relauncher.ReflectionHelper;

/**
 * S5 "Fetching wolves", from the request only (mod id wolffetch; nothing of its own is registered). The owner is
 * a TestContext.targetPlayer, the kind that is in the world's player list, so a wolf's getOwner() finds it.
 */
public final class S5Acceptance {
    static FakePlayer owner(TestContext t, String name, BlockPos at) {
        FakePlayer p = t.targetPlayer(name);
        p.setLocationAndAngles(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 0, 0);
        return p;
    }
    static EntityWolf tamedWolf(TestContext t, FakePlayer owner, BlockPos at) {
        EntityWolf wolf = (EntityWolf) t.spawn("minecraft:wolf", at);
        wolf.setTamed(true);
        wolf.setOwnerId(owner.getUniqueID());
        wolf.setHealth(wolf.getMaxHealth());
        return wolf;
    }
    /** Right-click with whatever is set in the main hand: the real interaction path (Forge EntityInteract, then the wolf). */
    static void rightClick(FakePlayer p, Entity wolf, ItemStack held) {
        p.setHeldItem(EnumHand.MAIN_HAND, held);
        p.interactOn(wolf, EnumHand.MAIN_HAND);
    }
    static EntityItem drop(TestContext t, BlockPos at, Item item) {
        EntityItem e = new EntityItem(t.world(), at.getX() + 0.5, at.getY(), at.getZ() + 0.5, new ItemStack(item));
        e.motionX = e.motionY = e.motionZ = 0;
        e.setNoPickupDelay();
        t.world().spawnEntity(e);
        return e;
    }
    /** At the owner's feet (within 2.5 blocks) or already in the owner's inventory. */
    static boolean delivered(TestContext t, FakePlayer owner, Item item) {
        if (Accept.countInventory(owner, item) > 0) return true;
        for (EntityItem e : t.world().getEntitiesWithinAABB(EntityItem.class, new AxisAlignedBB(owner.posX - 2.5, owner.posY - 1, owner.posZ - 2.5, owner.posX + 2.5, owner.posY + 2, owner.posZ + 2.5)))
            if (!e.isDead && e.getItem().getItem() == item) return true;
        return false;
    }
    /** An item entity in this test's own area (grown by 3; neighbouring areas start 4 blocks away and may still hold their items). */
    static boolean onGround(TestContext t, Item item) {
        BlockPos o = t.origin();
        for (EntityItem e : t.world().getEntitiesWithinAABB(EntityItem.class, new AxisAlignedBB(o.add(-3, -2, -3), o.add(19, 8, 19))))
            if (!e.isDead && e.getItem().getItem() == item) return true;
        return false;
    }
    /**
     * Whether the wolf has been told to sit: EntityAISit's own flag, which processInteract flips at once (isSitting()
     * follows only when that AI task next runs and the wolf is on the ground).
     */
    static boolean toldToSit(EntityWolf wolf) {
        return ReflectionHelper.<Boolean, EntityAISit>getPrivateValue(EntityAISit.class, wolf.getAISit(), "isSitting", "field_75271_b");
    }
    static boolean unmoved(EntityItem e, BlockPos at) { return !e.isDead && e.getDistanceSq(at.getX() + 0.5, at.getY(), at.getZ() + 0.5) < 1.0; }
    static String where(EntityItem e) { return e.isDead ? "picked up" : String.format(Locale.ROOT, "at %.1f %.1f %.1f", e.posX, e.posY, e.posZ); }

    @GameTest(value = "acceptance-s5-stick-toggles-mode")
    public static class Toggle implements Scenario {
        @Override public void plan(TestContext t) {
            FakePlayer[] o = new FakePlayer[1];
            EntityWolf[] w = new EntityWolf[2];
            List<String> said = new ArrayList<>();
            t.then("owner, a standing wolf and a sitting wolf", () -> {
                o[0] = owner(t, "ToggleOwner", t.origin().add(4, 0, 4));
                w[0] = tamedWolf(t, o[0], t.origin().add(6, 0, 4));
                w[1] = tamedWolf(t, o[0], t.origin().add(4, 0, 6));
                w[1].getAISit().setSitting(true); w[1].setSitting(true);
            });
            t.waitTicks(2);
            t.then("stick right-click on the standing wolf", () -> {
                int before = t.messages(o[0]).size();
                rightClick(o[0], w[0], new ItemStack(Items.STICK));
                said.add(t.messages(o[0]).size() > before ? t.messages(o[0]).get(t.messages(o[0]).size() - 1) : null);
            });
            t.waitTicks(10);
            t.check("first-click-does-not-sit-it", () -> !toldToSit(w[0]), () -> "the first stick click made the wolf sit");
            t.then("stick right-click again", () -> {
                int before = t.messages(o[0]).size();
                rightClick(o[0], w[0], new ItemStack(Items.STICK));
                said.add(t.messages(o[0]).size() > before ? t.messages(o[0]).get(t.messages(o[0]).size() - 1) : null);
            });
            t.waitTicks(10);
            t.check("tells-the-mode-each-time", () -> said.get(0) != null && said.get(1) != null && !said.get(0).equals(said.get(1)), () -> "messages after each click: " + said);
            t.check("standing-wolf-stays-standing", () -> !toldToSit(w[0]), () -> "the wolf sat down");
            t.then("stick right-click on the sitting wolf", () -> rightClick(o[0], w[1], new ItemStack(Items.STICK)));
            t.waitTicks(10);
            t.check("sitting-wolf-stays-sitting", () -> toldToSit(w[1]), () -> "the wolf stood up");
        }
    }

    @GameTest(value = "acceptance-s5-fetches-items", maxTicks = 2400)
    public static class Fetch implements Scenario {
        @Override public void plan(TestContext t) {
            FakePlayer[] o = new FakePlayer[1];
            EntityItem[] items = new EntityItem[2];
            t.then("fetch mode on, two items on the ground", () -> {
                BlockPos me = t.origin().add(3, 0, 3);
                o[0] = owner(t, "FetchOwner", me);
                EntityWolf wolf = tamedWolf(t, o[0], me.add(1, 0, 0));
                rightClick(o[0], wolf, new ItemStack(Items.STICK));
                items[0] = drop(t, me.add(8, 0, 5), Items.DIAMOND);
                items[1] = drop(t, me.add(4, 0, 9), Items.EMERALD);
            });
            t.waitUntil("both-brought-to-my-feet", 2000, () -> delivered(t, o[0], Items.DIAMOND) && delivered(t, o[0], Items.EMERALD),
                    () -> "diamond " + (delivered(t, o[0], Items.DIAMOND) ? "delivered" : where(items[0])) + ", emerald " + (delivered(t, o[0], Items.EMERALD) ? "delivered" : where(items[1])));
        }
    }

    @GameTest(value = "acceptance-s5-no-fetch-while-sitting")
    public static class Sitting implements Scenario {
        @Override public void plan(TestContext t) {
            EntityItem[] item = new EntityItem[1];
            BlockPos spot = t.origin().add(9, 0, 9);
            FakePlayer[] o = new FakePlayer[1];
            EntityWolf[] w = new EntityWolf[1];
            t.then("fetch mode on, then sit the wolf", () -> {
                BlockPos me = t.origin().add(3, 0, 3);
                o[0] = owner(t, "SitOwner", me);
                w[0] = tamedWolf(t, o[0], me.add(1, 0, 0));
                rightClick(o[0], w[0], new ItemStack(Items.STICK));
                rightClick(o[0], w[0], ItemStack.EMPTY);
            });
            t.waitTicks(10);
            t.then("drop an item", () -> {
                t.require(toldToSit(w[0]), "an empty-hand right-click did not sit the wolf");
                item[0] = drop(t, spot, Items.NETHER_STAR);
            });
            t.waitTicks(400);
            t.check("item-left-alone", () -> unmoved(item[0], spot), () -> "the item was " + where(item[0]));
        }
    }

    @GameTest(value = "acceptance-s5-strangers-and-wild-wolves")
    public static class Strangers implements Scenario {
        @Override public void plan(TestContext t) {
            FakePlayer[] p = new FakePlayer[2];
            EntityWolf[] w = new EntityWolf[2];
            EntityItem[] item = new EntityItem[1];
            BlockPos spot = t.origin().add(8, 0, 4);
            t.then("a stranger sticks my wolf, I stick a wild wolf", () -> {
                p[0] = owner(t, "StrangerTestOwner", t.origin().add(3, 0, 3));
                p[1] = owner(t, "StrangerTestStranger", t.origin().add(3, 0, 10));
                w[0] = tamedWolf(t, p[0], t.origin().add(4, 0, 4));
                w[1] = (EntityWolf) t.spawn("minecraft:wolf", t.origin().add(4, 0, 11));
                rightClick(p[1], w[0], new ItemStack(Items.STICK));
                rightClick(p[0], w[1], new ItemStack(Items.STICK));
                item[0] = drop(t, spot, Items.APPLE);
            });
            t.check("nobody-told-anything", () -> t.messages(p[0]).isEmpty() && t.messages(p[1]).isEmpty(), () -> "owner got " + t.messages(p[0]) + ", stranger got " + t.messages(p[1]));
            t.check("wild-wolf-unchanged", () -> !w[1].isTamed() && !w[1].isSitting(), () -> "wild wolf tamed " + w[1].isTamed() + ", sitting " + w[1].isSitting());
            t.waitTicks(400);
            t.check("no-fetching", () -> unmoved(item[0], spot), () -> "the item was " + where(item[0]));
        }
    }

    @GameTest(value = "acceptance-s5-ignores-far-items")
    public static class FarItems implements Scenario {
        @Override public void plan(TestContext t) {
            EntityItem[] item = new EntityItem[1];
            BlockPos me = t.origin().add(0, 0, 8), spot = me.add(15, 0, 0);
            // 15 blocks from the owner in a straight line (outside a 12 radius or a 12 box), 12 from the wolf (vanilla paths reach 16).
            t.then("fetch mode on, an item 15 blocks from me", () -> {
                FakePlayer o = owner(t, "FarOwner", me);
                rightClick(o, tamedWolf(t, o, me.add(3, 0, 0)), new ItemStack(Items.STICK));
                item[0] = drop(t, spot, Items.GOLD_INGOT);
            });
            t.waitTicks(400);
            t.check("far-item-left-alone", () -> unmoved(item[0], spot), () -> "the item 15 blocks away was " + where(item[0]));
        }
    }

    /** Fetch mode on and an item 9 blocks away; waits until the wolf has picked it up. Each test drops its own item type: wolves may also fetch what earlier tests left nearby. */
    static void untilCarrying(TestContext t, String ownerName, Item item, FakePlayer[] o, EntityWolf[] w) {
        t.then("fetch mode on, an item 9 blocks away", () -> {
            BlockPos me = t.origin().add(2, 0, 2);
            o[0] = owner(t, ownerName, me);
            w[0] = tamedWolf(t, o[0], me.add(1, 0, 0));
            rightClick(o[0], w[0], new ItemStack(Items.STICK));
            drop(t, me.add(9, 0, 3), item);
        });
        t.waitUntil("wolf-picks-it-up", 1200, () -> !onGround(t, item) && Accept.countInventory(o[0], item) == 0, () -> "the item is still on the ground");
    }

    @GameTest(value = "acceptance-s5-drops-carried-item-on-death", maxTicks = 1600)
    public static class DeathDrop implements Scenario {
        @Override public void plan(TestContext t) {
            FakePlayer[] o = new FakePlayer[1];
            EntityWolf[] w = new EntityWolf[1];
            untilCarrying(t, "DeathOwner", Items.DIAMOND, o, w);
            t.then("kill the carrying wolf", () -> w[0].attackEntityFrom(DamageSource.OUT_OF_WORLD, 1000.0F));
            t.waitTicks(2);
            t.check("carried-item-dropped", () -> onGround(t, Items.DIAMOND) || Accept.countInventory(o[0], Items.DIAMOND) > 0, () -> "the diamond is gone");
        }
    }

    @GameTest(value = "acceptance-s5-keeps-carried-item-on-reload", maxTicks = 1600)
    public static class Reload implements Scenario {
        @Override public void plan(TestContext t) {
            FakePlayer[] o = new FakePlayer[1];
            EntityWolf[] w = new EntityWolf[2];
            NBTTagCompound tag = new NBTTagCompound();
            untilCarrying(t, "ReloadOwner", Items.BLAZE_ROD, o, w);
            t.then("save and unload the wolf", () -> { t.require(w[0].writeToNBTAtomically(tag), "wolf not saved"); w[0].setDead(); });
            t.waitTicks(2);
            t.then("load it back", () -> {
                Entity copy = EntityList.createEntityFromNBT(tag, t.world());
                t.require(copy instanceof EntityWolf && t.world().spawnEntity(copy), "could not load the wolf back");
                w[1] = (EntityWolf) copy;
            });
            t.waitTicks(10);
            t.then("kill the reloaded wolf", () -> w[1].attackEntityFrom(DamageSource.OUT_OF_WORLD, 1000.0F));
            t.waitTicks(2);
            t.check("reloaded-wolf-still-had-it", () -> onGround(t, Items.BLAZE_ROD) || Accept.countInventory(o[0], Items.BLAZE_ROD) > 0,
                    () -> "after reloading and killing the wolf the blaze rod is gone (it brings it back or drops it on death if it kept it)");
        }
    }
}
