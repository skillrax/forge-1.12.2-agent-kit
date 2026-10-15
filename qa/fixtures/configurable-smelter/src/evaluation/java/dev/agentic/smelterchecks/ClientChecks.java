package dev.agentic.smelterchecks;
import dev.agentic.smelter.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiMainMenu;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ScreenShotHelper;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.GameType;
import net.minecraft.world.WorldSettings;
import net.minecraft.world.WorldType;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.Side;
import org.lwjgl.opengl.Display;
import org.lwjgl.opengl.DisplayMode;

@Mod.EventBusSubscriber(modid="smelterchecks",value=Side.CLIENT)
public final class ClientChecks {
    private static boolean launched,opened,guiRequested,tooltipCaptured;
    private static int ticks;
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event) throws Exception {
        if(event.phase!=TickEvent.Phase.END)return;
        Minecraft mc=Minecraft.getMinecraft();
        if(!launched && mc.currentScreen instanceof GuiMainMenu){
            launched=true;Display.setDisplayMode(new DisplayMode(1280,900));mc.resize(1280,900);
            mc.gameSettings.pauseOnLostFocus=false;mc.gameSettings.guiScale=3;mc.gameSettings.renderDistanceChunks=4;
            mc.launchIntegratedServer("smelter-preview-"+System.currentTimeMillis(),"Smelter UI Preview",new WorldSettings(12,GameType.CREATIVE,false,false,WorldType.FLAT));return;
        }
        if(mc.world==null || mc.player==null || mc.getIntegratedServer()==null)return;
        if(!opened){
            opened=true;
            mc.getIntegratedServer().addScheduledTask(()->{
                EntityPlayerMP player=mc.getIntegratedServer().getPlayerList().getPlayerByUUID(mc.player.getUniqueID());
                BlockPos pos=new BlockPos(0,6,0);player.getServerWorld().setBlockState(pos,Content.SMELTER.getDefaultState(),3);
                player.getServerWorld().setBlockState(pos.south(),Blocks.REDSTONE_BLOCK.getDefaultState(),3);
                SmelterTile tile=(SmelterTile)player.getServerWorld().getTileEntity(pos);
                tile.energy.restore(12000);tile.setInventorySlotContents(0,new ItemStack(Blocks.IRON_ORE,2));tile.setInventorySlotContents(1,new ItemStack(Blocks.SAND,2));tile.setInventorySlotContents(4,new ItemStack(Content.UPGRADE));
                player.connection.setPlayerLocation(.5,6,3.5,180,0);
            });return;
        }
        if(!guiRequested){
            BlockPos pos=new BlockPos(0,6,0);
            // The client must receive the teleported chunk/tile before the open-window packet.
            if(mc.currentScreen!=null || !(mc.world.getTileEntity(pos) instanceof SmelterTile))return;
            guiRequested=true;
            mc.getIntegratedServer().addScheduledTask(()->{
                EntityPlayerMP player=mc.getIntegratedServer().getPlayerList().getPlayerByUUID(mc.player.getUniqueID());
                player.openGui(SmelterProof.instance,0,player.world,pos.getX(),pos.getY(),pos.getZ());
            });return;
        }
        ticks++;
        if(ticks==70){
            if(!(mc.currentScreen instanceof SmelterScreen))throw new IllegalStateException("SMELTER_ASSERTIONS_FAIL: GUI did not open");
            System.out.println("SMELTER_CLIENT_SCREEN "+ScreenShotHelper.saveScreenshot(mc.gameDir,"smelter-ui-default.png",mc.displayWidth,mc.displayHeight,mc.getFramebuffer()).getUnformattedText());
            int window=mc.player.openContainer.windowId;
            for(int action:new int[]{6,7,8,2})mc.playerController.sendEnchantPacket(window,action);
        }
        if(ticks==100){
            SmelterTile tile=((SmelterContainer)mc.player.openContainer).tile;
            if(tile.getField(3)!=1 || tile.getField(4)!=1 || tile.getField(5)!=1 || tile.getField(8)!=1 || tile.getField(19)!=5)throw new IllegalStateException("SMELTER_ASSERTIONS_FAIL: GUI controls/transfer status not synchronized");
            System.out.println("SMELTER_CLIENT_SCREEN "+ScreenShotHelper.saveScreenshot(mc.gameDir,"smelter-ui-controls.png",mc.displayWidth,mc.displayHeight,mc.getFramebuffer()).getUnformattedText());
        }
        if(ticks==120){if(!tooltipCaptured)throw new IllegalStateException("SMELTER_ASSERTIONS_FAIL: no diagnostic tooltip capture");System.out.println("SMELTER_CLIENT_PASS screenshots=3 controls=server-synchronized transferStatus=true");mc.shutdown();}
    }
    @SubscribeEvent public static void render(TickEvent.RenderTickEvent event){
        if(event.phase!=TickEvent.Phase.END || ticks<100 || tooltipCaptured)return;
        Minecraft mc=Minecraft.getMinecraft();if(!(mc.currentScreen instanceof SmelterScreen))return;
        net.minecraft.client.gui.ScaledResolution size=new net.minecraft.client.gui.ScaledResolution(mc);
        mc.currentScreen.drawScreen((size.getScaledWidth()-344)/2+306,(size.getScaledHeight()-238)/2+64,0);
        System.out.println("SMELTER_CLIENT_SCREEN "+ScreenShotHelper.saveScreenshot(mc.gameDir,"smelter-ui-transfer-status.png",mc.displayWidth,mc.displayHeight,mc.getFramebuffer()).getUnformattedText());tooltipCaptured=true;
    }
}
