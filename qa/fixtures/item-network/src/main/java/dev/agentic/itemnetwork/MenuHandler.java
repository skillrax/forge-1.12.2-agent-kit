package dev.agentic.itemnetwork;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.network.IGuiHandler;

public final class MenuHandler implements IGuiHandler {
    private DuctTile tile(int id,World world,int x,int y,int z) {
        BlockPos pos=new BlockPos(x,y,z);
        if(id!=0 || !world.isBlockLoaded(pos))return null;
        TileEntity tile=Network.loaded(world,pos);return tile instanceof DuctTile?(DuctTile)tile:null;
    }
    public Object getServerGuiElement(int id,EntityPlayer player,World world,int x,int y,int z) {
        DuctTile tile=tile(id,world,x,y,z);return tile!=null && tile.isUsableByPlayer(player)?new DuctContainer(player.inventory,tile):null;
    }
    public Object getClientGuiElement(int id,EntityPlayer player,World world,int x,int y,int z) {
        DuctTile tile=tile(id,world,x,y,z);return tile==null?null:ItemNetwork.proxy.screen(player,tile);
    }
}
