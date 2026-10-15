package dev.agentic.rfnetwork;
import net.minecraft.item.ItemStack;
import net.minecraft.init.Items;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntityFurnace;
import net.minecraft.util.EnumFacing;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.fluids.*;
import net.minecraftforge.fluids.capability.*;
import net.minecraftforge.items.*;
import net.minecraftforge.items.wrapper.InvWrapper;
public final class GeneratorTile extends EnergyTile implements net.minecraft.inventory.ISidedInventory {
    public static final int TANK=8000;
    private int lava;
    private long credit; // prepaid fuel, NOT stored RF. Four units/base RF, three/upgraded RF.
    public long generated,charged;
    private int clientRate=1000;
    public GeneratorTile(){super(2);}
    public boolean upgraded(){return getStackInSlot(1).getItem()==Content.UPGRADE;}
    public int rate(){return upgraded()?2000:1000;}
    private int cost(){return upgraded()?3:4;}
    public int fuelCost(){return cost();}
    public void refund(int amount,int fuelCost){if(amount>0){long n=(long)amount*fuelCost;credit+=n;charged-=n;generated-=amount;markDirty();}}
    public long credit(){return credit;}
    public int lava(){return lava;}
    public int stored(){return 0;}public int capacity(){return 0;}
    protected boolean output(EnumFacing f){return f!=null;}
    protected int receive(int n,boolean sim,EnumFacing f){return 0;}
    private int burn(){ItemStack s=getStackInSlot(0);return isItemValidForSlot(0,s)?TileEntityFurnace.getItemBurnTime(s):0;}
    private long available(){return credit+(long)burn()*4000L+(long)lava*4000L;}
    protected int extract(int n,boolean sim,EnumFacing f){
        int a=(int)Math.min(Math.min(n,TickLedger.remaining(world,pos,"gen",rate())),available()/cost());
        if(!sim && a>0){
            long charge=(long)a*cost();
            if(credit<charge && burn()>0){ItemStack s=getStackInSlot(0);credit+=(long)burn()*4000L;ItemStack container=s.getItem().hasContainerItem(s)?s.getItem().getContainerItem(s):ItemStack.EMPTY;s.shrink(1);if(s.isEmpty())setInventorySlotContents(0,container);else if(!container.isEmpty())net.minecraft.inventory.InventoryHelper.spawnItemStack(world,pos.getX()+.5,pos.getY()+.5,pos.getZ()+.5,container);}
            if(credit<charge){int mb=(int)((charge-credit+3999)/4000);lava-=mb;credit+=(long)mb*4000L;}
            credit-=charge;charged+=charge;generated+=a;TickLedger.spend(world,pos,"gen",a);markDirty();
        }return a;
    }
    public boolean isItemValidForSlot(int i,ItemStack s){return !s.isEmpty() && (i==1?s.getItem()==Content.UPGRADE:i==0 && s.getItem()!=Items.LAVA_BUCKET && TileEntityFurnace.getItemBurnTime(s)>0);}
    public int[] getSlotsForFace(EnumFacing f){return new int[]{0};}
    public boolean canInsertItem(int slot,ItemStack s,EnumFacing f){return slot==0 && isItemValidForSlot(0,s);}
    public boolean canExtractItem(int slot,ItemStack s,EnumFacing f){return false;}
    private final IItemHandler items=new InvWrapper(this){
        public ItemStack insertItem(int slot,ItemStack stack,boolean sim){return slot==0?super.insertItem(slot,stack,sim):stack;}
        public ItemStack extractItem(int slot,int amount,boolean sim){return ItemStack.EMPTY;}
    };
    private final IFluidHandler fluids=new IFluidHandler(){
        public IFluidTankProperties[] getTankProperties(){return new IFluidTankProperties[]{new FluidTankProperties(lava==0?null:new FluidStack(FluidRegistry.LAVA,lava),TANK,true,false)};}
        public int fill(FluidStack f,boolean commit){if(f==null || f.getFluid()!=FluidRegistry.LAVA)return 0;int a=Math.max(0,Math.min(f.amount,TANK-lava));if(commit && a>0){lava+=a;markDirty();}return a;}
        public FluidStack drain(FluidStack f,boolean commit){return null;}public FluidStack drain(int n,boolean commit){return null;}
    };
    public IFluidHandler fluids(){return fluids;}
    public boolean hasCapability(Capability<?> c,EnumFacing f){return c==CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY || c==CapabilityItemHandler.ITEM_HANDLER_CAPABILITY || super.hasCapability(c,f);}
    public <T>T getCapability(Capability<T> c,EnumFacing f){return c==CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY?CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY.cast(fluids):c==CapabilityItemHandler.ITEM_HANDLER_CAPABILITY?CapabilityItemHandler.ITEM_HANDLER_CAPABILITY.cast(items):super.getCapability(c,f);}
    protected int display(int i){return i==2?lava:i==3?(world!=null && world.isRemote?clientRate:rate()):i==4?(int)Math.min(Integer.MAX_VALUE,credit):super.display(i);}
    protected void applyDisplay(int i,int v){if(i==2)lava=v;else if(i==3)clientRate=v;else if(i==4)credit=v;}
    public NBTTagCompound writeToNBT(NBTTagCompound n){super.writeToNBT(n);n.setInteger("Lava",lava);n.setLong("FuelCredit",credit);n.setLong("Generated",generated);n.setLong("Charged",charged);return n;}
    public void readFromNBT(NBTTagCompound n){super.readFromNBT(n);lava=Math.max(0,Math.min(TANK,n.getInteger("Lava")));credit=Math.max(0,n.getLong("FuelCredit"));generated=Math.max(0,n.getLong("Generated"));charged=Math.max(0,n.getLong("Charged"));}
}
