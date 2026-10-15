package com.example.rubymod;

import java.io.File;
import kit.gametest.GameTest;
import kit.gametest.Scenario;
import kit.gametest.TestContext;
import net.minecraft.block.BlockCrops;
import net.minecraft.block.BlockHorizontal;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.projectile.EntitySmallFireball;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.DamageSource;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.storage.loot.LootTable;
import net.minecraftforge.common.util.FakePlayer;

/** Behavior checks for the guide samples. */
public final class SnippetTests {
    @GameTest("wand-fires")
    public static final class Wand implements Scenario {
        @Override public void plan(TestContext t) {
            t.then("use", () -> {
                FakePlayer p = t.player("wizard");
                p.setHeldItem(EnumHand.MAIN_HAND, new ItemStack(ModItems.WAND));
                t.require(t.useItem(p, EnumHand.MAIN_HAND) == EnumActionResult.SUCCESS, "use failed");
                ItemStack wand = p.getHeldItemMainhand();
                t.require(wand.getItemDamage() == 1, "durability " + wand.getItemDamage());
                t.require(wand.getTagCompound().getInteger("Shots") == 1, "shot counter");
                t.require(!t.world().getEntitiesWithinAABB(EntitySmallFireball.class, new AxisAlignedBB(t.origin()).grow(4)).isEmpty(), "no fireball");
            });
        }
    }

    @GameTest("facing-block-placement")
    public static final class Facing implements Scenario {
        @Override public void plan(TestContext t) {
            BlockPos ground = t.origin().add(3, -1, 3);
            t.then("place facing south-looking player", () -> {
                FakePlayer p = t.player("builder");      // yaw 0 = looking south
                p.setHeldItem(EnumHand.MAIN_HAND, new ItemStack(ModBlocks.FACING_BLOCK));
                t.require(t.useOnBlock(p, ground, EnumFacing.UP, EnumHand.MAIN_HAND) == EnumActionResult.SUCCESS, "placement failed");
                t.require(t.state(ground.up()).getValue(BlockHorizontal.FACING) == EnumFacing.NORTH, "facing " + t.state(ground.up()));
            });
        }
    }

    @GameTest("crop-grows-and-drops")
    public static final class Crop implements Scenario {
        @Override public void plan(TestContext t) {
            BlockPos soil = t.origin().add(5, -1, 5);
            t.then("plant on farmland", () -> {
                t.place(soil, Blocks.FARMLAND.getDefaultState());
                t.place(soil.up(2), Blocks.AIR.getDefaultState());
                FakePlayer p = t.player("farmer");
                p.setHeldItem(EnumHand.MAIN_HAND, new ItemStack(ModItems.RUBY_SEEDS));
                t.require(t.useOnBlock(p, soil, EnumFacing.UP, EnumHand.MAIN_HAND) == EnumActionResult.SUCCESS, "planting failed");
                t.require(t.state(soil.up()).getBlock() == ModBlocks.RUBY_CROP, "no crop");
                t.place(soil.up(), ((BlockCrops) ModBlocks.RUBY_CROP).withAge(7));
            });
            t.waitTicks(2);
            t.check("still-planted", () -> t.state(soil.up()).getBlock() == ModBlocks.RUBY_CROP, () -> "crop popped off: " + t.state(soil.up()));
            t.then("harvest", () -> t.world().destroyBlock(soil.up(), true));
            t.check("rubies-dropped", () -> t.world().getEntitiesWithinAABB(EntityItem.class, new AxisAlignedBB(soil).grow(2)).stream()
                    .anyMatch(e -> e.getItem().getItem() == ModItems.RUBY), () -> "no ruby");
        }
    }

    @GameTest("zombie-entity")
    public static final class Zombie implements Scenario {
        @Override public void plan(TestContext t) {
            t.then("spawn", () -> {
                RubyZombie zombie = (RubyZombie) t.spawn("rubymod:ruby_zombie", t.origin().add(8, 0, 8));
                t.require(zombie.getMaxHealth() == 40.0F, "health " + zombie.getMaxHealth());
                LootTable table = t.world().getLootTableManager().getLootTableFromLocation(RubyZombie.LOOT);
                t.require(table != LootTable.EMPTY_LOOT_TABLE, "loot table missing");
            });
        }
    }

    @GameTest("enchant-and-potion")
    public static final class Enchant implements Scenario {
        private RubyZombie plain, enchanted;
        private net.minecraft.entity.EntityLivingBase cow;
        @Override public void plan(TestContext t) {
            t.then("hit two zombies", () -> {
                plain = (RubyZombie) t.spawn("rubymod:ruby_zombie", t.origin().add(2, 0, 10));
                enchanted = (RubyZombie) t.spawn("rubymod:ruby_zombie", t.origin().add(6, 0, 10));
                FakePlayer p = t.player("fighter");
                p.setHeldItem(EnumHand.MAIN_HAND, new ItemStack(Items.IRON_SWORD));
                t.attack(p, plain);
                ItemStack sword = new ItemStack(Items.IRON_SWORD);
                sword.addEnchantment(ModEnchantments.RUBY_EDGE, 3);
                p.setHeldItem(EnumHand.MAIN_HAND, sword);
                t.require(EnchantmentHelper.getMaxEnchantmentLevel(ModEnchantments.RUBY_EDGE, p) == 3, "enchant not seen");
                t.attack(p, enchanted);
                float extra = plain.getHealth() - enchanted.getHealth();
                t.require(extra > 4.0F && extra < 5.0F, "extra damage " + extra);
            });
            t.then("potion on a cow", () -> {
                cow = (net.minecraft.entity.EntityLivingBase) t.spawn("minecraft:cow", t.origin().add(10, 0, 10));
                cow.setHealth(4.0F);
                cow.addPotionEffect(new PotionEffect(ModEnchantments.SHINE, 200, 0));
            });
            t.waitTicks(45);
            t.check("shine-heals", () -> cow.getHealth() >= 5.0F, () -> "health " + cow.getHealth());
        }
    }

    @GameTest("command-config-gui")
    public static final class Misc implements Scenario {
        @Override public void plan(TestContext t) {
            BlockPos box = t.origin().add(10, 0, 2);
            t.then("command", () -> {
                FakePlayer p = t.player("admin");
                boolean usage = false;
                try { new RubyCommand().execute(t.server(), p, new String[0]); } catch (net.minecraft.command.WrongUsageException e) { usage = true; }
                t.require(usage, "missing argument accepted");
                t.require(new RubyCommand().getRequiredPermissionLevel() == 2, "permission");
                t.require(t.server().getCommandManager().getCommands().containsKey("rubies"), "command not registered");
            });
            t.then("config", () -> {
                t.require(new File(t.server().getDataDirectory(), "config/rubymod.cfg").isFile(), "no config file");
                t.require(ModConfig.zombieWeight == 20, "config default");
            });
            t.then("container shift-click", () -> {
                t.place(box, "rubymod:ruby_box");
                FakePlayer p = t.player("user");
                p.inventory.setInventorySlotContents(0, new ItemStack(ModItems.RUBY, 10));
                RubyBoxContainer menu = new RubyBoxContainer(p.inventory, t.tile(box, RubyBoxTile.class));
                t.require(menu.inventorySlots.size() == 45, "slots " + menu.inventorySlots.size());
                menu.transferStackInSlot(p, 36);   // hotbar slot 0 is container slot 36
                t.require(t.tile(box, RubyBoxTile.class).inventory.getStackInSlot(0).getCount() == 10, "not moved");
            });
        }
    }

    @GameTest("ticks-after-300")
    public static final class Late implements Scenario {
        @Override public void plan(TestContext t) {
            BlockPos pos = t.origin().add(12, 0, 12);
            t.then("place", () -> t.place(pos, "rubymod:ruby_box"));
            t.waitTicks(330);
            t.check("still-ticking", () -> t.tile(pos, RubyBoxTile.class).ticks >= 320, () -> "ticks=" + t.tile(pos, RubyBoxTile.class).ticks);
        }
    }
}
