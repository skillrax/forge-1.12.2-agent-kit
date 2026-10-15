package dev.agentic.statue;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.block.properties.PropertyDirection;
import net.minecraft.block.state.BlockStateContainer;
import net.minecraft.block.state.IBlockState;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;

public final class StatueBlock extends Block {
    public static final PropertyDirection FACING=PropertyDirection.create("facing",EnumFacing.Plane.HORIZONTAL);
    private static final AxisAlignedBB BASE=new AxisAlignedBB(0,0,0,1,.15,1);
    StatueBlock(){
        super(Material.ROCK);setRegistryName(ModelStatue.ID,"bunny_statue");setTranslationKey(ModelStatue.ID+".bunny_statue");
        setHardness(2);setCreativeTab(CreativeTabs.DECORATIONS);setDefaultState(blockState.getBaseState().withProperty(FACING,EnumFacing.SOUTH));
    }
    @Override protected BlockStateContainer createBlockState(){return new BlockStateContainer(this,FACING);}
    @Override public IBlockState getStateFromMeta(int meta){return getDefaultState().withProperty(FACING,EnumFacing.byHorizontalIndex(meta&3));}
    @Override public int getMetaFromState(IBlockState state){return state.getValue(FACING).getHorizontalIndex();}
    @Override public IBlockState getStateForPlacement(World world,BlockPos pos,EnumFacing side,float x,float y,float z,int meta,EntityLivingBase placer,EnumHand hand){return getDefaultState().withProperty(FACING,placer.getHorizontalFacing().getOpposite());}
    @Override public boolean hasTileEntity(IBlockState state){return true;}
    @Override public TileEntity createTileEntity(World world,IBlockState state){return new StatueTile();}
    @Override public boolean isOpaqueCube(IBlockState state){return false;}
    @Override public boolean isFullCube(IBlockState state){return false;}
    @Override public AxisAlignedBB getBoundingBox(IBlockState state,IBlockAccess world,BlockPos pos){return BASE;}
}
