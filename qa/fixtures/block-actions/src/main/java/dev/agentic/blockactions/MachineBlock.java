package dev.agentic.blockactions;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.InventoryHelper;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

public final class MachineBlock extends Block {
    public net.minecraft.util.NonNullList<net.minecraft.item.ItemStack> capture(boolean start) {return captureDrops(start);}
    public boolean capturing() {return captureDrops.get();}
    public MachineBlock() {
        super(Material.IRON);
        setRegistryName(MachineStarter.ID,"machine");setTranslationKey(MachineStarter.ID+".machine");
        setCreativeTab(CreativeTabs.REDSTONE);setHardness(1.5F);
    }
    @Override public boolean hasTileEntity(IBlockState state) {return true;}
    @Override public TileEntity createTileEntity(World world,IBlockState state) {return new MachineTile();}
    @Override public boolean onBlockActivated(World world,BlockPos pos,IBlockState state,EntityPlayer player,
            EnumHand hand,EnumFacing side,float x,float y,float z) {
        if(!world.isRemote && world.getTileEntity(pos) instanceof MachineTile)
            player.openGui(MachineStarter.instance,0,world,pos.getX(),pos.getY(),pos.getZ());
        return true;
    }
    @Override public void breakBlock(World world,BlockPos pos,IBlockState state) {
        TileEntity tile=world.getTileEntity(pos);
        if(!world.isRemote && tile instanceof MachineTile) {
            InventoryHelper.dropInventoryItems(world,pos,(MachineTile)tile);((MachineTile)tile).clear();
        }
        super.breakBlock(world,pos,state);
    }
}
