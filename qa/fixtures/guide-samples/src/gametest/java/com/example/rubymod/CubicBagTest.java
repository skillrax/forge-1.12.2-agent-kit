package com.example.rubymod;

import java.util.UUID;
import kit.gametest.GameTest;
import kit.gametest.Scenario;
import kit.gametest.TestContext;
import net.minecraft.entity.Entity;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.util.FakePlayer;

/** Cubic bag: chest with items + torch on stone + named armor stand, moved by clicks, then through the Nether and back. */
@GameTest("cubic-bag-moves-region")
public final class CubicBagTest implements Scenario {
    private FakePlayer player;
    private UUID standId;


    @Override public void plan(TestContext t) {
        BlockPos a = t.origin().add(1, 0, 1), size = new BlockPos(3, 2, 3);    // source a .. a+(2,1,2)
        BlockPos dest = a.add(5, 0, 0), back = a.add(5, 0, 5);
        AxisAlignedBB everything = new AxisAlignedBB(t.origin().down(), t.origin().add(16, 16, 16));   // this test's area only
        t.then("capture by clicking two corners", () -> {
            t.place(a, Blocks.CHEST.getDefaultState());
            t.inventory(a).setInventorySlotContents(0, new ItemStack(Items.DIAMOND, 5));
            t.place(a.add(2, 0, 2), Blocks.STONE.getDefaultState());
            t.place(a.add(2, 1, 2), Blocks.TORCH.getDefaultState());           // pops off if the stone vanishes first with updates
            Entity stand = t.spawn("minecraft:armor_stand", a.add(1, 0, 1));   // not an animal: test servers (spawn-animals=false) remove those
            stand.setCustomNameTag("Bag test");
            standId = stand.getUniqueID();
            player = t.player("mover");
            player.setHeldItem(EnumHand.MAIN_HAND, new ItemStack(ModItems.CUBIC_BAG));
            t.useOnBlock(player, a, EnumFacing.UP, EnumHand.MAIN_HAND);          // clicking the chest must not open it
            t.useOnBlock(player, a.add(2, 1, 2), EnumFacing.UP, EnumHand.MAIN_HAND);
            NBTTagCompound tag = player.getHeldItemMainhand().getTagCompound();
            t.require(tag.hasKey("Capture") && BlockPos.fromLong(tag.getLong("Size")).equals(size), "not captured: " + tag);
            t.require(tag.getIntArray("Preview").length == 18, "preview");
            for (int i = 0; i < 18; i++) t.require(t.world().isAirBlock(RegionMover.at(a, size, i)), "left behind at " + RegionMover.at(a, size, i));
            t.require(t.world().getTileEntity(a) == null, "chest tile left behind");
            t.require(t.world().getEntityFromUuid(standId) == null, "armor stand left behind");
        });
        t.waitTicks(5);
        t.check("no-spill-on-capture", () -> t.world().getEntitiesWithinAABB(EntityItem.class, everything).isEmpty(), () -> "items dropped: " + t.world().getEntitiesWithinAABB(EntityItem.class, everything));
        t.then("place by clicking the floor", () -> {
            t.useOnBlock(player, dest.down(), EnumFacing.UP, EnumHand.MAIN_HAND);
            t.require(!player.getHeldItemMainhand().getTagCompound().hasKey("Capture"), "bag still full");
            t.require(t.inventory(dest).getStackInSlot(0).getCount() == 5, "chest contents");
            t.require(t.state(dest.add(2, 1, 2)).getBlock() == Blocks.TORCH, "torch");
            Entity moved = t.world().getEntityFromUuid(standId);
            t.require(moved != null && Math.abs(moved.posX - (dest.getX() + 1.5)) < 0.01 && "Bag test".equals(moved.getCustomNameTag()), "armor stand: " + moved);
        });
        t.then("nether round trip", () -> {                                    // one step: the Nether has no player to keep chunks loaded
            WorldServer nether = t.server().getWorld(-1);
            BlockPos high = new BlockPos(a.getX(), 130, a.getZ());               // above the Nether roof: always air
            for (int x = (high.getX() - 1) >> 4; x <= (high.getX() + 3) >> 4; x++)
                for (int z = (high.getZ() - 1) >> 4; z <= (high.getZ() + 3) >> 4; z++) nether.getChunk(x, z);
            RegionMover.place(nether, player, RegionMover.capture(t.world(), player, dest, size), high);
            t.require(((IInventory) nether.getTileEntity(high)).getStackInSlot(0).getCount() == 5, "nether chest");
            Entity there = nether.getEntityFromUuid(standId);
            t.require(there != null && there.dimension == -1 && t.world().getEntityFromUuid(standId) == null, "armor stand in nether: " + there);
            RegionMover.place(t.world(), player, RegionMover.capture(nether, player, high, size), back);
            t.require(nether.isAirBlock(high) && nether.getEntityFromUuid(standId) == null, "nether not cleared");
            Entity home = t.world().getEntityFromUuid(standId);
            t.require(home != null, "armor stand not back: " + home);
        });
        t.waitTicks(20);
        t.check("chest-restored", () -> t.inventory(back).getStackInSlot(0).getCount() == 5, () -> "chest " + t.state(back));
        t.check("torch-still-attached", () -> t.state(back.add(2, 1, 2)).getBlock() == Blocks.TORCH, () -> "torch " + t.state(back.add(2, 1, 2)));
        t.check("entity-same-uuid", () -> {
            Entity moved = t.world().getEntityFromUuid(standId);
            return moved != null && RegionMover.box(back, size).contains(moved.getPositionVector().add(0, 0.1, 0));   // contains() is strict
        }, () -> "armor stand " + t.world().getEntityFromUuid(standId));
        t.check("no-spill-at-all", () -> t.world().getEntitiesWithinAABB(EntityItem.class, everything).isEmpty(),
                () -> "items dropped: " + t.world().getEntitiesWithinAABB(EntityItem.class, everything));
    }
}
