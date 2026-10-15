package dev.agentic.itemnetwork;

import net.minecraft.inventory.InventoryBasic;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;

/** No public inventory capability: only explicitly configured source faces extract. */
public final class DuctTile extends InventoryTile {
    public static final int TRANSIT=0,SOURCE=1,DESTINATION=2;
    public int role,face=EnumFacing.WEST.ordinal();
    public boolean enabled=true,ignoreNBT,ignoreMetadata;
    public long delivered;
    public BlockPos origin;
    public final InventoryBasic filter=new InventoryBasic("Ghost filter",false,1);
    public DuctTile(){super(1);}
    @Override public String getName(){return "tile.itemnetwork.duct.name";}
    @Override public void markDirty(){if(world!=null && !world.isRemote)new LoadedView(world).dirty(this);}
    @Override public boolean isUsableByPlayer(EntityPlayer p){return world!=null && !isInvalid() && Network.loaded(world,pos)==this && p.getDistanceSq(pos.getX()+.5,pos.getY()+.5,pos.getZ()+.5)<=64;}
    @Override public boolean isItemValidForSlot(int i,ItemStack s){return false;}
    public EnumFacing direction(){return EnumFacing.values()[face];}
    public void select(ItemStack cursor){ItemStack s=cursor.copy();if(!s.isEmpty())s.setCount(1);filter.setInventorySlotContents(0,s);markDirty();}
    public boolean matches(ItemStack s){
        ItemStack f=filter.getStackInSlot(0);
        return !s.isEmpty() && (f.isEmpty() || f.getItem()==s.getItem()
            && (ignoreMetadata || f.getMetadata()==s.getMetadata())
            && (ignoreNBT || ItemStack.areItemStackTagsEqual(f,s)));
    }
    public void control(int b){
        if(b==0)role=(role+1)%3;
        if(b==1)face=(face+1)%6;
        if(b==2)enabled=!enabled;
        if(b==3)ignoreNBT=!ignoreNBT;
        if(b==4)ignoreMetadata=!ignoreMetadata;
        markDirty();
    }
    // Ownership is independent of role/filter: only destination filters govern buffered delivery.
    public void own(ItemStack s){if(!isEmpty())throw new IllegalStateException("Buffer occupied");origin=pos.offset(direction());stacks.set(0,s.copy());markDirty();}
    @Override public int getFieldCount(){return 7;}
    @Override public int getField(int i){switch(i){case 0:return role;case 1:return face;case 2:return enabled?1:0;case 3:return ignoreNBT?1:0;case 4:return ignoreMetadata?1:0;case 5:return getStackInSlot(0).getCount();case 6:return (int)Math.min(32767,delivered);default:return 0;}}
    @Override public void setField(int i,int n){switch(i){case 0:role=n;break;case 1:face=n;break;case 2:enabled=n!=0;break;case 3:ignoreNBT=n!=0;break;case 4:ignoreMetadata=n!=0;break;case 6:delivered=n;}}
    @Override public NBTTagCompound writeToNBT(NBTTagCompound n){super.writeToNBT(n);n.setInteger("Role",role);n.setInteger("Face",face);n.setBoolean("Enabled",enabled);n.setBoolean("IgnoreNBT",ignoreNBT);n.setBoolean("IgnoreMetadata",ignoreMetadata);n.setLong("Delivered",delivered);if(origin!=null)n.setLong("Origin",origin.toLong());n.setTag("Filter",filter.getStackInSlot(0).writeToNBT(new NBTTagCompound()));return n;}
    @Override public void readFromNBT(NBTTagCompound n){super.readFromNBT(n);role=Math.max(0,Math.min(2,n.getInteger("Role")));face=Math.max(0,Math.min(5,n.getInteger("Face")));enabled=n.getBoolean("Enabled");ignoreNBT=n.getBoolean("IgnoreNBT");ignoreMetadata=n.getBoolean("IgnoreMetadata");delivered=n.getLong("Delivered");origin=n.hasKey("Origin")?BlockPos.fromLong(n.getLong("Origin")):null;filter.setInventorySlotContents(0,new ItemStack(n.getCompoundTag("Filter")));}
}
