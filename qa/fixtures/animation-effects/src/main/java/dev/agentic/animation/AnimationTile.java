package dev.agentic.animation;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.play.server.SPacketUpdateTileEntity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.AxisAlignedBB;

// Clock and control are server-owned. No renderer/particle classes on this path.
public final class AnimationTile extends TileEntity {
    public boolean active=true;
    private long elapsed,start;
    public double motionTime(float partial){return elapsed+(active?Math.max(0,world.getTotalWorldTime()-start)+partial:0);}
    public void toggle(){
        if(world.isRemote)return;
        elapsed=(long)motionTime(0);start=world.getTotalWorldTime();active=!active;markDirty();
        world.notifyBlockUpdate(pos,world.getBlockState(pos),world.getBlockState(pos),3);
    }
    @Override public void onLoad(){if(!world.isRemote && start==0){start=world.getTotalWorldTime();markDirty();}}
    @Override public NBTTagCompound writeToNBT(NBTTagCompound n){super.writeToNBT(n);n.setBoolean("active",active);n.setLong("elapsed",elapsed);n.setLong("start",start);return n;}
    @Override public void readFromNBT(NBTTagCompound n){super.readFromNBT(n);active=!n.hasKey("active")||n.getBoolean("active");elapsed=Math.max(0,n.getLong("elapsed"));start=n.getLong("start");}
    @Override public NBTTagCompound getUpdateTag(){return writeToNBT(new NBTTagCompound());}
    @Override public SPacketUpdateTileEntity getUpdatePacket(){return new SPacketUpdateTileEntity(pos,0,getUpdateTag());}
    @Override public void onDataPacket(NetworkManager manager,SPacketUpdateTileEntity packet){readFromNBT(packet.getNbtCompound());}
    @Override public AxisAlignedBB getRenderBoundingBox(){return new AxisAlignedBB(pos).grow(.35,1, .35);}
}
