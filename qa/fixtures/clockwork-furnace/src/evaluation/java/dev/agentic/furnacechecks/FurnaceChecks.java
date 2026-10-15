package dev.agentic.furnacechecks;

import java.util.UUID;
import com.mojang.authlib.GameProfile;
import dev.agentic.clockworkbottle.FurnaceBoosts;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.EnumPacketDirection;
import net.minecraft.network.NetHandlerPlayServer;
import net.minecraft.network.NetworkManager;
import net.minecraft.tileentity.TileEntityFurnace;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.world.ChunkEvent;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLServerStartingEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.PlayerEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.common.registry.ForgeRegistries;

@Mod(modid="furnacechecks",name="Furnace Acceptance",version="1.0",dependencies="required-after:clockworkchecks")
public final class FurnaceChecks {
    private int assertions;
    private Item item;
    private WorldServer world;
    private FakePlayer livePlayer;
    private TileEntityFurnace liveBoosted,liveControl;
    private int liveTicks;
    private boolean live;
    private static final BlockPos LIVE_POS=new BlockPos(0,75,0), CONTROL_POS=new BlockPos(2,75,0);

    private void require(boolean condition,String message) {
        assertions++;
        if(!condition)throw new IllegalStateException("FURNACE_ASSERTIONS_FAIL: "+message);
    }
    private FakePlayer player(String name) {
        FakePlayer p=new FakePlayer(world,new GameProfile(UUID.nameUUIDFromBytes(name.getBytes(java.nio.charset.StandardCharsets.UTF_8)),name));
        p.connection=new NetHandlerPlayServer(world.getMinecraftServer(),new NetworkManager(EnumPacketDirection.SERVERBOUND),p);
        p.setHeldItem(EnumHand.MAIN_HAND,new ItemStack(item));
        return p;
    }
    private void charge(FakePlayer p,long ticks) {
        NBTTagCompound root=new NBTTagCompound(),state=new NBTTagCompound();state.setLong("ChargeTicks",ticks);
        root.setTag("ClockworkBottle",state);p.getHeldItemMainhand().setTagCompound(root);
    }
    private long charge(FakePlayer p) {return p.getHeldItemMainhand().getTagCompound().getCompoundTag("ClockworkBottle").getLong("ChargeTicks");}
    private TileEntityFurnace furnace(BlockPos pos) {
        world.setBlockState(pos,Blocks.AIR.getDefaultState());
        world.setBlockState(pos,Blocks.FURNACE.getDefaultState());
        TileEntityFurnace tile=(TileEntityFurnace)world.getTileEntity(pos);
        tile.setInventorySlotContents(0,new ItemStack(Blocks.IRON_ORE,64));
        tile.setInventorySlotContents(1,new ItemStack(Items.COAL,64));
        return tile;
    }
    private EnumActionResult use(FakePlayer p,BlockPos pos) {
        p.setPosition(pos.getX()+0.5,pos.getY()+0.5,pos.getZ()+2.5);p.setSneaking(true);
        return item.onItemUse(p,world,pos,EnumHand.MAIN_HAND,EnumFacing.UP,0.5F,0.5F,0.5F);
    }
    private int work(TileEntityFurnace furnace) {return furnace.getStackInSlot(2).getCount()*200+furnace.getField(2);}
    private void steps(int count) {
        for(int i=0;i<count;i++){world.updateEntities();FMLCommonHandler.instance().onPostWorldTick(world);}
    }
    private void staticChecks() {
        FurnaceBoosts.clear();BlockPos pos=new BlockPos(0,70,0),controlPos=new BlockPos(2,70,0);
        TileEntityFurnace boosted=furnace(pos),control=furnace(controlPos);FakePlayer p=player("FurnaceStatic");charge(p,100000);
        require(use(p,pos)==EnumActionResult.SUCCESS,"First boost rejected");
        require(charge(p)==99400,"First boost cost incorrect");
        require(FurnaceBoosts.multiplier(world,pos)==2 && FurnaceBoosts.remainingTicks(world,pos)==300,"First rate/duration incorrect");
        steps(20);require(work(boosted)==40 && work(control)==20,"2x furnace did not run twice normal work");
        require(FurnaceBoosts.remainingTicks(world,pos)==280,"Duration followed accelerated rather than game ticks");
        require(control.getField(0)-boosted.getField(0)==20,"Fuel was not accelerated with cooking");
        require(use(p,pos)==EnumActionResult.SUCCESS && charge(p)==98200,"4x cost incorrect");
        require(FurnaceBoosts.remainingTicks(world,pos)==300,"Second click did not refresh duration");
        int priorWork=work(boosted);steps(1);require(work(boosted)-priorWork==4,"4x work rate incorrect");
        FakePlayer other=player("SecondActivator");charge(other,100000);
        require(use(other,pos)==EnumActionResult.SUCCESS && charge(other)==97600,"Other player did not pay target's8x cost");
        priorWork=work(boosted);steps(1);require(work(boosted)-priorWork==8,"Repeated players stacked incorrect multiplier");
        charge(other,10);int beforeRemaining=FurnaceBoosts.remainingTicks(world,pos);NBTTagCompound before=other.getHeldItemMainhand().getTagCompound().copy();
        require(use(other,pos)==EnumActionResult.FAIL,"Insufficient boost succeeded");
        require(before.equals(other.getHeldItemMainhand().getTagCompound()),"Failed boost spent charge");
        require(FurnaceBoosts.multiplier(world,pos)==8 && FurnaceBoosts.remainingTicks(world,pos)==beforeRemaining,"Failed boost changed speed/timer");
        steps(beforeRemaining);require(FurnaceBoosts.multiplier(world,pos)==0,"Boost did not expire exactly");
        charge(p,100000);require(use(p,pos)==EnumActionResult.SUCCESS && charge(p)==99400,"Expiry did not reset to2x/30 seconds");
        MinecraftForge.EVENT_BUS.post(new PlayerEvent.PlayerLoggedOutEvent(p));require(FurnaceBoosts.multiplier(world,pos)==0,"Disconnect leaked boost");
        require(use(p,pos)==EnumActionResult.SUCCESS,"Lifecycle setup failed");
        MinecraftForge.EVENT_BUS.post(new ChunkEvent.Unload(world.getChunk(pos)));
        require(FurnaceBoosts.multiplier(world,pos)==0,"Chunk unload event leaked boost");
        require(use(p,pos)==EnumActionResult.SUCCESS,"Replacement setup failed");
        world.setBlockState(pos,Blocks.AIR.getDefaultState());furnace(pos);steps(1);
        require(FurnaceBoosts.multiplier(world,pos)==0,"Replacement furnace inherited boost");
        charge(p,100000);p.setSneaking(false);p.setPosition(0.5,70.5,2.5);
        require(item.onItemUse(p,world,pos,EnumHand.MAIN_HAND,EnumFacing.UP,0,0,0)==EnumActionResult.PASS && charge(p)==100000,"Normal block click intercepted");
        require(item.onItemRightClick(world,p,EnumHand.MAIN_HAND).getType()==EnumActionResult.SUCCESS && charge(p)==99700,"Normal Night Vision regressed");
        p.setSneaking(true);require(item.onItemRightClick(world,p,EnumHand.MAIN_HAND).getType()==EnumActionResult.PASS && charge(p)==99700,"Shift failure fell back to Night Vision");
        p.setPosition(1000,70,1000);
        require(item.onItemUse(p,world,pos,EnumHand.MAIN_HAND,EnumFacing.UP,0,0,0)==EnumActionResult.FAIL && charge(p)==99700,"Remote target accepted");
        BlockPos absent=new BlockPos(2000000,70,2000000);boolean loaded=world.isBlockLoaded(absent,false);
        require(!loaded && use(p,absent)==EnumActionResult.FAIL && !world.isBlockLoaded(absent,false),"Activation loaded an absent chunk");
        world.setBlockState(controlPos,Blocks.CHEST.getDefaultState());charge(p,100000);
        require(use(p,controlPos)==EnumActionResult.PASS && charge(p)==100000,"Unsupported chest spent charge");
        // Global reservation: highest individually affordable rate + one2x fill8192 extra ticks.
        FurnaceBoosts.clear();furnace(pos);furnace(controlPos);
        for(int rate=2;rate<=8192;rate*=2){charge(p,4320000);require(use(p,pos)==EnumActionResult.SUCCESS && FurnaceBoosts.multiplier(world,pos)==rate,"Affordable escalation rejected at"+rate);}
        charge(p,4320000);require(use(p,pos)==EnumActionResult.FAIL && charge(p)==4320000 && FurnaceBoosts.multiplier(world,pos)==8192,"Unaffordable escalation wrapped/spent");
        require(use(p,controlPos)==EnumActionResult.SUCCESS,"Remaining global budget rejected2x");
        long balance=charge(p);require(use(p,controlPos)==EnumActionResult.FAIL && charge(p)==balance && FurnaceBoosts.multiplier(world,controlPos)==2,"Global limit changed charge/rate");
        FurnaceBoosts.clear();
        // Empty fuel/input and blocked output must retain vanilla failure behavior.
        TileEntityFurnace blocked=furnace(pos);blocked.setInventorySlotContents(2,new ItemStack(Items.IRON_INGOT,64));charge(p,100000);use(p,pos);steps(5);
        require(blocked.getStackInSlot(0).getCount()==64 && blocked.getStackInSlot(1).getCount()==64 && blocked.getField(2)==0,"Full output duplicated/consumed ingredients");
        FurnaceBoosts.clear();TileEntityFurnace noFuel=furnace(pos);noFuel.setInventorySlotContents(1,ItemStack.EMPTY);charge(p,100000);use(p,pos);steps(5);
        require(work(noFuel)==0 && noFuel.getStackInSlot(0).getCount()==64,"Acceleration invented fuel");
        FurnaceBoosts.clear();TileEntityFurnace noInput=furnace(pos);noInput.setInventorySlotContents(0,ItemStack.EMPTY);charge(p,100000);use(p,pos);steps(5);
        require(work(noInput)==0 && noInput.getStackInSlot(1).getCount()==64,"Empty input produced output/burned new fuel");
        FurnaceBoosts.clear();
        System.out.println("FURNACE_STATIC_PASS count="+assertions);
    }

    @Mod.EventHandler public void start(FMLServerStartingEvent event) {
        world=event.getServer().getWorld(0);
        item=ForgeRegistries.ITEMS.getValue(new ResourceLocation("clockworkbottle","clockwork_bottle"));
        require(item!=null,"Bottle missing");
        // WorldServer culls entity/tile ticking after300 empty-world updates.
        // Keep real vanilla ticks active without claiming a networked login.
        FakePlayer observer=player("FurnaceObserver");observer.setPosition(4.5,75,4.5);world.playerEntities.add(observer);
        try(java.io.InputStream input=new java.io.FileInputStream(System.getProperty("furnace.baselinePlayer"))) {
            NBTTagCompound old=net.minecraft.nbt.CompressedStreamTools.readCompressed(input);
            FakePlayer upgraded=player("UpgradeBottle");upgraded.readFromNBT(old);
            require(upgraded.getHeldItemMainhand().getItem()==item && charge(upgraded)==19,"1.0.1 saved bottle did not retain identity/charge");
            require(upgraded.getHeldItemMainhand().getTagCompound().getCompoundTag("ClockworkBottle").getInteger("NextSeconds")==30,"1.0.1 Night Vision sequence lost");
        }catch(java.io.IOException error){throw new IllegalStateException("FURNACE_ASSERTIONS_FAIL: Baseline1.0.1 fixture unreadable",error);}
        if("restore".equals(System.getProperty("furnace.phase"))) {
            FakePlayer restored=player("FurnacePersist");
            require(event.getServer().getPlayerList().readPlayerDataFromFile(restored)!=null,"Furnace player's save missing");
            require(charge(restored)==97600,"Furnace spending changed across server restart");
            require(FurnaceBoosts.multiplier(world,LIVE_POS)==0,"Active boost survived server restart");
            System.out.println("FURNACE_RESTART_PASS count="+assertions);return;
        }
        staticChecks();
        liveBoosted=furnace(LIVE_POS);liveControl=furnace(CONTROL_POS);livePlayer=player("FurnacePersist");charge(livePlayer,100000);
        require(use(livePlayer,LIVE_POS)==EnumActionResult.SUCCESS,"Live2x activation failed");
        MinecraftForge.EVENT_BUS.register(this);live=true;
    }

    @SubscribeEvent(priority=EventPriority.LOWEST) public void realTick(TickEvent.WorldTickEvent event) {
        if(!live || event.world!=world || event.phase!=TickEvent.Phase.END)return;
        try {
            ++liveTicks;
            int expected=liveTicks<=20?liveTicks*2:40+(Math.min(liveTicks,320)-20)*4+Math.max(0,liveTicks-320);
            require(work(liveBoosted)==expected && work(liveControl)==liveTicks,"Actual server-tick work mismatch at"+liveTicks+": boosted="+work(liveBoosted)+", control="+work(liveControl));
            if(liveTicks==20) {
                require(use(livePlayer,LIVE_POS)==EnumActionResult.SUCCESS && charge(livePlayer)==98200,"Live refresh/cost incorrect");
                require(FurnaceBoosts.remainingTicks(world,LIVE_POS)==300,"Live refresh duration incorrect");
            }
            if(liveTicks==319)require(FurnaceBoosts.multiplier(world,LIVE_POS)==4 && FurnaceBoosts.remainingTicks(world,LIVE_POS)==1,"Early live expiry");
            if(liveTicks==320)require(FurnaceBoosts.multiplier(world,LIVE_POS)==0,"Late live expiry");
            if(liveTicks==340) {
                require(liveControl.getField(0)-liveBoosted.getField(0)==920,"Live fuel consumption mismatch");
                require(use(livePlayer,LIVE_POS)==EnumActionResult.SUCCESS && charge(livePlayer)==97600,"Live reset/cost incorrect");
                world.getSaveHandler().getPlayerNBTManager().writePlayerData(livePlayer);
                live=false;System.out.println("FURNACE_LIVE_PASS ticks="+liveTicks+" assertions="+assertions);
            }
        }catch(RuntimeException error){live=false;System.out.println(error.getMessage());throw error;}
    }
}
