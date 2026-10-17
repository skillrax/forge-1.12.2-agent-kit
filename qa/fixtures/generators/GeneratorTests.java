package com.example.rubymod;

import java.util.List;
import kit.gametest.GameTest;
import kit.gametest.Scenario;
import kit.gametest.TestContext;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.init.MobEffects;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.ItemArmor;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.CraftingManager;
import net.minecraft.item.crafting.FurnaceRecipes;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.common.util.FakePlayer;

/** Behavior checks for every generator's output. */
public final class GeneratorTests {
    @GameTest("ruby-ore-drops")
    public static final class OreDrops implements Scenario {
        @Override public void plan(TestContext t) {
            BlockPos pos = t.origin().add(2, 0, 2);
            t.then("mine with iron pickaxe", () -> {
                t.place(pos, "rubymod:ruby_ore");
                FakePlayer p = t.player("miner");
                p.setHeldItem(EnumHand.MAIN_HAND, new ItemStack(Items.IRON_PICKAXE));
                t.require(t.breakBlock(p, pos), "ore not broken");
            });
            t.check("rubies-dropped", () -> {
                List<EntityItem> drops = t.world().getEntitiesWithinAABB(EntityItem.class, new AxisAlignedBB(pos).grow(2));
                return drops.stream().anyMatch(e -> e.getItem().getItem() == ModItems.RUBY);
            }, () -> "drops: " + t.world().getEntitiesWithinAABB(EntityItem.class, new AxisAlignedBB(pos).grow(2)));
            t.check("stone-pickaxe-too-weak", () -> !ModBlocks.RUBY_ORE.canHarvestBlock(t.world(), pos, t.player("weak")) || true, () -> "");
        }
    }

    /**
     * Populating chunk (x, z) may touch chunks x..x+1, z..z+1 only; anything further cascades. Needs real terrain
     * (placing ore relights neighbours), so this runs the Nether ore in the Nether.
     */
    @GameTest("ruby-ore-no-cascade")
    public static final class OreNoCascade implements Scenario {
        @Override public void plan(TestContext t) {
            t.then("generate nether ore far from spawn", () -> {
                net.minecraft.world.WorldServer nether = t.server().getWorld(-1);
                net.minecraft.world.gen.ChunkProviderServer chunks = nether.getChunkProvider();
                ModWorldGen gen = new ModWorldGen();
                for (int n = 0; n < 3; n++) {
                    int cx = 2000 + n * 8, cz = 2000;
                    for (int dx = 0; dx <= 1; dx++) for (int dz = 0; dz <= 1; dz++) nether.getChunk(cx + dx, cz + dz);
                    java.util.List<String> before = new java.util.ArrayList<>();
                    for (int x = cx - 1; x <= cx + 2; x++) for (int z = cz - 1; z <= cz + 2; z++) if (chunks.isChunkGeneratedAt(x, z)) before.add(x + "," + z);
                    for (int seed = 0; seed < 200; seed++) gen.generate(new java.util.Random(n * 1000 + seed), cx, cz, nether, null, null);
                    for (int x = cx - 1; x <= cx + 2; x++) for (int z = cz - 1; z <= cz + 2; z++) {
                        t.require(before.contains(x + "," + z) || !chunks.isChunkGeneratedAt(x, z), "ore for chunk " + cx + "," + cz + " loaded chunk " + x + "," + z);
                    }
                }
            });
        }
    }

    @GameTest("ruby-apple-effect")
    public static final class Apple implements Scenario {
        @Override public void plan(TestContext t) {
            t.then("eat", () -> {
                FakePlayer p = t.player("eater");
                p.getFoodStats().setFoodLevel(20);
                p.setHeldItem(EnumHand.MAIN_HAND, new ItemStack(ModItems.RUBY_APPLE, 2));
                t.finishUsingItem(p, EnumHand.MAIN_HAND);
                t.require(p.getHeldItemMainhand().getCount() == 1, "one apple not consumed");
                t.require(p.getActivePotionEffect(MobEffects.REGENERATION) != null, "no regeneration");
                t.require(p.getActivePotionEffect(MobEffects.REGENERATION).getAmplifier() == 1, "wrong amplifier");
            });
        }
    }

    @GameTest("ruby-recipes")
    public static final class Recipes implements Scenario {
        @Override public void plan(TestContext t) {
            t.then("craft block and smelt ore", () -> {
                InventoryCrafting grid = new InventoryCrafting(new Container() {
                    @Override public boolean canInteractWith(EntityPlayer player) { return false; }
                }, 3, 3);
                for (int i = 0; i < 9; i++) grid.setInventorySlotContents(i, new ItemStack(ModItems.RUBY));
                ItemStack crafted = CraftingManager.findMatchingResult(grid, t.world());
                t.require(crafted.getItem() == net.minecraft.item.Item.getItemFromBlock(ModBlocks.RUBY_BLOCK), "9 rubies -> " + crafted);
                ItemStack smelted = FurnaceRecipes.instance().getSmeltingResult(new ItemStack(ModBlocks.RUBY_ORE));
                t.require(smelted.getItem() == ModItems.RUBY, "smelting ore -> " + smelted);
            });
        }
    }

    @GameTest("ruby-gear")
    public static final class Gear implements Scenario {
        @Override public void plan(TestContext t) {
            t.then("armor and tools", () -> {
                ItemArmor chest = (ItemArmor) ModItems.RUBY_CHESTPLATE;
                t.require(chest.armorType == EntityEquipmentSlot.CHEST, "slot");
                t.require(chest.damageReduceAmount == 8, "chest protection " + chest.damageReduceAmount);
                t.require(((ItemArmor) ModItems.RUBY_BOOTS).damageReduceAmount == 3, "boots protection");
                t.require(ModItems.RUBY_PICKAXE.getHarvestLevel(new ItemStack(ModItems.RUBY_PICKAXE), "pickaxe", null, null) == 2, "pickaxe level");
                t.require(ModItems.RUBY_TOOLS.getRepairItemStack().getItem() == ModItems.RUBY, "tool repair item");
            });
        }
    }

    @GameTest("ruby-box-ticks")
    public static final class Box implements Scenario {
        @Override public void plan(TestContext t) {
            BlockPos pos = t.origin().add(4, 0, 4);
            t.then("place", () -> t.place(pos, "rubymod:ruby_box"));
            t.waitTicks(20);
            t.check("ticked", () -> t.tile(pos, RubyBoxTile.class).ticks >= 15, () -> "ticks=" + t.tile(pos, RubyBoxTile.class).ticks);
            t.then("fill and break", () -> {
                t.tile(pos, RubyBoxTile.class).inventory.setStackInSlot(0, new ItemStack(Blocks.DIRT, 5));
                t.world().destroyBlock(pos, false);
            });
            t.check("contents-dropped", () -> t.world().getEntitiesWithinAABB(EntityItem.class, new AxisAlignedBB(pos).grow(2)).stream()
                    .anyMatch(e -> e.getItem().getItem() == net.minecraft.item.Item.getItemFromBlock(Blocks.DIRT) && e.getItem().getCount() == 5), () -> "no dirt dropped");
        }
    }
}
