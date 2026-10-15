package dev.agentic.workshop;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.player.*;
import net.minecraft.util.*;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
public final class TrialPortal extends Block {
    public TrialPortal() {
        super(Material.ROCK);setRegistryName(Workshop.ID,"test_portal");setTranslationKey(Workshop.ID+".test_portal");
        setHardness(1.5F);setCreativeTab(CreativeTabs.DECORATIONS);
    }
    @Override public boolean onBlockActivated(World world,BlockPos pos,IBlockState state,EntityPlayer player,EnumHand hand,EnumFacing face,float x,float y,float z) {
        if(hand!=EnumHand.MAIN_HAND)return true;
        if(!world.isRemote && player instanceof EntityPlayerMP)PortalTravel.travel((EntityPlayerMP)player);
        return true;
    }
}