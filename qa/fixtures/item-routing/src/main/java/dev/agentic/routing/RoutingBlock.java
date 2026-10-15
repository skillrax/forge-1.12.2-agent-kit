package dev.agentic.routing;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.InventoryHelper;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.world.World;

public final class RoutingBlock extends Block {
    public final boolean splitter;
    RoutingBlock(boolean splitter,String name) {
        super(Material.IRON);this.splitter=splitter;
        setRegistryName(RoutingProof.ID,name);setTranslationKey(RoutingProof.ID+"."+name);
        setHardness(2);setCreativeTab(CreativeTabs.REDSTONE);
    }
    @Override public boolean hasTileEntity(IBlockState state){return true;}
    @Override public TileEntity createTileEntity(World world,IBlockState state){return new RoutingTile();}
    @Override public boolean onBlockActivated(World world,BlockPos pos,IBlockState state,EntityPlayer player,
            EnumHand hand,EnumFacing face,float x,float y,float z) {
        if(hand!=EnumHand.MAIN_HAND)return true;
        if(!world.isRemote) {
            RoutingTile tile=(RoutingTile)world.getTileEntity(pos);
            if(tile==null)return true;
            ItemStack held=player.getHeldItem(hand);
            if(splitter && !held.isEmpty())tile.setFilter(held);
            player.sendMessage(new TextComponentString(tile.describe()));
        }
        return true;
    }
    @Override public void breakBlock(World world,BlockPos pos,IBlockState state) {
        TileEntity tile=world.getTileEntity(pos);
        if(!world.isRemote && tile instanceof RoutingTile) {
            RoutingTile router=(RoutingTile)tile;
            for(int i=0;i<router.buffer.getSlots();i++) {
                ItemStack stack=router.buffer.extractItem(i,64,false);
                if(!stack.isEmpty())InventoryHelper.spawnItemStack(world,pos.getX(),pos.getY(),pos.getZ(),stack);
            }
        }
        super.breakBlock(world,pos,state);
    }
}
