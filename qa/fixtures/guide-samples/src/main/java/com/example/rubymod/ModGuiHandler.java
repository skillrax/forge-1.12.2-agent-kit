package com.example.rubymod;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.network.IGuiHandler;

/**
 * Registered in preInit: NetworkRegistry.INSTANCE.registerGuiHandler(INSTANCE, new ModGuiHandler()).
 * Open on the server with player.openGui(MainMod.INSTANCE, ID, world, x, y, z); Forge opens the client screen.
 * getClientGuiElement only runs on the client, so naming a client-only screen class here is safe.
 */
public class ModGuiHandler implements IGuiHandler {
    public static final int RUBY_BOX = 0;

    @Override
    public Object getServerGuiElement(int id, EntityPlayer player, World world, int x, int y, int z) {
        TileEntity tile = world.getTileEntity(new BlockPos(x, y, z));
        if (id == RUBY_BOX && tile instanceof RubyBoxTile) return new RubyBoxContainer(player.inventory, (RubyBoxTile) tile);
        return null;
    }

    @Override
    public Object getClientGuiElement(int id, EntityPlayer player, World world, int x, int y, int z) {
        TileEntity tile = world.getTileEntity(new BlockPos(x, y, z));
        if (id == RUBY_BOX && tile instanceof RubyBoxTile) return new RubyBoxScreen(player.inventory, (RubyBoxTile) tile);
        return null;
    }
}
