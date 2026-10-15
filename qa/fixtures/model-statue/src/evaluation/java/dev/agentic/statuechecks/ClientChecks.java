package dev.agentic.statuechecks;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiMainMenu;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.ScreenShotHelper;
import net.minecraft.world.GameType;
import net.minecraft.world.WorldSettings;
import net.minecraft.world.WorldType;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.Side;
import org.lwjgl.opengl.Display;
import org.lwjgl.opengl.DisplayMode;

// Runs a real graphical client, screenshots actual framebuffers, then saves/exits.
@Mod.EventBusSubscriber(modid="statuechecks",value=Side.CLIENT)
public final class ClientChecks {
    private static boolean launched,positioned;
    private static int ticks;
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event) throws Exception {
        if(event.phase!=TickEvent.Phase.END)return;
        Minecraft mc=Minecraft.getMinecraft();
        if(!launched && mc.currentScreen instanceof GuiMainMenu){
            launched=true;Display.setDisplayMode(new DisplayMode(1280,900));mc.resize(1280,900);
            mc.gameSettings.pauseOnLostFocus=false;mc.gameSettings.hideGUI=true;mc.gameSettings.renderDistanceChunks=6;mc.gameSettings.fovSetting=60;
            mc.launchIntegratedServer("statue-proof-"+System.currentTimeMillis(),"Model Statue Preview",new WorldSettings(12,GameType.CREATIVE,false,false,WorldType.FLAT));
            return;
        }
        if(mc.world==null || mc.player==null || mc.getIntegratedServer()==null)return;
        if(!positioned){
            positioned=true;
            mc.getIntegratedServer().addScheduledTask(()->{
                EntityPlayerMP player=mc.getIntegratedServer().getPlayerList().getPlayerByUUID(mc.player.getUniqueID());
                if(player!=null)player.connection.setPlayerLocation(.5,4,5.5,180,0);
            });
            return;
        }
        ticks++;
        // Keep captures reproducible even when the window receives mouse movement.
        mc.player.setPosition(.5,4,5.5);
        mc.player.rotationYaw=mc.player.prevRotationYaw=180;
        mc.player.rotationPitch=mc.player.prevRotationPitch=0;
        if(ticks==80){System.out.println("STATUE_CLIENT_SCREEN "+ScreenShotHelper.saveScreenshot(mc.gameDir,"statue-front.png",mc.displayWidth,mc.displayHeight,mc.getFramebuffer()).getUnformattedText());}
        if(ticks==100){mc.refreshResources();System.out.println("STATUE_CLIENT_RELOAD_REQUESTED");}
        if(ticks==160){System.out.println("STATUE_CLIENT_SCREEN "+ScreenShotHelper.saveScreenshot(mc.gameDir,"statue-after-reload.png",mc.displayWidth,mc.displayHeight,mc.getFramebuffer()).getUnformattedText());}
        if(ticks==180){System.out.println("STATUE_CLIENT_PASS ticks="+ticks+" screenshots=2 resourceReload=true");mc.shutdown();}
    }
}
