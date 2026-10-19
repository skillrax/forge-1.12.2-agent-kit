package acceptance;

import java.util.*;
import kit.gametest.GameTest;
import kit.gametest.Scenario;
import kit.gametest.TestContext;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentData;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.inventory.ContainerRepair;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.Item;
import net.minecraft.item.ItemEnchantedBook;
import net.minecraft.item.ItemStack;
import net.minecraft.util.DamageSource;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;

/** S3 "Gathering and Soulbound enchantments", from the request only. */
public final class S3Acceptance {
    static final String GATHERING = "soulgather:gathering";
    static final String SOULBOUND = "soulgather:soulbound";

    static Enchantment ench(TestContext t, String id) {
        Enchantment e = Enchantment.getEnchantmentByLocation(id);
        t.require(e != null, "no enchantment " + id + " registered");
        return e;
    }
    static ItemStack enchanted(Item item, Enchantment e) { ItemStack s = new ItemStack(item); s.addEnchantment(e, 1); return s; }
    static Accept.Listener player(TestContext t, String name, BlockPos at) {
        Accept.Listener p = new Accept.Listener(t.world(), name);
        p.setLocationAndAngles(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 0, 0);
        return p;
    }
    static final Item[] GATHER_ITEMS = { Items.DIAMOND_PICKAXE, Items.DIAMOND_AXE, Items.DIAMOND_SHOVEL, Items.DIAMOND_SWORD };
    static final Item[] SOUL_ITEMS = { Items.DIAMOND_SWORD, Items.DIAMOND_PICKAXE, Items.IRON_HOE, Items.BOW, Items.FISHING_ROD, Items.SHEARS,
            Items.DIAMOND_HELMET, Items.DIAMOND_CHESTPLATE, Items.DIAMOND_LEGGINGS, Items.DIAMOND_BOOTS };

    @GameTest(value = "acceptance-s3-which-items")
    public static class WhichItems implements Scenario {
        @Override public void plan(TestContext t) {
            List<String> wrong = new ArrayList<>();
            t.check("gathering-on-pickaxe-axe-shovel-sword", () -> {
                Enchantment g = ench(t, GATHERING);
                for (Item item : GATHER_ITEMS) if (!g.canApply(new ItemStack(item)) || !g.canApplyAtEnchantingTable(new ItemStack(item))) wrong.add(item.getRegistryName().toString());
                return wrong.isEmpty();
            }, () -> "Gathering cannot go on (anvil or table): " + wrong);
            t.check("soulbound-on-tools-weapons-armor", () -> {
                wrong.clear();
                Enchantment s = ench(t, SOULBOUND);
                for (Item item : SOUL_ITEMS) if (!s.canApply(new ItemStack(item))) wrong.add(item.getRegistryName().toString());
                return wrong.isEmpty();
            }, () -> "Soulbound cannot go on: " + wrong);
            t.check("soulbound-is-rare", () -> {
                Enchantment.Rarity r = ench(t, SOULBOUND).getRarity();
                return r == Enchantment.Rarity.RARE || r == Enchantment.Rarity.VERY_RARE;
            }, () -> "Soulbound rarity " + ench(t, SOULBOUND).getRarity());
        }
    }

    @GameTest(value = "acceptance-s3-table-and-books")
    public static class TableAndBooks implements Scenario {
        @Override public void plan(TestContext t) {
            List<String> missing = new ArrayList<>();
            t.check("both-rollable-at-the-table-and-on-books", () -> {
                Object[][] cases = { { GATHERING, Items.DIAMOND_PICKAXE }, { GATHERING, Items.BOOK }, { SOULBOUND, Items.DIAMOND_CHESTPLATE }, { SOULBOUND, Items.BOOK } };
                for (Object[] c : cases) {
                    Enchantment e = ench(t, (String) c[0]);
                    boolean rolled = false;
                    for (int level = 1; level <= 50 && !rolled; level++)
                        for (EnchantmentData d : EnchantmentHelper.getEnchantmentDatas(level, new ItemStack((Item) c[1]), false)) rolled |= d.enchantment == e;
                    if (!rolled) missing.add(c[0] + " on " + ((Item) c[1]).getRegistryName());
                }
                return missing.isEmpty();
            }, () -> "the enchanting table (levels 1-50, no treasure) never offers: " + missing);
        }
    }

    @GameTest(value = "acceptance-s3-anvil")
    public static class Anvil implements Scenario {
        @Override public void plan(TestContext t) {
            List<String> failed = new ArrayList<>();
            t.check("books-combine-on-an-anvil", () -> {
                Object[][] cases = { { GATHERING, Items.DIAMOND_PICKAXE }, { SOULBOUND, Items.DIAMOND_CHESTPLATE } };
                for (Object[] c : cases) {
                    Enchantment e = ench(t, (String) c[0]);
                    Accept.Listener p = player(t, "Smith", t.origin());
                    p.experienceLevel = 100;
                    ContainerRepair anvil = new ContainerRepair(p.inventory, t.world(), t.origin(), p);
                    anvil.getSlot(0).putStack(new ItemStack((Item) c[1]));
                    anvil.getSlot(1).putStack(ItemEnchantedBook.getEnchantedItemStack(new EnchantmentData(e, 1)));
                    anvil.updateRepairOutput();
                    ItemStack out = anvil.getSlot(2).getStack();
                    if (EnchantmentHelper.getEnchantmentLevel(e, out) < 1) failed.add(c[0] + " book + " + ((Item) c[1]).getRegistryName() + " -> " + Accept.describe(out));
                }
                return failed.isEmpty();
            }, () -> String.join("; ", failed));
        }
    }

    @GameTest(value = "acceptance-s3-gathering-blocks")
    public static class GatheringBlocks implements Scenario {
        @Override public void plan(TestContext t) {
            BlockPos a = t.origin().add(3, 0, 3), b = t.origin().add(9, 0, 9);
            Accept.Listener[] p = new Accept.Listener[1];
            int[] counts = new int[3];
            t.then("mine stone with a Gathering pickaxe", () -> {
                p[0] = player(t, "Miner", t.origin().add(6, 0, 6));
                p[0].setHeldItem(EnumHand.MAIN_HAND, enchanted(Items.DIAMOND_PICKAXE, ench(t, GATHERING)));
                t.place(a, Blocks.STONE.getDefaultState());
                t.require(t.breakBlock(p[0], a), "could not mine the stone");
                counts[0] = Accept.countInventory(p[0], Item.getItemFromBlock(Blocks.COBBLESTONE));
                counts[1] = Accept.countEntities(Accept.items(t, a, 1.5), Item.getItemFromBlock(Blocks.COBBLESTONE));
            });
            t.check("drop-goes-to-inventory", () -> counts[0] == 1 && counts[1] == 0, () -> "cobblestone in inventory " + counts[0] + ", on the ground " + counts[1]);
            t.then("fill the inventory, mine again", () -> {
                for (int slot = 1; slot < p[0].inventory.mainInventory.size(); slot++) p[0].inventory.mainInventory.set(slot, new ItemStack(Blocks.DIRT, 64));
                t.place(b, Blocks.STONE.getDefaultState());
                t.require(t.breakBlock(p[0], b), "could not mine the second stone");
                counts[2] = Accept.countEntities(Accept.items(t, b, 1.5), Item.getItemFromBlock(Blocks.COBBLESTONE));
            });
            t.check("full-inventory-drops-normally", () -> counts[2] == 1, () -> "with a full inventory, cobblestone on the ground: " + counts[2]);
        }
    }

    @GameTest(value = "acceptance-s3-gathering-kills")
    public static class GatheringKills implements Scenario {
        @Override public void plan(TestContext t) {
            BlockPos at = t.origin().add(8, 0, 8);
            Accept.Listener[] p = new Accept.Listener[1];
            int[] counts = new int[2];
            t.then("kill a cow with a Gathering sword", () -> {
                p[0] = player(t, "Hunter", t.origin().add(6, 0, 8));
                p[0].setHeldItem(EnumHand.MAIN_HAND, enchanted(Items.DIAMOND_SWORD, ench(t, GATHERING)));
                Entity cow = t.spawn("minecraft:cow", at);
                cow.attackEntityFrom(DamageSource.causePlayerDamage(p[0]), 1000.0F);
                t.require(!((EntityLivingBase) cow).isEntityAlive(), "the cow survived");
                counts[0] = Accept.countInventory(p[0], Items.BEEF) + Accept.countInventory(p[0], Items.COOKED_BEEF);
                counts[1] = Accept.countEntities(Accept.items(t, at, 3), Items.BEEF) + Accept.countEntities(Accept.items(t, at, 3), Items.COOKED_BEEF);
            });
            t.check("kill-drops-go-to-inventory", () -> counts[0] >= 1 && counts[1] == 0, () -> "beef in inventory " + counts[0] + ", on the ground " + counts[1]);
        }
    }

    @GameTest(value = "acceptance-s3-soulbound-death")
    public static class SoulboundDeath implements Scenario {
        @Override public void plan(TestContext t) {
            List<EntityItem> landed = new ArrayList<>();
            Accept.Listener[] p = new Accept.Listener[2];
            t.then("die with a soulbound sword and chestplate and plain dirt", () -> {
                Enchantment s = ench(t, SOULBOUND);
                p[0] = player(t, "Fallen", t.origin().add(4, 0, 4));
                p[0].inventory.mainInventory.set(0, enchanted(Items.DIAMOND_SWORD, s));
                p[0].inventory.mainInventory.set(5, new ItemStack(Blocks.DIRT, 32));
                p[0].inventory.armorInventory.set(EntityEquipmentSlot.CHEST.getIndex(), enchanted(Items.DIAMOND_CHESTPLATE, s));
                t.world().getGameRules().setOrCreateGameRule("keepInventory", "false");
                landed.addAll(Accept.die(p[0], DamageSource.LAVA));
                p[1] = player(t, "Fallen", t.origin().add(8, 0, 8));   // same name: same UUID
                Accept.respawn(p[0], p[1], false);
            });
            t.check("soulbound-items-back-once", () -> Accept.countInventory(p[1], Items.DIAMOND_SWORD) == 1 && Accept.countInventory(p[1], Items.DIAMOND_CHESTPLATE) == 1
                    && Accept.countEntities(landed, Items.DIAMOND_SWORD) == 0 && Accept.countEntities(landed, Items.DIAMOND_CHESTPLATE) == 0,
                    () -> "after respawn: sword " + Accept.countInventory(p[1], Items.DIAMOND_SWORD) + ", chestplate " + Accept.countInventory(p[1], Items.DIAMOND_CHESTPLATE)
                            + "; dropped: sword " + Accept.countEntities(landed, Items.DIAMOND_SWORD) + ", chestplate " + Accept.countEntities(landed, Items.DIAMOND_CHESTPLATE));
            t.check("everything-else-drops", () -> Accept.countEntities(landed, Item.getItemFromBlock(Blocks.DIRT)) == 32 && Accept.countInventory(p[1], Item.getItemFromBlock(Blocks.DIRT)) == 0,
                    () -> "dirt dropped " + Accept.countEntities(landed, Item.getItemFromBlock(Blocks.DIRT)) + ", kept " + Accept.countInventory(p[1], Item.getItemFromBlock(Blocks.DIRT)));
        }
    }

    @GameTest(value = "acceptance-s3-soulbound-keepinventory")
    public static class SoulboundKeepInventory implements Scenario {
        @Override public void plan(TestContext t) {
            Accept.Listener[] p = new Accept.Listener[2];
            List<EntityItem> landed = new ArrayList<>();
            t.then("die with keepInventory on", () -> {
                Enchantment s = ench(t, SOULBOUND);
                p[0] = player(t, "Keeper", t.origin().add(4, 0, 4));
                p[0].inventory.mainInventory.set(0, enchanted(Items.DIAMOND_SWORD, s));
                p[0].inventory.mainInventory.set(5, new ItemStack(Blocks.DIRT, 32));
                t.world().getGameRules().setOrCreateGameRule("keepInventory", "true");
                try {
                    landed.addAll(Accept.die(p[0], DamageSource.LAVA));
                    p[1] = player(t, "Keeper", t.origin().add(8, 0, 8));
                    Accept.respawn(p[0], p[1], false);
                } finally { t.world().getGameRules().setOrCreateGameRule("keepInventory", "false"); }
            });
            t.check("no-duplicate", () -> Accept.countInventory(p[1], Items.DIAMOND_SWORD) == 1 && Accept.countInventory(p[1], Item.getItemFromBlock(Blocks.DIRT)) == 32 && landed.isEmpty(),
                    () -> "after respawn: swords " + Accept.countInventory(p[1], Items.DIAMOND_SWORD) + ", dirt " + Accept.countInventory(p[1], Item.getItemFromBlock(Blocks.DIRT)) + ", dropped " + landed.size());
        }
    }
}
