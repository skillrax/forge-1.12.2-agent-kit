package com.example.rubymod;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import kit.gametest.GameTest;
import kit.gametest.Scenario;
import kit.gametest.TestContext;
import net.minecraft.block.BlockCrops;
import net.minecraft.block.BlockHopper;
import net.minecraft.block.BlockHorizontal;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.projectile.EntitySmallFireball;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.energy.CapabilityEnergy;
import net.minecraftforge.energy.IEnergyStorage;
import net.minecraftforge.fluids.FluidTank;
import net.minecraftforge.items.ItemStackHandler;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.DamageSource;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.storage.loot.LootTable;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextComponentTranslation;
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
    @GameTest("recipes-machines-player-data")
    public static final class Fragments implements Scenario {
        @Override public void plan(TestContext t) {
            t.then("recipes by hand (guides/recipes.md)", () -> {
                ItemStack block = t.craft("rubymod:ruby", "rubymod:ruby", "rubymod:ruby", "rubymod:ruby", "rubymod:ruby", "rubymod:ruby", "rubymod:ruby", "rubymod:ruby", "rubymod:ruby");
                t.require(block.getItem() == t.item("rubymod:ruby_block"), "9 rubies gave " + block);
                t.require(t.smelt("rubymod:ruby_ore").getItem() == t.item("rubymod:ruby"), "smelting");
            });
            t.then("craftOnce uses the registered recipe", () -> {
                InventoryCrafting grid = new InventoryCrafting(new Container() {
                    @Override public boolean canInteractWith(EntityPlayer player) { return false; }
                }, 3, 3);
                grid.setInventorySlotContents(4, new ItemStack(Blocks.LOG));
                List<ItemStack> returns = new ArrayList<>();
                ItemStack planks = MachineHelpers.craftOnce(grid, t.world(), returns);
                t.require(planks.getItem() == Item.getItemFromBlock(Blocks.PLANKS) && planks.getCount() == 4, "log gave " + planks);
                t.require(returns.size() == 9, "remaining items " + returns.size());
                grid.setInventorySlotContents(0, new ItemStack(Items.STICK));
                t.require(MachineHelpers.craftOnce(grid, t.world(), new ArrayList<>()).isEmpty(), "log + stick matched a recipe");
            });
            t.then("push moves only what fits", () -> {
                ItemStackHandler buffer = new ItemStackHandler(1), target = new ItemStackHandler(1);
                buffer.setStackInSlot(0, new ItemStack(Blocks.COBBLESTONE, 64));
                target.setStackInSlot(0, new ItemStack(Blocks.COBBLESTONE, 60));
                int moved = MachineHelpers.push(buffer, 0, target, 8);
                t.require(moved == 4 && buffer.getStackInSlot(0).getCount() == 60 && target.getStackInSlot(0).getCount() == 64,
                        "moved " + moved + ", buffer " + buffer.getStackInSlot(0) + ", target " + target.getStackInSlot(0));
            });
            t.then("tank NBT with a stale Empty marker", () -> {
                NBTTagCompound tankTag = new NBTTagCompound();
                tankTag.setString("Empty", "");
                tankTag.setString("FluidName", "water");
                tankTag.setInteger("Amount", 1000);
                NBTTagCompound nbt = new NBTTagCompound();
                nbt.setTag("Tank", tankTag);
                FluidTank plain = new FluidTank(4000);
                plain.readFromNBT(tankTag.copy());
                t.require(plain.getFluid() == null, "trap gone? plain read kept " + plain.getFluidAmount());
                FluidTank tank = new FluidTank(4000);
                MachineHelpers.readTank(tank, nbt);
                t.require(tank.getFluidAmount() == 1000, "readTank lost the fluid: " + tank.getFluidAmount());
            });
            t.then("filtered slot rejects other items; isItemValid alone does not", () -> {
                BlockPos pos = t.origin().add(6, 0, 12);
                t.place(pos, "rubymod:ruby_box");
                ItemStackHandler fuel = t.tile(pos, RubyBoxTile.class).fuel;
                t.require(fuel.insertItem(0, new ItemStack(Blocks.STONE), false).getCount() == 1 && fuel.getStackInSlot(0).isEmpty(), "stone got in");
                t.require(fuel.insertItem(0, new ItemStack(Items.COAL, 3), false).isEmpty() && fuel.getStackInSlot(0).getCount() == 3, "coal refused");
                ItemStackHandler naive = new ItemStackHandler(1) {
                    @Override public boolean isItemValid(int slot, ItemStack stack) { return false; }
                };
                t.require(naive.insertItem(0, new ItemStack(Blocks.STONE), false).isEmpty(), "trap gone? insertItem now checks isItemValid");
            });
            t.then("energy capability", () -> {
                BlockPos pos = t.origin().add(2, 0, 12);
                t.place(pos, "rubymod:ruby_box");
                IEnergyStorage energy = t.tile(pos, RubyBoxTile.class).getCapability(CapabilityEnergy.ENERGY, null);
                t.require(energy != null && energy.receiveEnergy(500, false) == 100, "energy " + energy);
            });
            t.then("persisted player data survives respawn copy", () -> {
                FakePlayer before = t.player("keeper"), after = t.player("keeper-respawned");
                NBTTagCompound state = new NBTTagCompound();
                state.setInteger("Rubies", 5);
                PlayerData.save(before, state);
                t.require(PlayerData.data(before).getInteger("Rubies") == 5, "not saved");
                after.copyFrom(before, false);   // what a respawn does
                t.require(PlayerData.data(after).getInteger("Rubies") == 5, "lost on respawn: " + after.getEntityData());
            });
            t.then("wand bobs only for non-counter changes", () -> {
                ItemStack a = new ItemStack(ModItems.WAND), b = a.copy();
                NBTTagCompound tag = new NBTTagCompound();
                tag.setInteger("Shots", 3);
                b.setTagCompound(tag);
                t.require(!ModItems.WAND.shouldCauseReequipAnimation(a, b, false), "Shots change re-equips");
                b.setItemDamage(1);
                t.require(ModItems.WAND.shouldCauseReequipAnimation(a, b, false), "damage change ignored");
            });
        }
    }
    /** TESTING.md facts: hoppers tick in the test area and pull through the item capability; a powered hopper is disabled. */
    @GameTest("hopper-under-tile")
    public static final class Hopper implements Scenario {
        @Override public void plan(TestContext t) {
            BlockPos box = t.origin().add(2, 1, 2), hopper = box.down();
            BlockPos lockedBox = t.origin().add(8, 1, 2), lockedHopper = lockedBox.down();
            t.then("boxes with rubies above hoppers, the second hopper next to a redstone block", () -> {
                t.place(box, "rubymod:ruby_box");
                t.place(lockedBox, "rubymod:ruby_box");
                t.tile(box, RubyBoxTile.class).inventory.setStackInSlot(0, new ItemStack(ModItems.RUBY, 2));
                t.tile(lockedBox, RubyBoxTile.class).inventory.setStackInSlot(0, new ItemStack(ModItems.RUBY, 2));
                t.place(hopper, "minecraft:hopper");
                t.place(lockedHopper, "minecraft:hopper");
                t.place(lockedHopper.east(), "minecraft:redstone_block");
            });
            t.waitUntil("hopper-pulls", 40, () -> t.inventory(hopper).getStackInSlot(0).getItem() == ModItems.RUBY,
                    () -> "hopper " + t.inventory(hopper).getStackInSlot(0) + ", state " + t.state(hopper));
            t.waitTicks(40);
            t.check("powered-hopper-idle", () -> !t.state(lockedHopper).getValue(BlockHopper.ENABLED)
                            && t.inventory(lockedHopper).getStackInSlot(0).isEmpty()
                            && t.tile(lockedBox, RubyBoxTile.class).inventory.getStackInSlot(0).getCount() == 2,
                    () -> "state " + t.state(lockedHopper) + ", hopper " + t.inventory(lockedHopper).getStackInSlot(0));
        }
    }
    /** TESTING.md: t.messages records what FakePlayer would drop; held-item onUpdate must be called by the test. */
    @GameTest("messages-and-held-item-ticks")
    public static final class Messages implements Scenario {
        @Override public void plan(TestContext t) {
            t.then("chat and action bar are recorded", () -> {
                FakePlayer p = t.player("listener");
                p.sendMessage(new TextComponentString("hello"));
                p.sendStatusMessage(new TextComponentTranslation("item.rubymod.wand.name"), true);
                t.require(t.messages(p).size() == 2 && t.messages(p).get(0).equals("hello"), "chat " + t.messages(p));
                t.require(t.messages(p).get(1).startsWith("actionbar: ") && t.messages(p).get(1).endsWith("[item.rubymod.wand.name]"), "status " + t.messages(p));
            });
            t.then("FakePlayer does not tick held items; the test calls onUpdate", () -> {
                FakePlayer p = t.player("holder");
                ItemStack wand = new ItemStack(ModItems.WAND);
                NBTTagCompound tag = new NBTTagCompound();
                tag.setInteger("Heat", 5);
                wand.setTagCompound(tag);
                p.inventory.setInventorySlotContents(5, wand);
                for (int i = 0; i < 3; i++) p.onUpdate();   // no-op on FakePlayer
                t.require(wand.getTagCompound().getInteger("Heat") == 5, "FakePlayer ticked the item");
                for (int i = 0; i < 2; i++) wand.getItem().onUpdate(wand, t.world(), p, 5, false);
                t.require(wand.getTagCompound().getInteger("Heat") == 3, "heat " + wand.getTagCompound().getInteger("Heat"));
            });
        }
    }
    /** TESTING.md / entities.md: mobs cannot see t.player(); t.targetPlayer() is targeted and hurt. t.loot rolls a table. */
    @GameTest("mob-targets-and-loot")
    public static final class Targeting implements Scenario {
        @Override public void plan(TestContext t) {
            BlockPos spot = t.origin().add(8, 0, 8);
            EntityLiving[] zombie = new EntityLiving[1];
            FakePlayer[] target = new FakePlayer[1];
            t.then("zombie next to a plain test player", () -> {
                t.player("ignored").setPosition(spot.getX() + 2.5, spot.getY(), spot.getZ() + 0.5);
                zombie[0] = (EntityLiving) t.spawn("rubymod:ruby_zombie", spot);
            });
            t.waitTicks(40);
            t.check("plain-player-invisible-to-mobs", () -> zombie[0].getAttackTarget() == null, () -> "target " + zombie[0].getAttackTarget());
            t.then("add a target player", () -> {
                target[0] = t.targetPlayer("target");
                target[0].setPosition(spot.getX() + 2.5, spot.getY(), spot.getZ() + 0.5);
            });
            t.waitUntil("zombie-targets-it", 100, () -> zombie[0].getAttackTarget() == target[0], () -> "target " + zombie[0].getAttackTarget());
            t.waitUntil("zombie-hurts-it", 200, () -> target[0].getHealth() < 20, () -> "health " + target[0].getHealth());
            t.then("loot table rolls 0-2 rubies", () -> {
                int min = 99, max = -1;
                for (int roll = 0; roll < 100; roll++) {
                    int rubies = t.count(t.loot("rubymod:entities/ruby_zombie"), "rubymod:ruby");
                    min = Math.min(min, rubies); max = Math.max(max, rubies);
                }
                t.require(min == 0 && max == 2, "rubies per roll " + min + ".." + max);
            });
        }
    }
}
