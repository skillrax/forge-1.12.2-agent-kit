package dev.agentic.rfnetwork;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.InventoryHelper;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.*;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.fluids.FluidUtil;
public final class MachineBlock extends Block {
    private final int kind;
    public MachineBlock(String name,int k){super(Material.IRON);kind=k;setRegistryName(RfProof.ID,name);setTranslationKey(RfProof.ID+"."+name);setHardness(2);setCreativeTab(CreativeTabs.REDSTONE);}
    public boolean hasTileEntity(IBlockState s){return true;}
    public TileEntity createTileEntity(World w,IBlockState s){return kind==0?new GeneratorTile():kind==1?new StorageTile():new SinkTile();}
    public boolean onBlockActivated(World w,BlockPos p,IBlockState s,EntityPlayer player,EnumHand hand,EnumFacing f,float x,float y,float z){
        if(w.isRemote)return true;
        if(kind==0 && FluidUtil.interactWithFluidHandler(player,hand,((GeneratorTile)w.getTileEntity(p)).fluids()))return true;
        player.openGui(RfProof.instance,0,w,p.getX(),p.getY(),p.getZ());return true;
    }
    public void breakBlock(World w,BlockPos p,IBlockState s){TileEntity t=w.getTileEntity(p);if(!w.isRemote && t instanceof InventoryTile){InventoryHelper.dropInventoryItems(w,p,(InventoryTile)t);((InventoryTile)t).clear();}super.breakBlock(w,p,s);}
}
