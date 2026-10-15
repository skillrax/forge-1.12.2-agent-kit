package com.example.networkstarter;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
public final class SinkTile extends EnergyTile {
    public static final int LIMIT=NetworkSettings.SINK_LIMIT;
    private int requested=NetworkSettings.SINK_DEFAULT,shownLast;
    public long consumed;
    public SinkTile(){super(0);}
    protected boolean input(EnumFacing f){return f!=null;}
    public int stored(){return 0;}public int capacity(){return 0;}
    public int requested(){return requested;}
    public void recordTick(){shownLast=TickLedger.used(world,pos,"sink");}
    protected int extract(int n,boolean sim,EnumFacing f){return 0;}
    protected int receive(int n,boolean sim,EnumFacing f){int a=Math.min(n,TickLedger.remaining(world,pos,"sink",requested));if(!sim && a>0){TickLedger.spend(world,pos,"sink",a);consumed+=a;markDirty();}return a;}
    // Bounded integer action carried by our validated custom menu packet.
    public boolean control(int action){if(action<100 || action>100+LIMIT)return false;requested=action-100;markDirty();return true;}
    protected int display(int i){return i==2?requested:i==3?shownLast:super.display(i);}
    protected void applyDisplay(int i,int v){if(i==2)requested=v;else if(i==3)shownLast=v;}
    public NBTTagCompound writeToNBT(NBTTagCompound n){super.writeToNBT(n);n.setInteger("Request",requested);n.setLong("Consumed",consumed);return n;}
    public void readFromNBT(NBTTagCompound n){super.readFromNBT(n);requested=Math.max(0,Math.min(LIMIT,n.getInteger("Request")));consumed=Math.max(0,n.getLong("Consumed"));}
}
