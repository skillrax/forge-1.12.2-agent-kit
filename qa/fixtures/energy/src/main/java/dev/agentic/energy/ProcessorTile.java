package dev.agentic.energy;

import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.inventory.ISidedInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ITickable;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.energy.CapabilityEnergy;

public final class ProcessorTile extends InventoryTile implements ITickable,ISidedInventory {
    private static final List<ProcessingRecipe> RECIPES=ProcessingRecipe.recipes();
    public final EnergyBuffer energy=new EnergyBuffer(4000,128,0,this::markDirty);
    private int progress;
    private int clientDuration=1;
    public long spent,completed,secondaryProduced;
    public ProcessorTile() {super(3);}
    @Override public String getName() {return "tile.energyproof.processor.name";}
    public static ProcessingRecipe recipe(ItemStack input) {
        for(ProcessingRecipe recipe:RECIPES)if(recipe.matches(input))return recipe;return null;
    }
    private boolean room(int slot,ItemStack output) {
        ItemStack current=getStackInSlot(slot);
        return current.isEmpty() || ItemStack.areItemsEqual(current,output) && ItemStack.areItemStackTagsEqual(current,output)
            && current.getCount()+output.getCount()<=Math.min(current.getMaxStackSize(),getInventoryStackLimit());
    }
    private void output(int slot,ItemStack stack) {
        if(getStackInSlot(slot).isEmpty())stacks.set(slot,stack.copy());else getStackInSlot(slot).grow(stack.getCount());
    }
    @Override public void update() {
        if(world==null || world.isRemote)return;
        ProcessingRecipe recipe=recipe(getStackInSlot(0));
        if(recipe==null) {if(progress!=0){progress=0;markDirty();}return;}
        // Reserve both outputs even if the secondary roll might fail; blocked work never rerolls.
        if(!room(1,recipe.primary) || !room(2,recipe.secondary) || !energy.spend(recipe.rfPerTick))return;
        spent+=recipe.rfPerTick;
        if(++progress>=recipe.ticks) {
            getStackInSlot(0).shrink(recipe.input.getCount());output(1,recipe.primary);
            if(recipe.producesSecondary(world.rand.nextFloat())) {output(2,recipe.secondary);secondaryProduced+=recipe.secondary.getCount();}
            completed++;progress=0;
        }
        markDirty();
    }
    @Override public void setInventorySlotContents(int slot,ItemStack stack) {
        if(slot==0 && (!ItemStack.areItemsEqual(getStackInSlot(0),stack) || !ItemStack.areItemStackTagsEqual(getStackInSlot(0),stack)))progress=0;
        super.setInventorySlotContents(slot,stack);
    }
    @Override public boolean isItemValidForSlot(int slot,ItemStack stack) {return slot==0 && recipe(stack)!=null;}
    @Override public int[] getSlotsForFace(EnumFacing side) {return side==EnumFacing.DOWN?new int[]{1,2}:new int[]{0};}
    @Override public boolean canInsertItem(int slot,ItemStack stack,EnumFacing side) {return side!=EnumFacing.DOWN && isItemValidForSlot(slot,stack);}
    @Override public boolean canExtractItem(int slot,ItemStack stack,EnumFacing side) {return side==EnumFacing.DOWN && (slot==1 || slot==2);}
    @Override public int getField(int id) {
        if(id==0)return progress;
        if(id==1)return energy.getEnergyStored();
        if(id==2) {
            if(world!=null && world.isRemote)return clientDuration;
            ProcessingRecipe recipe=recipe(getStackInSlot(0));return recipe==null?1:recipe.ticks;
        }
        return 0;
    }
    @Override public void setField(int id,int value) {
        if(id==0)progress=Math.max(0,value);
        else if(id==1)energy.restore(value);
        else if(id==2)clientDuration=Math.max(1,value);
    }
    @Override public int getFieldCount() {return 3;}
    @Override public boolean hasCapability(Capability<?> capability,@Nullable EnumFacing side) {
        return capability==CapabilityEnergy.ENERGY || super.hasCapability(capability,side);
    }
    @Override public <T> T getCapability(Capability<T> capability,@Nullable EnumFacing side) {
        if(capability==CapabilityEnergy.ENERGY)return CapabilityEnergy.ENERGY.cast(energy);
        return super.getCapability(capability,side);
    }
    @Override public NBTTagCompound writeToNBT(NBTTagCompound tag) {
        super.writeToNBT(tag);tag.setInteger("Energy",energy.getEnergyStored());tag.setInteger("Progress",progress);
        tag.setLong("Spent",spent);tag.setLong("Completed",completed);tag.setLong("SecondaryProduced",secondaryProduced);return tag;
    }
    @Override public void readFromNBT(NBTTagCompound tag) {
        super.readFromNBT(tag);energy.restore(tag.getInteger("Energy"));
        ProcessingRecipe savedRecipe=recipe(getStackInSlot(0));
        progress=savedRecipe==null?0:Math.max(0,Math.min(savedRecipe.ticks-1,tag.getInteger("Progress")));
        spent=tag.getLong("Spent");completed=tag.getLong("Completed");secondaryProduced=tag.getLong("SecondaryProduced");
    }
}
