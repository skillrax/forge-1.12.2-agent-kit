package dev.agentic.energy;

import javax.annotation.Nullable;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ITickable;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.energy.CapabilityEnergy;

// Deliberately free power: fuel conversion is outside this small transfer proof.
public final class GeneratorTile extends TileEntity implements ITickable {
    public final EnergyBuffer energy=new EnergyBuffer(4000,0,128,this::markDirty);
    public long generated;
    @Override public void update() {
        if(world==null || world.isRemote)return;
        generated+=energy.generate(40);
    }
    @Override public boolean hasCapability(Capability<?> capability,@Nullable EnumFacing side) {
        return capability==CapabilityEnergy.ENERGY && side==EnumFacing.EAST || super.hasCapability(capability,side);
    }
    @Override public <T> T getCapability(Capability<T> capability,@Nullable EnumFacing side) {
        if(capability==CapabilityEnergy.ENERGY && side==EnumFacing.EAST)return CapabilityEnergy.ENERGY.cast(energy);
        return super.getCapability(capability,side);
    }
    @Override public NBTTagCompound writeToNBT(NBTTagCompound tag) {
        super.writeToNBT(tag);tag.setInteger("Energy",energy.getEnergyStored());tag.setLong("Generated",generated);return tag;
    }
    @Override public void readFromNBT(NBTTagCompound tag) {
        super.readFromNBT(tag);energy.restore(tag.getInteger("Energy"));generated=tag.getLong("Generated");
    }
}
