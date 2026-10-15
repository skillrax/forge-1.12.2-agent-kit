package com.example.networkstarter;
import net.minecraft.util.EnumFacing;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.energy.*;
import net.minecraft.tileentity.TileEntity;
public final class CableTile extends TileEntity {
    @Override public void markDirty(){if(world!=null && !world.isRemote)new LoadedView(world).markOwnerDirty(pos);}
    public int remaining(){return TickLedger.remaining(world,pos,"cable",NetworkSettings.CABLE_RATE);}
    public IEnergyStorage port(final EnumFacing side){return new IEnergyStorage(){
        public int receiveEnergy(int n,boolean sim){return world==null || world.isRemote || side==null?0:Network.offer(CableTile.this,Math.max(0,n),sim,pos.offset(side));}
        public int extractEnergy(int n,boolean sim){return 0;}
        public int getEnergyStored(){return 0;}public int getMaxEnergyStored(){return 0;}
        public boolean canReceive(){return side!=null;}public boolean canExtract(){return false;}
    };}
    public boolean hasCapability(Capability<?> c,EnumFacing f){return c==CapabilityEnergy.ENERGY || super.hasCapability(c,f);}
    public <T>T getCapability(Capability<T> c,EnumFacing f){return c==CapabilityEnergy.ENERGY?CapabilityEnergy.ENERGY.cast(port(f)):super.getCapability(c,f);}
}
