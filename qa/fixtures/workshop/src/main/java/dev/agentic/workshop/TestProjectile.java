package dev.agentic.workshop;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.projectile.EntityThrowable;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.DamageSource;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.world.World;

/** Vanilla throwable movement/collision with a small, server-authoritative damage effect. */
public final class TestProjectile extends EntityThrowable {
    private int flightTicks;
    public TestProjectile(World world) {super(world);}
    public TestProjectile(World world,EntityLivingBase owner) {super(world,owner);}
    @Override protected void onImpact(RayTraceResult hit) {
        if(world.isRemote || isDead)return;
        if(hit.entityHit!=null && hit.entityHit!=getThrower())
            hit.entityHit.attackEntityFrom(DamageSource.causeThrownDamage(this,getThrower()),4F);
        setDead();
    }
    @Override public void onUpdate() {
        super.onUpdate();
        if(!world.isRemote && ++flightTicks>=60)setDead();
    }
    @Override public void writeEntityToNBT(NBTTagCompound tag) {
        super.writeEntityToNBT(tag);tag.setInteger("TrialFlightTicks",flightTicks);
    }
    @Override public void readEntityFromNBT(NBTTagCompound tag) {
        super.readEntityFromNBT(tag);flightTicks=Math.max(0,Math.min(60,tag.getInteger("TrialFlightTicks")));
    }
}
