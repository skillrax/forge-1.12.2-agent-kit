package dev.agentic.itemnetwork;
import net.minecraft.block.*;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.InventoryHelper;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.*;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
public final class DuctBlock extends Block {
 public DuctBlock(){super(Material.IRON);setRegistryName(ItemNetwork.ID,"duct");setTranslationKey("itemnetwork.duct");setCreativeTab(CreativeTabs.REDSTONE);setHardness(1.5F);}
 @Override public boolean hasTileEntity(IBlockState s){return true;}
 @Override public TileEntity createTileEntity(World w,IBlockState s){return new DuctTile();}
 @Override public boolean onBlockActivated(World w,BlockPos p,IBlockState s,EntityPlayer player,EnumHand h,EnumFacing f,float x,float y,float z){if(!w.isRemote)player.openGui(ItemNetwork.instance,0,w,p.getX(),p.getY(),p.getZ());return true;}
 @Override public void breakBlock(World w,BlockPos p,IBlockState s){TileEntity t=Network.loaded(w,p);if(!w.isRemote && t instanceof DuctTile){InventoryHelper.dropInventoryItems(w,p,(DuctTile)t);((DuctTile)t).clear();}super.breakBlock(w,p,s);}
}
