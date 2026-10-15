package dev.agentic.crafting;

import javax.annotation.Nullable;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.*;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.*;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.*;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.energy.CapabilityEnergy;

// Inputs 0..8 are real items; outputs 9..18 include ALL recipe remainders.
public final class ProcessorTile extends InventoryTile implements ITickable {
    public static final int TICKS=20, RF=4;
    public final EnergyBuffer energy=new EnergyBuffer(4000,128,0,this::markDirty);
    public final InventoryBasic pattern=new InventoryBasic("Ghost pattern",false,9) {
        @Override public void markDirty() {resetWork();ProcessorTile.this.markDirty();}
    };
    private int progress,status;
    private boolean enabled,ignoreNBT,ignoreMetadata;
    private NBTTagCompound workInputs=new NBTTagCompound();
    public long spent,completed;
    public ProcessorTile() {super(19);}
    @Override public String getName() {return "tile.craftingproof.processor.name";}
    @Override public void markDirty() {if(world!=null && !world.isRemote)world.markChunkDirty(pos,this);}
    private void resetWork() {progress=0;workInputs=new NBTTagCompound();}
    public void select(int slot,ItemStack cursor) {
        ItemStack ghost=cursor.copy();if(!ghost.isEmpty())ghost.setCount(1);
        pattern.setInventorySlotContents(slot,ghost);
    }
    public void control(int button) {
        if(button==0)enabled=!enabled;
        if(button==1){pattern.clear();ignoreNBT=false;ignoreMetadata=false;resetWork();}
        if(button==2){ignoreNBT=!ignoreNBT;resetWork();}
        if(button==3){ignoreMetadata=!ignoreMetadata;resetWork();}
        markDirty();
    }
    private static final Container SILENT=new Container() {
        @Override public boolean canInteractWith(EntityPlayer player) {return false;}
    };
    private InventoryCrafting grid(boolean ghosts) {
        InventoryCrafting grid=new InventoryCrafting(SILENT,3,3);
        for(int i=0;i<9;i++) {
            ItemStack s=(ghosts?pattern.getStackInSlot(i):getStackInSlot(i)).copy();
            if(!s.isEmpty())s.setCount(1);grid.setInventorySlotContents(i,s);
        }
        return grid;
    }
    public IRecipe selectedRecipe() {return world==null?null:CraftingManager.findMatchingRecipe(grid(true),world);}
    public ItemStack preview() {
        InventoryCrafting view=grid(supplied()?false:true);
        IRecipe r=world==null?null:CraftingManager.findMatchingRecipe(view,world);
        // Ignore incidental tags on the ghost example for the idle preview only.
        // Real inputs are NEVER stripped or rewritten before recipe validation.
        if(r==null && ignoreNBT) {
            view=grid(true);for(int i=0;i<9;i++)view.getStackInSlot(i).setTagCompound(null);
            r=world==null?null:CraftingManager.findMatchingRecipe(view,world);
        }
        return r==null?ItemStack.EMPTY:r.getCraftingResult(view);
    }
    private boolean supplied() {
        for(int i=0;i<9;i++) {
            ItemStack a=pattern.getStackInSlot(i),b=getStackInSlot(i);
            if(a.isEmpty()!=b.isEmpty())return false;
            if(!a.isEmpty() && (a.getItem()!=b.getItem()
                || !ignoreMetadata && a.getMetadata()!=b.getMetadata()
                || !ignoreNBT && !ItemStack.areItemStackTagsEqual(a,b)))return false;
        }
        return true;
    }
    private NBTTagCompound inputSnapshot() {
        NBTTagCompound tag=new NBTTagCompound();
        for(int i=0;i<9;i++)tag.setTag("Slot"+i,getStackInSlot(i).writeToNBT(new NBTTagCompound()));return tag;
    }
    private boolean insert(NonNullList<ItemStack> planned,ItemStack offered) {
        ItemStack remaining=offered.copy();
        // Merge first, then use empty slots; respect inventory AND item stack limits.
        for(int pass=0;pass<2;pass++)for(int i=0;i<planned.size();i++) {
            if(remaining.isEmpty())return true;
            ItemStack current=planned.get(i);
            if(pass==0 && !current.isEmpty() && ItemStack.areItemsEqual(current,remaining)
                    && ItemStack.areItemStackTagsEqual(current,remaining)) {
                int n=Math.min(remaining.getCount(),Math.max(0,Math.min(64,current.getMaxStackSize())-current.getCount()));
                current.grow(n);remaining.shrink(n);
            } else if(pass==1 && current.isEmpty()) {
                int n=Math.min(remaining.getCount(),Math.min(64,remaining.getMaxStackSize()));
                ItemStack placed=remaining.copy();placed.setCount(n);planned.set(i,placed);remaining.shrink(n);
            }
        }
        return remaining.isEmpty();
    }
    private NonNullList<ItemStack> plan(IRecipe recipe,InventoryCrafting grid) {
        ItemStack result=recipe.getCraftingResult(grid);if(result.isEmpty())return null;
        NonNullList<ItemStack> out=NonNullList.withSize(10,ItemStack.EMPTY);
        for(int i=0;i<10;i++)out.set(i,getStackInSlot(9+i).copy());
        if(!insert(out,result))return null;
        NonNullList<ItemStack> remainder=recipe.getRemainingItems(grid);if(remainder.size()!=9)return null;
        for(ItemStack stack:remainder)if(!insert(out,stack))return null;return out;
    }
    @Override public void update() {
        if(world==null || world.isRemote)return;
        NBTTagCompound now=inputSnapshot();
        if(!now.equals(workInputs)){resetWork();workInputs=now;markDirty();}
        if(!enabled){status=0;return;}
        InventoryCrafting actual=grid(false);
        if(!supplied()){status=2;return;}
        // Fuzzy matching permits another registered variant using these same item
        // identities/grid positions. Its real input data still determines validity,
        // result and remainders; no recipe bypass or NBT sanitization occurs.
        IRecipe recipe=(ignoreNBT || ignoreMetadata)?CraftingManager.findMatchingRecipe(actual,world):selectedRecipe();
        if(recipe==null || !recipe.matches(actual,world)){status=6;return;}
        NonNullList<ItemStack> planned=plan(recipe,actual);if(planned==null){status=3;return;}
        if(!energy.spend(RF)){status=4;return;}
        status=5;spent+=RF;
        if(++progress>=TICKS) {
            // No external handler calls between planning and commit on the server thread.
            for(int i=0;i<9;i++)if(!getStackInSlot(i).isEmpty())getStackInSlot(i).shrink(1);
            for(int i=0;i<10;i++)stacks.set(9+i,planned.get(i));
            completed++;resetWork();workInputs=inputSnapshot();
        }
        markDirty();
    }
    @Override public boolean isItemValidForSlot(int slot,ItemStack stack) {return slot<9;}
    @Override public int getField(int id) {return id==0?progress:id==1?energy.getEnergyStored():id==2?(enabled?1:0):id==3?status:id==4?(ignoreNBT?1:0):(ignoreMetadata?1:0);}
    @Override public void setField(int id,int value) {
        if(id==0)progress=value;else if(id==1)energy.restore(value);else if(id==2)enabled=value!=0;else if(id==3)status=value;else if(id==4)ignoreNBT=value!=0;else if(id==5)ignoreMetadata=value!=0;
    }
    @Override public int getFieldCount() {return 6;}
    @Override public boolean hasCapability(Capability<?> cap,@Nullable EnumFacing side) {return cap==CapabilityEnergy.ENERGY || super.hasCapability(cap,side);}
    @Override public <T> T getCapability(Capability<T> cap,@Nullable EnumFacing side) {
        return cap==CapabilityEnergy.ENERGY?CapabilityEnergy.ENERGY.cast(energy):super.getCapability(cap,side);
    }
    @Override public NBTTagCompound writeToNBT(NBTTagCompound tag) {
        super.writeToNBT(tag);NBTTagCompound ghosts=new NBTTagCompound();
        for(int i=0;i<9;i++)ghosts.setTag("Slot"+i,pattern.getStackInSlot(i).writeToNBT(new NBTTagCompound()));
        tag.setTag("Pattern",ghosts);tag.setTag("WorkInputs",workInputs);tag.setInteger("Progress",progress);
        tag.setInteger("Energy",energy.getEnergyStored());tag.setBoolean("Enabled",enabled);
        tag.setBoolean("IgnoreNBT",ignoreNBT);tag.setBoolean("IgnoreMetadata",ignoreMetadata);
        tag.setLong("Spent",spent);tag.setLong("Completed",completed);return tag;
    }
    @Override public void readFromNBT(NBTTagCompound tag) {
        super.readFromNBT(tag);
        for(int i=0;i<9;i++)select(i,new ItemStack(tag.getCompoundTag("Pattern").getCompoundTag("Slot"+i)));
        workInputs=tag.getCompoundTag("WorkInputs").copy();progress=Math.max(0,Math.min(TICKS-1,tag.getInteger("Progress")));
        energy.restore(tag.getInteger("Energy"));enabled=tag.getBoolean("Enabled");
        ignoreNBT=tag.getBoolean("IgnoreNBT");ignoreMetadata=tag.getBoolean("IgnoreMetadata");
        spent=tag.getLong("Spent");completed=tag.getLong("Completed");
    }
}
