package com.example.configmachine;
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
import net.minecraft.util.text.TextComponentString;
import net.minecraft.world.World;

public final class MachineBlock extends Block {
    public MachineBlock(){super(Material.IRON);setRegistryName(MachineMod.ID,"machine");setTranslationKey(MachineMod.ID+".machine");setHardness(2);setCreativeTab(CreativeTabs.REDSTONE);}
    @Override public boolean hasTileEntity(IBlockState state){return true;}
    @Override public TileEntity createTileEntity(World world,IBlockState state){return new MachineTile();}
    @Override public boolean onBlockActivated(World world,BlockPos pos,IBlockState state,EntityPlayer player,EnumHand hand,EnumFacing side,float x,float y,float z){
        if(!world.isRemote){
            player.openGui(MachineMod.instance,0,world,pos.getX(),pos.getY(),pos.getZ());
        }
        return true;
    }
    @Override public void breakBlock(World world,BlockPos pos,IBlockState state){
        TileEntity tile=world.getTileEntity(pos);
        if(!world.isRemote && tile instanceof InventoryTile){InventoryHelper.dropInventoryItems(world,pos,(InventoryTile)tile);((InventoryTile)tile).clear();}
        super.breakBlock(world,pos,state);
    }
}
