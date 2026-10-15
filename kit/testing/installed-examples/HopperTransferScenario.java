package example.installedtests;

import dev.agentic.installedtest.*;
import net.minecraft.block.BlockHopper;
import net.minecraft.init.Blocks;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.*;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;

/** Optional example. Copy to src/installedTest/java/example/installedtests, then adapt. */
@InstalledTest("hopper-transfer")
public final class HopperTransferScenario implements InstalledScenario {
    private IInventory source,target,hopper;
    private Item item;
    private int count;
    @Override public void plan(TestContext t) {
        count=t.integer("count",8);
        if(count<1 || count>64)throw new IllegalArgumentException("count must be 1..64");
        t.then("arrange-inventories",()->{
            item=t.item(t.string("item","minecraft:cobblestone"));
            BlockPos pos=t.origin();
            t.place(pos,Blocks.HOPPER.getDefaultState().withProperty(BlockHopper.FACING,EnumFacing.EAST));
            t.place(pos.up(),t.string("sourceBlock","minecraft:chest"));
            t.place(pos.east(),t.string("targetBlock","minecraft:chest"));
            source=t.inventory(pos.up());target=t.inventory(pos.east());hopper=t.inventory(pos);
            source.clear();target.clear();hopper.clear();source.setInventorySlotContents(0,new ItemStack(item,count));source.markDirty();
        });
        t.check("initial-accounting",()->total(source)==count && total(target)==0 && total(hopper)==0,()->"Items begin only in source");
        t.invariant("item-conservation",()->total(source)+total(target)+total(hopper)==count,()->counts());
        t.waitUntil("natural-tick-transfer",t.integer("waitTicks",100),()->total(target)==count && total(source)==0 && total(hopper)==0,()->counts());
    }
    private String counts(){return "source="+total(source)+", hopper="+total(hopper)+", target="+total(target);}
    private int total(IInventory inventory) {
        int found=0;
        for(int slot=0;slot<inventory.getSizeInventory();slot++) {
            ItemStack stack=inventory.getStackInSlot(slot);
            if(!stack.isEmpty()) {
                if(stack.getItem()!=item)throw new AssertionError("Unexpected item in test inventory");
                found+=stack.getCount();
            }
        }
        return found;
    }
}
