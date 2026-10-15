package dev.agentic.fluids;

import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ITickable;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidTank;
import net.minecraftforge.fluids.capability.CapabilityFluidHandler;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fluids.capability.IFluidTankProperties;
import net.minecraftforge.fluids.capability.FluidTankProperties;

public final class MachineTile extends TileEntity implements ITickable {
    public int kind,progress,completed;
    public long generated,consumed,transferred;
    public ItemStack input=ItemStack.EMPTY,output=ItemStack.EMPTY;
    // Only suppress loose inventory drops during a successful wrench removal; never saved.
    boolean wrenchRemoval;
    public final FluidTank tank=new FluidTank(4000) {
        @Override public boolean canFillFluidType(FluidStack stack) {return stack!=null && stack.getFluid()==FluidProof.BRINE;}
        @Override protected void onContentsChanged() {markDirty();}
    };
    private final IFluidHandler inlet=new Port(true,false),outlet=new Port(false,true);
    public MachineTile() {}
    public MachineTile(int kind) {this.kind=kind;}
    public Item ingredient() {return kind==0?Items.REDSTONE:Item.getItemFromBlock(Blocks.COBBLESTONE);}
    @Override public void update() {
        if(world==null || world.isRemote)return;
        if(kind==0) {
            if(!input.isEmpty() && tank.getCapacity()-tank.getFluidAmount()>=1000) {
                if(++progress>=40) {
                    tank.fill(new FluidStack(FluidProof.BRINE,1000),true);input.shrink(1);
                    generated+=1000;completed++;progress=0;
                }
                markDirty();
            } else if(progress!=0) {progress=0;markDirty();}
        } else if(kind==2) {
            if(!input.isEmpty() && tank.getFluidAmount()>=250 && (output.isEmpty() || output.getCount()<64)) {
                if(++progress>=20) {
                    tank.drain(250,true);input.shrink(1);
                    if(output.isEmpty())output=new ItemStack(Items.CLAY_BALL);else output.grow(1);
                    consumed+=250;completed++;progress=0;
                }
                markDirty();
            } else if(progress!=0) {progress=0;markDirty();}
        }
        if(kind!=2)pushEast();
    }
    private void pushEast() {
        if(tank.getFluidAmount()==0 || !world.isBlockLoaded(pos.east()))return;
        TileEntity target=world.getTileEntity(pos.east());
        if(target==null)return;
        IFluidHandler receiver=target.getCapability(CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY,EnumFacing.WEST);
        if(receiver==null)return;
        FluidStack offer=tank.drain(100,false);
        int accepted=receiver.fill(offer,false);
        if(accepted<=0)return;
        offer.amount=Math.min(accepted,offer.amount);
        int delivered=receiver.fill(offer,true);
        if(delivered>0) {tank.drain(delivered,true);transferred+=delivered;markDirty();}
    }
    private IFluidHandler port(EnumFacing face) {
        if(kind==0)return outlet;
        if(kind==2)return inlet;
        return face==EnumFacing.WEST?inlet:face==EnumFacing.EAST?outlet:null;
    }
    @Override public boolean hasCapability(Capability<?> capability,EnumFacing face) {
        return capability==CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY?port(face)!=null:super.hasCapability(capability,face);
    }
    @Override public <T> T getCapability(Capability<T> capability,EnumFacing face) {
        if(capability==CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY)return CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY.cast(port(face));
        return super.getCapability(capability,face);
    }
    private final class Port implements IFluidHandler {
        private final boolean fill,drain;
        Port(boolean fill,boolean drain) {this.fill=fill;this.drain=drain;}
        @Override public IFluidTankProperties[] getTankProperties() {
            FluidStack fluid=tank.getFluid();
            return new IFluidTankProperties[]{new FluidTankProperties(fluid==null?null:fluid.copy(),tank.getCapacity(),fill,drain) {
                @Override public boolean canFillFluidType(FluidStack stack) {return fill && stack!=null && stack.getFluid()==FluidProof.BRINE;}
            }};
        }
        @Override public int fill(FluidStack fluid,boolean commit) {return fill?tank.fill(fluid,commit):0;}
        @Override public FluidStack drain(FluidStack fluid,boolean commit) {
            return drain && fluid!=null && fluid.getFluid()==FluidProof.BRINE?tank.drain(fluid.amount,commit):null;
        }
        @Override public FluidStack drain(int amount,boolean commit) {return drain?tank.drain(amount,commit):null;}
    }
    @Override public NBTTagCompound writeToNBT(NBTTagCompound nbt) {
        super.writeToNBT(nbt);nbt.setInteger("Kind",kind);nbt.setInteger("Progress",progress);nbt.setInteger("Completed",completed);
        nbt.setLong("Generated",generated);nbt.setLong("Consumed",consumed);nbt.setLong("Transferred",transferred);
        nbt.setTag("Tank",tank.writeToNBT(new NBTTagCompound()));
        nbt.setTag("Input",input.writeToNBT(new NBTTagCompound()));nbt.setTag("Output",output.writeToNBT(new NBTTagCompound()));return nbt;
    }
    @Override public void readFromNBT(NBTTagCompound nbt) {
        super.readFromNBT(nbt);kind=nbt.getInteger("Kind");progress=nbt.getInteger("Progress");completed=nbt.getInteger("Completed");
        generated=nbt.getLong("Generated");consumed=nbt.getLong("Consumed");transferred=nbt.getLong("Transferred");
        NBTTagCompound tankData=nbt.getCompoundTag("Tank").copy();
        // ItemBlock merges BlockEntityTag into a fresh tile. Its empty tank contributes
        // an "Empty" sentinel; explicit carried fluid must take precedence over that.
        if(tankData.hasKey("FluidName",8))tankData.removeTag("Empty");
        tank.readFromNBT(tankData);input=new ItemStack(nbt.getCompoundTag("Input"));output=new ItemStack(nbt.getCompoundTag("Output"));
    }
}
