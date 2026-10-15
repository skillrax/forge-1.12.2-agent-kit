package dev.agentic.fluids;

import java.util.List;
import net.minecraft.block.Block;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

public final class MachineItemBlock extends ItemBlock {
    public MachineItemBlock(Block block) {super(block);}
    @Override public int getItemStackLimit(ItemStack stack) {return stack.getSubCompound("BlockEntityTag")!=null?1:64;}
    @Override @SideOnly(Side.CLIENT) public void addInformation(ItemStack stack,World world,List<String> tooltip,ITooltipFlag flag) {
        super.addInformation(stack,world,tooltip,flag);
        NBTTagCompound data=stack.getSubCompound("BlockEntityTag");
        if(data!=null) {
            tooltip.add("Wrench-packed: "+data.getCompoundTag("Tank").getInteger("Amount")+" / 4000 mB Biting Brine");
            tooltip.add("Input: "+new ItemStack(data.getCompoundTag("Input")).getCount()+"; output: "+new ItemStack(data.getCompoundTag("Output")).getCount());
            tooltip.add("Progress and machine counters preserved.");
        }
    }
}
