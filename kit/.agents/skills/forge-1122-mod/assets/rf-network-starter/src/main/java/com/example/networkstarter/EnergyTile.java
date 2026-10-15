package com.example.networkstarter;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.energy.CapabilityEnergy;
import net.minecraftforge.energy.IEnergyStorage;
public abstract class EnergyTile extends InventoryTile {
    protected EnergyTile(int slots){super(slots);}
    protected abstract int receive(int amount,boolean simulate,EnumFacing side);
    protected abstract int extract(int amount,boolean simulate,EnumFacing side);
    public abstract int stored();
    public abstract int capacity();
    protected boolean input(EnumFacing side){return false;}
    protected boolean output(EnumFacing side){return false;}
    public boolean control(int action){return false;}
    /** Restore a partial committed transfer at its native source; keep the tick allowance spent. */
    public void refund(int amount,int fuelCost){throw new IllegalStateException("Not an energy source");}
    public int fuelCost(){return 0;}
    @Override public String getName(){return "container.networkstarter";}
    private boolean available(){return world!=null && !world.isRemote && !isInvalid() && Network.loaded(world,pos)==this;}
    public IEnergyStorage port(final EnumFacing side){return new IEnergyStorage(){
        public int receiveEnergy(int n,boolean sim){return available() && input(side)?receive(Math.max(0,n),sim,side):0;}
        public int extractEnergy(int n,boolean sim){return available() && output(side)?extract(Math.max(0,n),sim,side):0;}
        public int getEnergyStored(){return stored();}public int getMaxEnergyStored(){return capacity();}
        public boolean canReceive(){return input(side);}public boolean canExtract(){return output(side);}
    };}
    @Override public boolean hasCapability(Capability<?> c,EnumFacing side){return c==CapabilityEnergy.ENERGY || super.hasCapability(c,side);}
    @Override public <T>T getCapability(Capability<T> c,EnumFacing side){return c==CapabilityEnergy.ENERGY?CapabilityEnergy.ENERGY.cast(port(side)):super.getCapability(c,side);}
    // 32-bit values split into two unsigned 16-bit window properties (vanilla transports signed shorts).
    protected int display(int index){return index==0?stored():index==1?capacity():0;}
    protected void applyDisplay(int index,int value){}
    private final int[] clientFields=new int[16];
    @Override public int getFieldCount(){return 32;}
    @Override public int getField(int id){return (display(id/2) >>> ((id%2)*16)) & 65535;}
    @Override public void setField(int id,int value){int i=id/2;if(i>=clientFields.length)return;int shift=id%2*16;clientFields[i]=(clientFields[i]&~(65535<<shift))|((value&65535)<<shift);applyDisplay(i,clientFields[i]);}
}
