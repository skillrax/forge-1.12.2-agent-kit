package dev.agentic.playermenu;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;

/** Owned by vanilla player UUID; persisted by normal player saves, including logout. */
public final class PersonalState {
    public static final String KEY="PersonalMenuProof";
    public static final int MAX=1000000;
    private PersonalState() {}
    private static NBTTagCompound state(EntityPlayer p) {
        return p.getEntityData().getCompoundTag(EntityPlayer.PERSISTED_NBT_TAG).getCompoundTag(KEY);
    }
    public static int counter(EntityPlayer p) {return Math.max(0,Math.min(MAX,state(p).getInteger("Counter")));}
    public static boolean enabled(EntityPlayer p) {return state(p).getBoolean("Enabled");}
    /** Action 0 reads, 1 increments, 2 toggles, 3 resets both. Invalid actions cannot mutate. */
    public static int apply(EntityPlayer p,int action) {
        if(p.world.isRemote)throw new IllegalStateException("Personal state belongs to the server");
        if(action<0 || action>3)return 4;
        if(action==0)return 0;
        int count=counter(p);boolean enabled=enabled(p);int status=action;
        if(action==1){if(count==MAX)status=5;else count++;}
        if(action==2)enabled=!enabled;
        if(action==3){count=0;enabled=false;}
        NBTTagCompound root=p.getEntityData();
        NBTTagCompound persisted=root.getCompoundTag(EntityPlayer.PERSISTED_NBT_TAG);
        NBTTagCompound value=new NBTTagCompound();value.setInteger("Counter",count);value.setBoolean("Enabled",enabled);
        persisted.setTag(KEY,value);root.setTag(EntityPlayer.PERSISTED_NBT_TAG,persisted);
        return status;
    }
}
