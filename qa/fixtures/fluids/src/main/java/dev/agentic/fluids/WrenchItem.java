package dev.agentic.fluids;

import net.minecraft.block.Block;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

public final class WrenchItem extends Item {
    public WrenchItem() {
        setRegistryName(FluidProof.ID,"wrench");setTranslationKey("fluidproof.wrench");
        setCreativeTab(CreativeTabs.MISC);setMaxStackSize(1);
    }
    @Override public EnumActionResult onItemUseFirst(EntityPlayer player,World world,BlockPos pos,EnumFacing side,
        float hitX,float hitY,float hitZ,EnumHand hand) {
        Block block=world.getBlockState(pos).getBlock();
        if(!player.isSneaking() || !(block instanceof MachineBlock))return EnumActionResult.PASS;
        if(!world.isBlockModifiable(player,pos) || !player.canPlayerEdit(pos,side,player.getHeldItem(hand)))return EnumActionResult.FAIL;
        if(world.isRemote)return EnumActionResult.SUCCESS;
        return ((MachineBlock)block).dismantle(world,pos)?EnumActionResult.SUCCESS:EnumActionResult.FAIL;
    }
}
