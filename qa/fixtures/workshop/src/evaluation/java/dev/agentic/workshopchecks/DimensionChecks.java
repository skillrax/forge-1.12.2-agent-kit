package dev.agentic.workshopchecks;
import java.util.UUID;
import com.mojang.authlib.GameProfile;
import dev.agentic.workshop.*;
import net.minecraft.entity.player.*;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.*;
import net.minecraft.server.management.PlayerInteractionManager;
import net.minecraft.tileentity.TileEntityChest;
import net.minecraft.util.*;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.DimensionManager;
public final class DimensionChecks {
    private final WorldServer home,realm;
    private int assertions;
    private final BlockPos first=new BlockPos(100,80,100),second=new BlockPos(120,80,120),chest=new BlockPos(10,64,10);
    public DimensionChecks(WorldServer home) {this.home=home;realm=home.getMinecraftServer().getWorld(TrialDimension.id);}
    private void require(boolean condition,String message) {
        assertions++;if(!condition)throw new IllegalStateException("WORKSHOP_ASSERTIONS_FAIL: Dimension "+message);
    }
    private EntityPlayerMP player(String name,WorldServer world) {
        EntityPlayerMP player=new EntityPlayerMP(home.getMinecraftServer(),world,
            new GameProfile(UUID.nameUUIDFromBytes(name.getBytes(java.nio.charset.StandardCharsets.UTF_8)),name),new PlayerInteractionManager(world));
        player.connection=new NetHandlerPlayServer(home.getMinecraftServer(),new NetworkManager(EnumPacketDirection.SERVERBOUND),player);
        return player;
    }
    private void homePad(BlockPos pos) {
        for(int x=-2;x<=2;x++)for(int z=-2;z<=2;z++) {
            home.setBlockState(pos.add(x,-1,z),Blocks.STONE.getDefaultState(),2);
            home.setBlockToAir(pos.add(x,0,z));home.setBlockToAir(pos.add(x,1,z));
        }
        home.setBlockState(pos.east(),Content.PORTAL.getDefaultState(),2);
    }
    private void ready(EntityPlayerMP player) {
        player.getEntityData().removeTag("KitWorkshopPortalCooldown");
        // A connected client's movement packets clear this vanilla travel protection; the harness has no client.
        player.clearInvulnerableDimensionChange();
    }
    private void click(EntityPlayerMP player,WorldServer world,BlockPos portal) {
        Content.PORTAL.onBlockActivated(world,portal,Content.PORTAL.getDefaultState(),player,EnumHand.MAIN_HAND,EnumFacing.UP,.5F,.5F,.5F);
    }
    private void save(EntityPlayerMP player) {home.getSaveHandler().getPlayerNBTManager().writePlayerData(player);}
    public int live() {
        require(DimensionManager.isDimensionRegistered(TrialDimension.id) && realm.provider instanceof TrialWorldProvider
            && realm.provider.getDimensionType()==TrialDimension.type,"registration/provider");
        realm.getChunk(0,0);
        require(realm.getBlockState(new BlockPos(2,0,0)).getBlock()==Blocks.BEDROCK
            && realm.getBlockState(new BlockPos(2,63,0)).getBlock()==Blocks.GRASS && realm.isAirBlock(new BlockPos(2,64,0)),"flat terrain layers");
        require(realm.getBlockState(new BlockPos(0,64,0)).getBlock()==Content.PORTAL,"generated return portal");
        require(!realm.provider.canRespawnHere() && realm.provider.getRespawnDimension(player("RespawnPolicy",realm))==0,"Overworld death respawn policy");
        homePad(first);homePad(second);
        EntityPlayerMP one=player("DimensionOne",home),two=player("DimensionTwo",home);
        one.setPosition(first.getX()+.5,first.getY(),first.getZ()+.5);two.setPosition(second.getX()+.5,second.getY(),second.getZ()+.5);
        one.inventory.setInventorySlotContents(0,new ItemStack(Items.APPLE,7));one.getEntityData().setString("UnrelatedDimensionTrial","retained");
        one.openContainer=new net.minecraft.inventory.Container() {
            @Override public boolean canInteractWith(EntityPlayer player) {return true;}
        };
        require(!PortalTravel.travel(one) && one.dimension==0,"open menu travel allowed");one.openContainer=one.inventoryContainer;
        net.minecraft.entity.item.EntityBoat boat=new net.minecraft.entity.item.EntityBoat(home);one.startRiding(boat,true);
        require(!PortalTravel.travel(one) && one.dimension==0,"mounted travel allowed");one.dismountRidingEntity();
        one.setPosition(first.getX()+.5,first.getY(),first.getZ()+.5);
        click(one,home,first.east());
        require(one.dimension==TrialDimension.id && one.world==realm && PortalTravel.safe(realm,one.getPosition()),"entry transfer/landing");
        require(one.inventory.getStackInSlot(0).getCount()==7 && "retained".equals(one.getEntityData().getString("UnrelatedDimensionTrial")),"entry inventory/unrelated data");
        require(!PortalTravel.travel(one) && one.dimension==TrialDimension.id,"cooldown bypass");
        click(two,home,second.east());require(two.dimension==TrialDimension.id && two.world==realm,"second player entry");
        ready(one);click(one,realm,new BlockPos(0,64,0));
        require(one.dimension==0 && one.world==home && one.getPosition().distanceSq(first)<=4,"first return point: dimension="+one.dimension+", position="+one.getPosition());
        require(two.dimension==TrialDimension.id,"cross-player transfer leakage");
        ready(two);click(two,realm,new BlockPos(0,64,0));
        require(two.dimension==0 && two.getPosition().distanceSq(second)<=4,"second return point");
        // Do not overwrite a blocked origin with a new platform: a blocked search must cancel before moving/saving.
        BlockPos blocked=new BlockPos(200,200,200);
        for(int x=-8;x<=8;x++)for(int z=-8;z<=8;z++) {
            for(int y=195;y<=205;y++)realm.setBlockState(new BlockPos(200+x,y,200+z),Blocks.STONE.getDefaultState(),2);
            realm.setBlockState(new BlockPos(200+x,206,200+z),Blocks.LAVA.getDefaultState(),2);
        }
        require(PortalTravel.landing(realm,blocked)==null && realm.getBlockState(blocked).getBlock()==Blocks.STONE,"blocked landing/no edits");
        require(!PortalTravel.safe(realm,new BlockPos(0,64,0)),"portal occupancy accepted as air");
        ready(one);require(PortalTravel.travel(one) && one.dimension==TrialDimension.id,"repeat entry");
        // Block the preferred return cell; select a safe adjacent cell rather than suffocate or alter it.
        home.setBlockState(first,Blocks.STONE.getDefaultState(),2);ready(one);
        require(PortalTravel.travel(one) && one.dimension==0 && !one.getPosition().equals(first)
            && PortalTravel.safe(home,one.getPosition()) && home.getBlockState(first).getBlock()==Blocks.STONE,"blocked return fallback");
        home.setBlockToAir(first);one.setPosition(first.getX()+.5,first.getY(),first.getZ()+.5);
        ready(one);require(PortalTravel.travel(one),"save-stage first entry");ready(two);require(PortalTravel.travel(two),"save-stage second entry");
        realm.setBlockState(chest,Blocks.CHEST.getDefaultState(),2);
        ((TileEntityChest)realm.getTileEntity(chest)).setInventorySlotContents(0,new ItemStack(Items.APPLE,11));
        realm.setBlockState(chest.east(),Blocks.GOLD_BLOCK.getDefaultState(),2);
        save(one);save(two);
        require(one.dimension==TrialDimension.id && two.dimension==TrialDimension.id,"saved dimension state");
        System.out.println("DIMENSION_LIVE_PASS assertions="+assertions);return assertions;
    }
    public int restore() {
        realm.getChunk(chest);
        require(realm.getBlockState(chest.east()).getBlock()==Blocks.GOLD_BLOCK && realm.getTileEntity(chest) instanceof TileEntityChest,"realm block/chest disk state");
        require(((TileEntityChest)realm.getTileEntity(chest)).getStackInSlot(0).getCount()==11,"realm chest contents disk state");
        require(realm.getBlockState(new BlockPos(0,64,0)).getBlock()==Content.PORTAL,"return portal disk state");
        EntityPlayerMP one=player("DimensionOne",realm),two=player("DimensionTwo",realm);
        home.getSaveHandler().getPlayerNBTManager().readPlayerData(one);home.getSaveHandler().getPlayerNBTManager().readPlayerData(two);
        require(one.dimension==TrialDimension.id && two.dimension==TrialDimension.id,"player dimension disk state");
        require(one.inventory.getStackInSlot(0).getCount()==7 && "retained".equals(one.getEntityData().getString("UnrelatedDimensionTrial")),"inventory/unrelated disk state");
        ready(one);require(PortalTravel.travel(one) && one.dimension==0 && one.getPosition().distanceSq(first)<=4,"first persisted return point");
        ready(two);require(PortalTravel.travel(two) && two.dimension==0 && two.getPosition().distanceSq(second)<=4,"second persisted return point");
        System.out.println("DIMENSION_RESTART_PASS assertions="+assertions);return assertions;
    }
}