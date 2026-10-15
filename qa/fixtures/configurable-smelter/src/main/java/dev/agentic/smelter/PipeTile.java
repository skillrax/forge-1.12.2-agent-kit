package dev.agentic.smelter;

import javax.annotation.Nullable;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ITickable;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.energy.CapabilityEnergy;
import net.minecraftforge.energy.IEnergyStorage;

public final class PipeTile extends TileEntity implements ITickable {
    public static final int RATE=80;
    public final EnergyBuffer energy=new EnergyBuffer(256,RATE,RATE,this::markDirty);
    private final IEnergyStorage input=new Port(true),output=new Port(false);
    private final class Port implements IEnergyStorage {
        private final boolean receiving;
        Port(boolean receiving) {this.receiving=receiving;}
        public int receiveEnergy(int amount,boolean simulate) {return receiving?energy.receiveEnergy(amount,simulate):0;}
        public int extractEnergy(int amount,boolean simulate) {return receiving?0:energy.extractEnergy(amount,simulate);}
        public int getEnergyStored() {return energy.getEnergyStored();}
        public int getMaxEnergyStored() {return energy.getMaxEnergyStored();}
        public boolean canExtract() {return !receiving;}
        public boolean canReceive() {return receiving;}
    }
    private IEnergyStorage neighbor(EnumFacing direction) {
        BlockPos target=pos.offset(direction);
        if(!world.isBlockLoaded(target))return null;
        TileEntity tile=world.getTileEntity(target);
        return tile==null?null:tile.getCapability(CapabilityEnergy.ENERGY,direction.getOpposite());
    }
    @Override public void update() {
        if(world==null || world.isRemote)return;
        IEnergyStorage source=neighbor(EnumFacing.WEST),sink=neighbor(EnumFacing.EAST);
        if(source!=null && source.canExtract()) {
            int room=energy.receiveEnergy(RATE,true);
            int available=source.extractEnergy(room,true);
            int extracted=source.extractEnergy(Math.min(room,available),false);
            energy.receiveEnergy(extracted,false);
        }
        if(sink!=null && sink.canReceive()) {
            int available=energy.extractEnergy(RATE,true);
            int accepted=sink.receiveEnergy(available,true);
            int removed=energy.extractEnergy(Math.min(available,accepted),false);
            int delivered=sink.receiveEnergy(removed,false);
            // Preserve undelivered energy if a compliant receiver accepts less on commit.
            energy.generate(removed-delivered);
        }
    }
    @Override public boolean hasCapability(Capability<?> capability,@Nullable EnumFacing side) {
        return capability==CapabilityEnergy.ENERGY && (side==EnumFacing.WEST || side==EnumFacing.EAST) || super.hasCapability(capability,side);
    }
    @Override public <T> T getCapability(Capability<T> capability,@Nullable EnumFacing side) {
        if(capability==CapabilityEnergy.ENERGY) {
            if(side==EnumFacing.WEST)return CapabilityEnergy.ENERGY.cast(input);
            if(side==EnumFacing.EAST)return CapabilityEnergy.ENERGY.cast(output);
        }
        return super.getCapability(capability,side);
    }
    @Override public NBTTagCompound writeToNBT(NBTTagCompound tag) {
        super.writeToNBT(tag);tag.setInteger("Energy",energy.getEnergyStored());return tag;
    }
    @Override public void readFromNBT(NBTTagCompound tag) {super.readFromNBT(tag);energy.restore(tag.getInteger("Energy"));}
}
