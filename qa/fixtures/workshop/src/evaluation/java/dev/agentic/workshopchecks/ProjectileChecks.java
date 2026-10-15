package dev.agentic.workshopchecks;

import java.util.ArrayList;
import java.util.UUID;
import com.mojang.authlib.GameProfile;
import dev.agentic.workshop.*;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityList;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.*;
import net.minecraft.server.management.PlayerInteractionManager;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumHand;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldServer;
import net.minecraftforge.fml.common.registry.EntityRegistry;

/** Collisions/movement are driven by ordinary world ticks, not manual onImpact calls. */
public final class ProjectileChecks {
    private final WorldServer world;
    private int assertions,ticks;
    private EntityPlayerMP owner;
    private TestProjectile entityShot,wallShot,lifetime;
    private TraderNpc target,behindWall;
    private final BlockPos wall=new BlockPos(10,77,12);
    public ProjectileChecks(WorldServer world) {this.world=world;}
    private void require(boolean ok,String message) {
        assertions++;if(!ok)throw new IllegalStateException("WORKSHOP_ASSERTIONS_FAIL: Projectile "+message);
    }
    private EntityPlayerMP player(String name,double x) {
        EntityPlayerMP p=new EntityPlayerMP(world.getMinecraftServer(),world,
            new GameProfile(UUID.nameUUIDFromBytes(name.getBytes(java.nio.charset.StandardCharsets.UTF_8)),name),new PlayerInteractionManager(world));
        p.connection=new NetHandlerPlayServer(world.getMinecraftServer(),new NetworkManager(EnumPacketDirection.SERVERBOUND),p);
        p.setPosition(x,76,12);p.rotationYaw=-90;p.rotationPitch=0;p.onGround=true;
        p.inventory.setInventorySlotContents(0,new ItemStack(Content.LAUNCHER));return p;
    }
    private TraderNpc target(double x) {
        TraderNpc npc=new TraderNpc(world);npc.setNoAI(true);npc.setPosition(x,76,12);require(world.spawnEntity(npc),"target spawn failed");return npc;
    }
    private int shots() {int n=0;for(Entity entity:world.loadedEntityList)if(entity instanceof TestProjectile && !entity.isDead)n++;return n;}
    private TestProjectile owned(EntityPlayerMP p) {
        for(Entity entity:world.loadedEntityList)if(entity instanceof TestProjectile && ((TestProjectile)entity).getThrower()==p && !entity.isDead)return (TestProjectile)entity;
        throw new IllegalStateException("WORKSHOP_ASSERTIONS_FAIL: Projectile launched entity absent");
    }
    public int liveStart() {
        for(Entity entity:new ArrayList<Entity>(world.loadedEntityList))if(entity instanceof TestProjectile)entity.setDead();
        require(EntityRegistry.instance().lookupModSpawn(TestProjectile.class,false)!=null,"tracking registration missing");
        require(EntityList.createEntityByIDFromName(new ResourceLocation("kitworkshop","test_projectile"),world) instanceof TestProjectile,"registered constructor missing");
        target=target(-6);behindWall=target(14);
        for(int y=76;y<=78;y++)world.setBlockState(new BlockPos(10,y,12),Blocks.STONE.getDefaultState(),2);
        owner=player("ShotOwner",-12);int before=shots();
        require(Content.LAUNCHER.onItemRightClick(world,owner,EnumHand.MAIN_HAND).getType()==EnumActionResult.SUCCESS,"launch rejected");
        require(shots()==before+1 && owner.getHeldItemMainhand().getCount()==1,"launch count/item conservation");entityShot=owned(owner);
        require(entityShot.getThrower()==owner && entityShot.motionX>1.4 && Math.abs(entityShot.motionZ)<.001,"owner/direction setup");
        require(Content.LAUNCHER.onItemRightClick(world,owner,EnumHand.MAIN_HAND).getType()==EnumActionResult.FAIL && shots()==before+1,"cooldown allowed duplicate");
        require(Content.LAUNCHER.onItemRightClick(world,owner,EnumHand.OFF_HAND).getType()==EnumActionResult.PASS && shots()==before+1,"offhand launched");
        EntityPlayerMP other=player("ShotOther",4);
        require(owner.getCooldownTracker().hasCooldown(Content.LAUNCHER),"owner cooldown missing");
        require(Content.LAUNCHER.onItemRightClick(world,other,EnumHand.MAIN_HAND).getType()==EnumActionResult.SUCCESS,"other player's cooldown leaked");wallShot=owned(other);
        for(int i=0;i<10;i++)owner.getCooldownTracker().tick();
        require(!owner.getCooldownTracker().hasCooldown(Content.LAUNCHER),"cooldown did not expire");
        EntityPlayerMP dead=player("ShotDead",-16);dead.setHealth(0);int count=shots();
        require(Content.LAUNCHER.onItemRightClick(world,dead,EnumHand.MAIN_HAND).getType()==EnumActionResult.FAIL && shots()==count,"dead player launched");
        lifetime=new TestProjectile(world);lifetime.setPosition(0,190,0);lifetime.motionX=.1;
        require(world.spawnEntity(lifetime),"lifetime spawn failed");
        return assertions;
    }
    public void tick() {
        ticks++;
        if(ticks==10) {
            require(lifetime.ticksExisted>=9 && lifetime.posX>.5 && lifetime.posY<189,"actual trajectory/gravity missing: age="+lifetime.ticksExisted+", y="+lifetime.posY);
            NBTTagCompound saved=new NBTTagCompound();require(lifetime.writeToNBTOptional(saved),"projectile entity NBT ID missing");
            Entity restored=EntityList.createEntityFromNBT(saved,world);NBTTagCompound again=new NBTTagCompound();restored.writeToNBTOptional(again);
            require(restored instanceof TestProjectile && again.getInteger("TrialFlightTicks")==saved.getInteger("TrialFlightTicks")
                && saved.getInteger("TrialFlightTicks")>=9,"flight age NBT round-trip");
        }
        if(ticks==12) {
            require(entityShot.isDead && target.getHealth()==16F,"entity hit damage/cleanup: health="+target.getHealth()+", age="+entityShot.ticksExisted+", dead="+entityShot.isDead);
            require(target.getRevengeTarget()==owner,"damage owner attribution");
            require(wallShot.isDead && behindWall.getHealth()==20F,"wall collision/occlusion: health="+behindWall.getHealth()+", dead="+wallShot.isDead);
            require(world.getBlockState(wall).getBlock()==Blocks.STONE,"impact changed wall");
        }
        if(ticks==65)require(lifetime.isDead && lifetime.ticksExisted>=59 && lifetime.ticksExisted<=61,"active lifetime expiry: age="+lifetime.ticksExisted);
    }
    public int finish() {
        require(ticks>=135 && entityShot.isDead && wallShot.isDead && lifetime.isDead,"final lifecycle cleanup");
        require(target.getHealth()==16F && behindWall.getHealth()==20F,"duplicate or occluded damage");
        require(shots()==0,"surviving projectiles after lifetime");target.setDead();behindWall.setDead();
        System.out.println("PROJECTILE_LIVE_PASS assertions="+assertions+" ticks="+ticks);return assertions;
    }
}
