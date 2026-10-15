package dev.agentic.workshop;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.entity.player.*;
import net.minecraft.init.Blocks;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.world.WorldServer;
public final class PortalTravel {
    private static final String RETURN="KitWorkshopReturn",COOLDOWN="KitWorkshopPortalCooldown";
    private PortalTravel() {}
    private static void tell(EntityPlayerMP player,String text) {player.sendMessage(new TextComponentString("Test Portal: "+text));}
    public static boolean safe(WorldServer world,BlockPos feet) {
        if(feet.getY()<1 || feet.getY()>253 || !world.getWorldBorder().contains(feet))return false;
        Block floor=world.getBlockState(feet.down()).getBlock();Material material=world.getBlockState(feet.down()).getMaterial();
        return world.getBlockState(feet.down()).isFullBlock() && material!=Material.LEAVES && material!=Material.WOOD
            && floor!=Blocks.MAGMA && floor!=Blocks.CACTUS && world.isAirBlock(feet) && world.isAirBlock(feet.up());
    }
    public static BlockPos landing(WorldServer world,BlockPos preferred) {
        for(int radius=0;radius<=8;radius++)for(int x=-radius;x<=radius;x++)for(int z=-radius;z<=radius;z++) {
            if(Math.max(Math.abs(x),Math.abs(z))!=radius)continue;
            BlockPos column=preferred.add(x,0,z);
            if(!world.getWorldBorder().contains(column))continue;
            for(int offset=0;offset<=4;offset++) {
                BlockPos above=column.up(offset);if(safe(world,above))return above;
                if(offset>0 && safe(world,column.down(offset)))return column.down(offset);
            }
            BlockPos surface=world.getHeight(column);if(safe(world,surface))return surface;
        }
        return null;
    }
    public static boolean travel(EntityPlayerMP player) {
        if(player.world.isRemote || !player.isEntityAlive())return false;
        if(player.isRiding() || player.isBeingRidden()) {tell(player,"Dismount first.");return false;}
        if(player.openContainer!=player.inventoryContainer) {tell(player,"Close your menu first.");return false;}
        if(player.dimension!=0 && player.dimension!=TrialDimension.id) {tell(player,"Use this portal in the Overworld or Test Dimension.");return false;}
        NBTTagCompound data=player.getEntityData();
        long now=player.getServer().getWorld(0).getTotalWorldTime();
        if(data.getLong(COOLDOWN)>now) {tell(player,"Wait2 seconds before using the portal again.");return false;}
        boolean entering=player.dimension==0;
        NBTTagCompound persisted=data.getCompoundTag(EntityPlayer.PERSISTED_NBT_TAG);
        NBTTagCompound saved=persisted.getCompoundTag(RETURN);
        WorldServer destination=player.getServer().getWorld(entering?TrialDimension.id:0);
        BlockPos preferred=entering?new BlockPos(2,64,0):saved.hasKey("Position",4)?BlockPos.fromLong(saved.getLong("Position")):destination.getSpawnPoint();
        final BlockPos target=landing(destination,preferred);
        if(target==null) {tell(player,"No clear supported landing within8 blocks; travel cancelled.");return false;}
        final float yaw=entering?player.rotationYaw:saved.hasKey("Yaw",5)?saved.getFloat("Yaw"):player.rotationYaw;
        final float pitch=entering?player.rotationPitch:saved.hasKey("Pitch",5)?saved.getFloat("Pitch"):player.rotationPitch;
        NBTTagCompound returnPoint=new NBTTagCompound();returnPoint.setLong("Position",player.getPosition().toLong());
        returnPoint.setFloat("Yaw",player.rotationYaw);returnPoint.setFloat("Pitch",player.rotationPitch);
        player.closeScreen();
        player.changeDimension(destination.provider.getDimension(),(world,entity,rotation)-> {
            entity.setLocationAndAngles(target.getX()+.5,target.getY(),target.getZ()+.5,yaw,pitch);
            entity.motionX=entity.motionY=entity.motionZ=0;entity.fallDistance=0;
        });
        if(player.dimension!=destination.provider.getDimension()) {tell(player,"Travel was cancelled by another mod.");return false;}
        if(entering) {persisted.setTag(RETURN,returnPoint);data.setTag(EntityPlayer.PERSISTED_NBT_TAG,persisted);}
        data.setLong(COOLDOWN,now+40);
        tell(player,entering?"Entered Test Dimension (ID "+TrialDimension.id+"). Return portal:0 64 0.":"Returned to the Overworld.");
        return true;
    }
}