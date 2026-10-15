package dev.agentic.workshop;

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

public final class WorkshopBlock extends Block {
    private final boolean processor;
    public WorkshopBlock(String name,boolean processor) {
        super(Material.ROCK);this.processor=processor;
        setRegistryName(Workshop.ID,name);setTranslationKey(Workshop.ID+"."+name);
        setCreativeTab(CreativeTabs.DECORATIONS);setHardness(1.5F);
    }
    @Override public boolean hasTileEntity(IBlockState state) {return true;}
    @Override public TileEntity createTileEntity(World world,IBlockState state) {
        return processor?new ProcessorTile():new StorageTile();
    }
    @Override public boolean onBlockActivated(World world,BlockPos pos,IBlockState state,EntityPlayer player,
            EnumHand hand,EnumFacing face,float x,float y,float z) {
        if(!world.isRemote && world.getTileEntity(pos) instanceof InventoryTile)
            player.openGui(Workshop.instance,0,world,pos.getX(),pos.getY(),pos.getZ());
        return true;
    }
    @Override public void breakBlock(World world,BlockPos pos,IBlockState state) {
        TileEntity tile=world.getTileEntity(pos);
        if(!world.isRemote && tile instanceof InventoryTile) {
            InventoryHelper.dropInventoryItems(world,pos,(InventoryTile)tile);
            ((InventoryTile)tile).clear();
        }
        super.breakBlock(world,pos,state);
    }
}
