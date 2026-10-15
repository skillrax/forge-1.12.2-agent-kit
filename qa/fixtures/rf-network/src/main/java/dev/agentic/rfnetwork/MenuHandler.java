package dev.agentic.rfnetwork;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.network.IGuiHandler;
public final class MenuHandler implements IGuiHandler {
    private EnergyTile tile(int id,World w,int x,int y,int z){if(id!=0)return null;TileEntity t=Network.loaded(w,new BlockPos(x,y,z));return t instanceof EnergyTile?(EnergyTile)t:null;}
    public Object getServerGuiElement(int id,EntityPlayer p,World w,int x,int y,int z){EnergyTile t=tile(id,w,x,y,z);return t!=null && t.isUsableByPlayer(p)?new MachineContainer(p.inventory,t):null;}
    public Object getClientGuiElement(int id,EntityPlayer p,World w,int x,int y,int z){EnergyTile t=tile(id,w,x,y,z);return t==null?null:RfProof.proxy.screen(p,t);}
}
