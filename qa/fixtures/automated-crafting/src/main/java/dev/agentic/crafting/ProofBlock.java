package dev.agentic.crafting;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.InventoryHelper;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;

public final class ProofBlock extends Block {
    public final int kind;
    public ProofBlock(String name,int kind) {
        super(Material.IRON);this.kind=kind;
        setRegistryName(CraftingProof.ID,name);setTranslationKey(CraftingProof.ID+"."+name);
        setCreativeTab(CreativeTabs.REDSTONE);setHardness(1.5F);
    }
    @Override public boolean hasTileEntity(IBlockState state) {return true;}
    @Override public TileEntity createTileEntity(World world,IBlockState state) {
        return kind==0?new GeneratorTile():kind==1?new PipeTile():new ProcessorTile();
    }
    @Override public boolean isOpaqueCube(IBlockState state) {return kind!=1;}
    @Override public boolean isFullCube(IBlockState state) {return kind!=1;}
    @Override public AxisAlignedBB getBoundingBox(IBlockState state,IBlockAccess world,BlockPos pos) {
        return kind==1?new AxisAlignedBB(0,.375,.375,1,.625,.625):FULL_BLOCK_AABB;
    }
    @Override public boolean onBlockActivated(World world,BlockPos pos,IBlockState state,EntityPlayer player,
            EnumHand hand,EnumFacing side,float x,float y,float z) {
        if(!world.isRemote) {
            TileEntity tile=world.getTileEntity(pos);
            if(tile instanceof ProcessorTile)player.openGui(CraftingProof.instance,0,world,pos.getX(),pos.getY(),pos.getZ());
            else if(tile instanceof GeneratorTile) {
                GeneratorTile generator=(GeneratorTile)tile;
                player.sendMessage(new TextComponentString("Demo generator: "+generator.energy.getEnergyStored()+"/4000 RF; generated "+generator.generated+" RF. Output: EAST."));
            } else if(tile instanceof PipeTile)player.sendMessage(new TextComponentString("Pipe: "+((PipeTile)tile).energy.getEnergyStored()+"/256 RF; 80 RF/t, WEST -> EAST."));
        }
        return true;
    }
    @Override public void breakBlock(World world,BlockPos pos,IBlockState state) {
        TileEntity tile=world.getTileEntity(pos);
        if(!world.isRemote && tile instanceof InventoryTile) {
            InventoryHelper.dropInventoryItems(world,pos,(InventoryTile)tile);((InventoryTile)tile).clear();
        }
        super.breakBlock(world,pos,state);
    }
}
