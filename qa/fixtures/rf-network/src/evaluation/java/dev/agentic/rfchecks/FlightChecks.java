package dev.agentic.rfchecks;

import dev.agentic.rfnetwork.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiMainMenu;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.*;
import net.minecraft.world.chunk.Chunk;
import net.minecraftforge.fluids.*;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.Side;

/** Two short normal-player-tracking scenarios; all diagnostics are side-effect-free. */
@Mod.EventBusSubscriber(modid="rfchecks",value=Side.CLIENT)
public final class FlightChecks {
    private static boolean launched,scheduled,setup,done;
    private static volatile boolean failed;
    private static int scenario,phase,age;
    private static BlockPos gp,sp;
    private static GeneratorTile generator;
    private static StorageTile storage;
    private static long snapshot,snapshotTime;
    private static int offset;
    private static double movingX;
    private static void require(boolean ok,String why){if(!ok){failed=true;throw new IllegalStateException("RF_ASSERTIONS_FAIL: flight: "+why);}}
    private static Chunk peek(WorldServer w,int x,int z){for(Chunk c:w.getChunkProvider().getLoadedChunks())if(c.x==x && c.z==z)return c;return null;}
    private static int[] residency(WorldServer w){
        int present=0,queued=0,spawn=0;
        for(int x=gp.getX()>>4;x<=sp.getX()>>4;x++){
            Chunk c=peek(w,x,gp.getZ()>>4);if(c!=null){present++;if(c.unloadQueued)queued++;}if(w.isSpawnChunk(x,gp.getZ()>>4))spawn++;
        }return new int[]{present,queued,spawn};
    }
    private static long generated(WorldServer w){Chunk c=peek(w,gp.getX()>>4,gp.getZ()>>4);if(c!=null && c.getTileEntityMap().get(gp) instanceof GeneratorTile)generator=(GeneratorTile)c.getTileEntityMap().get(gp);return generator.generated;}
    private static int stored(WorldServer w){Chunk c=peek(w,sp.getX()>>4,sp.getZ()>>4);if(c!=null && c.getTileEntityMap().get(sp) instanceof StorageTile)storage=(StorageTile)c.getTileEntityMap().get(sp);return storage.stored();}
    private static void move(EntityPlayerMP p,double x){p.connection.setPlayerLocation(x,80,gp.getZ()+4.5,180,0);p.getServerWorld().getPlayerChunkMap().updateMovingPlayer(p);}
    private static void setup(EntityPlayerMP p){
        WorldServer w=p.getServerWorld();offset=scenario==0?4096:0;gp=new BlockPos(offset+1,80,offset+8);sp=gp.east(192);
        w.setSpawnPoint(new BlockPos(8,4,8));w.getGameRules().setOrCreateGameRule("doMobSpawning","false");
        for(int x=gp.getX();x<=sp.getX();x++){BlockPos pos=new BlockPos(x,80,gp.getZ());w.setBlockState(pos,(x==gp.getX()?Content.GENERATOR:x==sp.getX()?Content.STORAGE:Content.CABLE).getDefaultState(),3);}
        generator=(GeneratorTile)w.getTileEntity(gp);storage=(StorageTile)w.getTileEntity(sp);
        generator.setInventorySlotContents(1,new ItemStack(Content.UPGRADE));generator.fluids().fill(new FluidStack(FluidRegistry.LAVA,8000),true);
        Minecraft.getMinecraft().gameSettings.renderDistanceChunks=6;
        move(p,gp.getX()+96.5);phase=0;age=0;setup=true;
    }
    private static void serverStep(EntityPlayerMP p){
        WorldServer w=p.getServerWorld();if(!setup){setup(p);return;}age++;
        if(phase==0 && age>=60){
            require(generated(w)>0,"Fully visible twelve-chunk line transfers");snapshot=generated(w);Minecraft.getMinecraft().gameSettings.renderDistanceChunks=2;phase=1;age=0;
        }else if(phase==1){
            if(age==40)snapshot=generated(w);
            if(age==80){int[] r=residency(w);require(generated(w)==snapshot && r[0]<13,"Stationary render reduction breaks route");System.out.println("RF_FLIGHT stationary case="+scenario+" resident="+r[0]+" queued="+r[1]+" spawn="+r[2]+" generated="+snapshot);movingX=gp.getX()+96.5;phase=2;age=0;}
        }else if(phase==2){
            movingX=Math.max(gp.getX()+.5,movingX-2);move(p,movingX);if(movingX<=gp.getX()+.5){phase=3;age=0;}
        }else if(phase==3){
            movingX=Math.min(sp.getX()+.5,movingX+2);move(p,movingX);if(movingX>=sp.getX()+.5){phase=4;age=0;}
        }else if(phase==4){
            if(age==60){snapshot=generated(w);snapshotTime=w.getTotalWorldTime();int[] r=residency(w);System.out.println("RF_FLIGHT stopped case="+scenario+" resident="+r[0]+" queued="+r[1]+" spawn="+r[2]+" generated="+snapshot);}
            if(age==120){
                long now=generated(w);int[] r=residency(w);
                if(scenario==0)require(now==snapshot && r[0]<13,"Far-from-spawn flight stops after player settles");
                else require(now>snapshot && now-snapshot==(w.getTotalWorldTime()-snapshotTime)*2000 && r[0]==13 && stored(w)==now,"Spawn residency plus player radius sustains real conserved transfer");
                System.out.println("RF_FLIGHT result case="+scenario+" resident="+r[0]+" queued="+r[1]+" spawn="+r[2]+" generatedDelta="+(now-snapshot)+" stored="+stored(w));
                if(scenario==0){scenario=1;setup=false;}else{done=true;System.out.println("RF_CLIENT_PASS flight=true scenarios=2 forceTickets=0 explicitUnloads=0");}
            }
        }
    }
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent e){
        if(!Boolean.getBoolean("rfnetwork.flight") || e.phase!=TickEvent.Phase.END)return;Minecraft mc=Minecraft.getMinecraft();
        if(failed){mc.shutdown();return;}
        if(!launched && mc.currentScreen instanceof GuiMainMenu){launched=true;mc.gameSettings.pauseOnLostFocus=false;mc.gameSettings.renderDistanceChunks=6;mc.launchIntegratedServer("rf-flight-"+System.currentTimeMillis(),"RF Residency Replay",new WorldSettings(12,GameType.CREATIVE,false,false,WorldType.FLAT));return;}
        if(done){mc.shutdown();return;}
        if(mc.world==null || mc.player==null || mc.getIntegratedServer()==null || scheduled)return;
        scheduled=true;mc.getIntegratedServer().addScheduledTask(()->{try{EntityPlayerMP p=mc.getIntegratedServer().getPlayerList().getPlayerByUUID(mc.player.getUniqueID());if(p!=null)serverStep(p);}finally{scheduled=false;}});
    }
}
