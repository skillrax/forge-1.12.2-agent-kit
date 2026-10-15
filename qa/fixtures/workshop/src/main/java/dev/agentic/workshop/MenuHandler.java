package dev.agentic.workshop;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.network.IGuiHandler;
public final class MenuHandler implements IGuiHandler {
    private boolean pouch(EntityPlayer player,int slot) {
        return slot>=0 && slot<9 && player.inventory.currentItem==slot
            && player.inventory.getStackInSlot(slot).getItem()==Content.POUCH
            && player.inventory.getStackInSlot(slot).getCount()==1;
    }
    private InventoryTile tile(int id,World world,int x,int y,int z) {
        BlockPos pos=new BlockPos(x,y,z);
        if(id!=0 || !world.isBlockLoaded(pos))return null;
        TileEntity tile=world.getTileEntity(pos);return tile instanceof InventoryTile?(InventoryTile)tile:null;
    }
    @Override public Object getServerGuiElement(int id,EntityPlayer player,World world,int x,int y,int z) {
        if(id==1)return pouch(player,x)?new PouchContainer(player,x):null;
        InventoryTile tile=tile(id,world,x,y,z);
        return tile!=null && tile.isUsableByPlayer(player)?new WorkshopContainer(player.inventory,tile):null;
    }
    @Override public Object getClientGuiElement(int id,EntityPlayer player,World world,int x,int y,int z) {
        if(id==1)return pouch(player,x)?Workshop.proxy.pouchScreen(player,x):null;
        InventoryTile tile=tile(id,world,x,y,z);return tile==null?null:Workshop.proxy.screen(player,tile);
    }
}
