package dev.agentic.menuchecks;

import dev.agentic.playermenu.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.*;
import net.minecraft.util.ScreenShotHelper;
import net.minecraft.world.*;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.Side;
import org.lwjgl.opengl.*;
import org.lwjgl.input.Mouse;
import java.awt.Robot;
import java.awt.event.KeyEvent;
import java.io.File;

@Mod.EventBusSubscriber(modid="menuchecks",value=Side.CLIENT)
public final class ClientChecks {
    private static boolean launched,restart,rejoining;private static int stage,ticks;private static Robot robot;private static long worldTime;
    private static void ok(boolean b,String s){if(!b)throw new IllegalStateException("MENU_ASSERTIONS_FAIL: client "+s);}
    private static ClientProxy.Screen screen(){ok(Minecraft.getMinecraft().currentScreen instanceof ClientProxy.Screen,"N opened screen");return (ClientProxy.Screen)Minecraft.getMinecraft().currentScreen;}
    private static void pressN()throws Exception {
        // Real OS N press traverses LWJGL/Minecraft input and registered keybinding.
        robot.keyPress(KeyEvent.VK_N);robot.keyRelease(KeyEvent.VK_N);
    }
    private static void click(int id)throws Exception {
        Minecraft mc=Minecraft.getMinecraft();ScaledResolution sr=new ScaledResolution(mc);int l=(sr.getScaledWidth()-300)/2,t=(sr.getScaledHeight()-200)/2;
        int x=l+(id==2?180:40),y=t+(id==3?133:106);
        java.lang.reflect.Method m=GuiScreen.class.getDeclaredMethod("mouseClicked",int.class,int.class,int.class);m.setAccessible(true);m.invoke(screen(),x,y,0);
        java.lang.reflect.Method r=GuiScreen.class.getDeclaredMethod("mouseReleased",int.class,int.class,int.class);r.setAccessible(true);r.invoke(screen(),x,y,0);
    }
    private static void shot(String name){Minecraft mc=Minecraft.getMinecraft();System.out.println("MENU_SCREEN "+ScreenShotHelper.saveScreenshot(mc.gameDir,name,mc.displayWidth,mc.displayHeight,mc.getFramebuffer()).getUnformattedText());}
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event)throws Exception {
        if(event.phase!=TickEvent.Phase.END)return;Minecraft mc=Minecraft.getMinecraft();
        if(!launched && mc.currentScreen instanceof GuiMainMenu){
            launched=true;robot=new Robot();Display.setDisplayMode(new DisplayMode(1280,900));mc.resize(1280,900);mc.gameSettings.pauseOnLostFocus=false;mc.gameSettings.guiScale=3;mc.gameSettings.renderDistanceChunks=4;
            restart=new File(mc.gameDir,"menu-client-render.marker").exists();
            mc.launchIntegratedServer("personal-menu-preview-render","Personal Menu QA",new WorldSettings(12,GameType.CREATIVE,false,false,WorldType.FLAT));return;
        }
        if(rejoining && mc.world==null && mc.getIntegratedServer()==null){mc.launchIntegratedServer("personal-menu-preview-render","Personal Menu QA",null);rejoining=false;ticks=0;stage=10;return;}
        if(mc.world==null || mc.player==null)return;
        ticks++;
        if(stage==0 && ticks>50){
            mc.displayGuiScreen(null);
            // Focus the actual rendered game window before sending keyboard input.
            robot.mouseMove(Display.getX()+640,Display.getY()+450);robot.mousePress(java.awt.event.InputEvent.BUTTON1_DOWN_MASK);robot.mouseRelease(java.awt.event.InputEvent.BUTTON1_DOWN_MASK);
            pressN();stage=1;ticks=0;return;
        }
        if(stage==1 && ticks>30){
            ok(screen().ready && !screen().pending,"initial server reply");ok(!screen().doesGuiPauseGame(),"menu never pauses world");
            if(restart){String saved=new String(java.nio.file.Files.readAllBytes(new File(mc.gameDir,"menu-client-render.marker").toPath()),java.nio.charset.StandardCharsets.UTF_8);ok(saved.equals(mc.player.getUniqueID().toString()),"same UUID after separate JVM");ok(screen().counter==2 && screen().enabled,"separate JVM actual integrated save restart");shot("menu-restarted.png");System.out.println("MENU_CLIENT_RESTART_PASS actualNetworkSession=true sameUUID="+saved);System.out.println("MENU_CLIENT_PASS separateJVM=true");mc.shutdown();return;}
            ok(screen().counter==0 && !screen().enabled,"fresh values");worldTime=mc.world.getTotalWorldTime();shot("menu-default.png");click(1);stage=2;ticks=0;
        }
        if(stage==2 && ticks>20){ok(screen().counter==1,"button increment server reply");click(2);stage=3;ticks=0;}
        if(stage==3 && ticks>20){ok(screen().enabled,"toggle reply");click(3);stage=4;ticks=0;}
        if(stage==4 && ticks>20){ok(screen().counter==0 && !screen().enabled,"reset reply");click(1);stage=5;ticks=0;}
        if(stage==5 && ticks>20){ok(screen().counter==1,"increment after reset");click(1);stage=6;ticks=0;}
        if(stage==6 && ticks>20){ok(screen().counter==2,"exact two increments");click(2);stage=7;ticks=0;}
        if(stage==7 && ticks>20){
            ok(screen().enabled,"saved final toggle");ok(mc.world.getTotalWorldTime()>worldTime,"actual world ticks while menu open");ScaledResolution sr=new ScaledResolution(mc);int x=(sr.getScaledWidth()-300)/2+70,y=(sr.getScaledHeight()-200)/2+181;
            Mouse.setCursorPosition(x*mc.displayWidth/sr.getScaledWidth(),mc.displayHeight-y*mc.displayHeight/sr.getScaledHeight());stage=8;ticks=0;
        }
        if(stage==8 && ticks>15){shot("menu-tooltip.png");mc.displayGuiScreen(new GuiChat());pressN();stage=9;ticks=0;}
        if(stage==9 && ticks>20){
            ok(mc.currentScreen instanceof GuiChat,"N while typing does not open menu");mc.displayGuiScreen(null);
            mc.world.sendQuittingDisconnectingPacket();mc.loadWorld(null);mc.displayGuiScreen(new GuiMainMenu());rejoining=true;ticks=0;
        }
        if(stage==10 && ticks>50){mc.displayGuiScreen(null);pressN();stage=11;ticks=0;}
        if(stage==11 && ticks>30){
            ok(screen().ready && screen().counter==2 && screen().enabled,"actual disconnect and relogin retained values");shot("menu-relogin.png");
            java.nio.file.Files.write(new File(mc.gameDir,"menu-client-render.marker").toPath(),mc.player.getUniqueID().toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));System.out.println("MENU_CLIENT_PASS actualOSKey=N mouseCallbacks=6 packetReplies=true worldTicks=true typingGuard=true logoutRelogin=true UUID="+mc.player.getUniqueID()+" next=separateJVM");mc.shutdown();
        }
        if(ticks>600)throw new IllegalStateException("MENU_ASSERTIONS_FAIL: client stage timeout "+stage);
    }
}
