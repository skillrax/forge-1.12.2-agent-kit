package dev.agentic.animationchecks;

import dev.agentic.animation.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiMainMenu;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.*;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.*;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.Side;
import org.lwjgl.opengl.*;

@Mod.EventBusSubscriber(modid="animationchecks",value=Side.CLIENT)
public final class ClientChecks {
    private static boolean launched,positioned;private static int ticks;private static double frozen;
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent e) throws Exception {
        if(e.phase!=TickEvent.Phase.END)return;Minecraft mc=Minecraft.getMinecraft();
        if(!launched && mc.currentScreen instanceof GuiMainMenu){
            launched=true;Display.setDisplayMode(new DisplayMode(1000,760));mc.resize(1000,760);
            mc.gameSettings.pauseOnLostFocus=false;mc.gameSettings.renderDistanceChunks=4;mc.gameSettings.fovSetting=55;mc.gameSettings.particleSetting=0;
            mc.launchIntegratedServer("animation-observation-"+System.currentTimeMillis(),"Animation Observation",new WorldSettings(12,GameType.CREATIVE,false,false,WorldType.FLAT));return;
        }
        if(mc.world==null || mc.player==null || mc.getIntegratedServer()==null)return;
        if(!positioned){positioned=true;mc.getIntegratedServer().addScheduledTask(()->{
            EntityPlayerMP p=mc.getIntegratedServer().getPlayerList().getPlayerByUUID(mc.player.getUniqueID());
            if(p!=null)p.connection.setPlayerLocation(.5,4,3.5,180,12);
        });return;}
        mc.player.setPosition(.5,4,3.5);mc.player.rotationYaw=mc.player.prevRotationYaw=180;mc.player.rotationPitch=mc.player.prevRotationPitch=12;
        AnimationTile tile=(AnimationTile)mc.world.getTileEntity(ProofChecks.POS);if(tile==null)return;
        ticks++;
        if(ticks==80 || ticks==87 || ticks==150 || ticks==180 || ticks==220)screen(mc,"animation-"+ticks+".png");
        if(ticks==100 || ticks==190){
            mc.playerController.processRightClickBlock(mc.player,mc.world,ProofChecks.POS,EnumFacing.SOUTH,new Vec3d(.5,4.2,1),EnumHand.MAIN_HAND);
            System.out.println("ANIMATION_CLIENT_RIGHT_CLICK tick="+ticks);
        }
        if(ticks==130){ProofChecks.require(!tile.active,"server toggle synced OFF");frozen=tile.motionTime(0);}
        if(ticks==180)ProofChecks.require(!tile.active && tile.motionTime(.9F)==frozen,"client frozen after residual sparkles expire");
        if(ticks==220)ProofChecks.require(tile.active && tile.motionTime(0)>frozen,"server toggle synced ON + resume");
        if(ticks==240){System.out.println("ANIMATION_CLIENT_PASS actualControllerClicks=2 syncedOffOn=true frozen=true screenshots=5");mc.shutdown();}
    }
    private static void screen(Minecraft mc,String file){System.out.println("ANIMATION_SCREEN "+ScreenShotHelper.saveScreenshot(mc.gameDir,file,mc.displayWidth,mc.displayHeight,mc.getFramebuffer()).getUnformattedText());}
}
