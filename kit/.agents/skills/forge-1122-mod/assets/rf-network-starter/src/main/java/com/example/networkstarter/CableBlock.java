package com.example.networkstarter;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.block.properties.PropertyBool;
import net.minecraft.block.state.*;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.Entity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.*;
import net.minecraft.world.*;
import net.minecraftforge.energy.CapabilityEnergy;
public final class CableBlock extends Block {
    public static final PropertyBool[] LINKS={PropertyBool.create("down"),PropertyBool.create("up"),PropertyBool.create("north"),PropertyBool.create("south"),PropertyBool.create("west"),PropertyBool.create("east")};
    private static final AxisAlignedBB CORE=new AxisAlignedBB(.375,.375,.375,.625,.625,.625);
    private static final AxisAlignedBB[] ARMS={new AxisAlignedBB(.375,0,.375,.625,.375,.625),new AxisAlignedBB(.375,.625,.375,.625,1,.625),new AxisAlignedBB(.375,.375,0,.625,.625,.375),new AxisAlignedBB(.375,.375,.625,.625,.625,1),new AxisAlignedBB(0,.375,.375,.375,.625,.625),new AxisAlignedBB(.625,.375,.375,1,.625,.625)};
    public CableBlock(){super(Material.IRON);setRegistryName(NetworkMod.ID,"cable");setTranslationKey(NetworkMod.ID+".cable");setHardness(1);setCreativeTab(CreativeTabs.REDSTONE);IBlockState s=blockState.getBaseState();for(PropertyBool p:LINKS)s=s.withProperty(p,false);setDefaultState(s);}
    protected BlockStateContainer createBlockState(){return new BlockStateContainer(this,LINKS);}
    public boolean hasTileEntity(IBlockState s){return true;}public TileEntity createTileEntity(World w,IBlockState s){return new CableTile();}
    public int getMetaFromState(IBlockState s){return 0;}public IBlockState getStateFromMeta(int m){return getDefaultState();}
    public boolean isOpaqueCube(IBlockState s){return false;}public boolean isFullCube(IBlockState s){return false;}
    public IBlockState getActualState(IBlockState s,IBlockAccess access,BlockPos p){for(EnumFacing f:EnumFacing.values()){BlockPos q=p.offset(f);TileEntity t=access instanceof World?Network.loaded((World)access,q):access.getTileEntity(q);s=s.withProperty(LINKS[f.getIndex()],t instanceof CableTile || t!=null && t.hasCapability(CapabilityEnergy.ENERGY,f.getOpposite()));}return s;}
    public AxisAlignedBB getBoundingBox(IBlockState s,IBlockAccess w,BlockPos p){s=getActualState(s,w,p);AxisAlignedBB box=CORE;for(int i=0;i<6;i++)if(s.getValue(LINKS[i]))box=box.union(ARMS[i]);return box;}
    public void addCollisionBoxToList(IBlockState s,World w,BlockPos p,AxisAlignedBB box,List<AxisAlignedBB> list,@Nullable Entity e,boolean actual){s=getActualState(s,w,p);addCollisionBoxToList(p,box,list,CORE);for(int i=0;i<6;i++)if(s.getValue(LINKS[i]))addCollisionBoxToList(p,box,list,ARMS[i]);}
}
