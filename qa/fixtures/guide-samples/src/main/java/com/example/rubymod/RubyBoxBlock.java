package com.example.rubymod;

import net.minecraft.block.SoundType;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.world.World;
import net.minecraft.inventory.InventoryHelper;
import net.minecraftforge.items.ItemStackHandler;

public class RubyBoxBlock extends BasicBlock {
    public RubyBoxBlock() {
        super(Material.IRON, SoundType.METAL, 3.0F, 5.0F);
        harvest("pickaxe", 0);
    }

    @Override
    public boolean hasTileEntity(IBlockState state) { return true; }

    @Override
    public TileEntity createTileEntity(World world, IBlockState state) { return new RubyBoxTile(); }

    /** Right-click. Runs on both sides: return true on both, act on the server only. */
    @Override
    public boolean onBlockActivated(World world, BlockPos pos, IBlockState state, EntityPlayer player, EnumHand hand, EnumFacing facing, float hitX, float hitY, float hitZ) {
        if (!world.isRemote) {
            player.openGui(RubymodMod.INSTANCE, ModGuiHandler.RUBY_BOX, world, pos.getX(), pos.getY(), pos.getZ());
        }
        return true;
    }

    /** Drops the inventory when the block is broken. */
    @Override
    public void breakBlock(World world, BlockPos pos, IBlockState state) {
        TileEntity tile = world.getTileEntity(pos);
        if (tile instanceof RubyBoxTile) {
            ItemStackHandler inventory = ((RubyBoxTile) tile).inventory;
            for (int slot = 0; slot < inventory.getSlots(); slot++) InventoryHelper.spawnItemStack(world, pos.getX(), pos.getY(), pos.getZ(), inventory.getStackInSlot(slot));
        }
        super.breakBlock(world, pos, state);
    }}