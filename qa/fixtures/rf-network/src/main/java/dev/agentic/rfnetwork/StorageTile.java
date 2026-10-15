package dev.agentic.rfnetwork;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
public final class StorageTile extends EnergyTile {
    public static final int CAPACITY=2000000;
    private int rf;
    private final int[] faces={1,1,1,1,1,2}; // 0 disabled, 1 input, 2 output. World directions.
    public StorageTile(){super(0);}
    public int role(EnumFacing f){return f==null?0:faces[f.getIndex()];}
    protected boolean input(EnumFacing f){return role(f)==1;}
    protected boolean output(EnumFacing f){return role(f)==2;}
    public int stored(){return rf;}public int capacity(){return CAPACITY;}
    public void refund(int amount,int fuelCost){if(amount>0){rf+=amount;markDirty();}}
    protected int receive(int n,boolean sim,EnumFacing f){int a=Math.min(n,Math.min(CAPACITY-rf,TickLedger.remaining(world,pos,"in",60000)));if(!sim && a>0){rf+=a;TickLedger.spend(world,pos,"in",a);markDirty();}return a;}
    protected int extract(int n,boolean sim,EnumFacing f){int a=Math.min(n,Math.min(rf,TickLedger.remaining(world,pos,"out",20000)));if(!sim && a>0){rf-=a;TickLedger.spend(world,pos,"out",a);markDirty();}return a;}
    public boolean control(int action){if(action<0 || action>=6)return false;faces[action]=(faces[action]+1)%3;markDirty();return true;}
    protected int display(int i){return i<2?super.display(i):i>=2 && i<8?faces[i-2]:i==8 && world!=null?TickLedger.used(world,pos,"out"):0;}
    protected void applyDisplay(int i,int v){if(i==0)rf=v;else if(i>=2 && i<8)faces[i-2]=v;}
    public NBTTagCompound writeToNBT(NBTTagCompound n){super.writeToNBT(n);n.setInteger("RF",rf);n.setIntArray("Faces",faces);return n;}
    public void readFromNBT(NBTTagCompound n){super.readFromNBT(n);rf=Math.max(0,Math.min(CAPACITY,n.getInteger("RF")));int[] f=n.getIntArray("Faces");if(f.length==6)for(int i=0;i<6;i++)faces[i]=Math.max(0,Math.min(2,f[i]));}
}
