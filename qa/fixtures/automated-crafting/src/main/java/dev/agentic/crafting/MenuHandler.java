package dev.agentic.crafting;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.network.IGuiHandler;

public final class MenuHandler implements IGuiHandler {
    private ProcessorTile tile(int id,World world,int x,int y,int z) {
        BlockPos pos=new BlockPos(x,y,z);
        if(id!=0 || !world.isBlockLoaded(pos))return null;
        TileEntity tile=world.getTileEntity(pos);return tile instanceof ProcessorTile?(ProcessorTile)tile:null;
    }
    public Object getServerGuiElement(int id,EntityPlayer player,World world,int x,int y,int z) {
        ProcessorTile tile=tile(id,world,x,y,z);return tile!=null && tile.isUsableByPlayer(player)?new ProcessorContainer(player.inventory,tile):null;
    }
    public Object getClientGuiElement(int id,EntityPlayer player,World world,int x,int y,int z) {
        ProcessorTile tile=tile(id,world,x,y,z);return tile==null?null:CraftingProof.proxy.screen(player,tile);
    }
}
