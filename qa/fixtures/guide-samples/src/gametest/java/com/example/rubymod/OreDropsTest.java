package com.example.rubymod;

import kit.gametest.GameTest;
import kit.gametest.Scenario;
import kit.gametest.TestContext;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.common.util.FakePlayer;

/** The example test in kit/docs/TESTING.md. */
@GameTest("ore-drops-ruby")
public class OreDropsTest implements Scenario {
    @Override public void plan(TestContext t) {
        BlockPos pos = t.origin().add(2, 0, 2);
        t.then("mine it", () -> {
            t.place(pos, "rubymod:ruby_ore");
            FakePlayer p = t.player("miner");
            p.setHeldItem(EnumHand.MAIN_HAND, new ItemStack(Items.IRON_PICKAXE));
            t.require(t.breakBlock(p, pos), "not broken");
        });
        t.check("ruby-dropped", () -> t.world().getEntitiesWithinAABB(EntityItem.class, new AxisAlignedBB(pos).grow(2))
                .stream().anyMatch(e -> e.getItem().getItem() == ModItems.RUBY), () -> "no ruby item entity");
    }
}
