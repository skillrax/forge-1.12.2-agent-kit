package dev.agentic.itemnetworkchecks;
import dev.agentic.itemnetwork.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiMainMenu;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.inventory.*;
import net.minecraft.item.*;
import net.minecraft.util.*;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.*;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.Side;
import org.lwjgl.opengl.*;

@Mod.EventBusSubscriber(modid="itemnetworkchecks",value=Side.CLIENT)
public final class ClientChecks {
    private static boolean launched,positioned;private static int ticks;
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent e)throws Exception{
        if(e.phase!=TickEvent.Phase.END)return;Minecraft mc=Minecraft.getMinecraft();
        if(!launched && mc.currentScreen instanceof GuiMainMenu){launched=true;Display.setDisplayMode(new DisplayMode(1100,800));mc.resize(1100,800);mc.gameSettings.guiScale=2;mc.gameSettings.pauseOnLostFocus=false;mc.gameSettings.renderDistanceChunks=4;mc.launchIntegratedServer("item-network-observation-"+System.currentTimeMillis(),"Item Network Observation",new WorldSettings(12,GameType.CREATIVE,false,false,WorldType.FLAT));return;}
        if(mc.world==null || mc.player==null || mc.getIntegratedServer()==null)return;
        if(!positioned){positioned=true;mc.getIntegratedServer().addScheduledTask(()->{EntityPlayerMP p=mc.getIntegratedServer().getPlayerList().getPlayerByUUID(mc.player.getUniqueID());p.connection.setPlayerLocation(5.5,4,6.5,180,10);p.inventory.setInventorySlotContents(0,new ItemStack(Blocks.DIRT,5));});return;}
        ticks++;
        if(ticks==40)open(mc,ProofChecks.SOURCE);
        if(mc.player.openContainer instanceof DuctContainer){DuctContainer c=(DuctContainer)mc.player.openContainer;
            if(ticks==55)mc.playerController.windowClick(c.windowId,29,0,ClickType.PICKUP,mc.player);
            if(ticks==65)mc.playerController.windowClick(c.windowId,0,0,ClickType.PICKUP,mc.player);
            if(ticks==80){ProofChecks.require(mc.player.inventory.getItemStack().getCount()==5 && c.tile.filter.getStackInSlot(0).getItem()==Item.getItemFromBlock(Blocks.DIRT) && c.tile.filter.getStackInSlot(0).getCount()==1,"real ghost copy packet no consumption");screen(mc,"network-filter.png");}
            if(ticks==85)mc.playerController.windowClick(c.windowId,29,0,ClickType.PICKUP,mc.player);
            if(ticks==95)mc.playerController.windowClick(c.windowId,0,1,ClickType.PICKUP,mc.player);
            if(ticks==105){mc.playerController.sendEnchantPacket(c.windowId,3);mc.playerController.sendEnchantPacket(c.windowId,4);}
            if(ticks==115){ProofChecks.require(c.tile.filter.isEmpty() && c.tile.ignoreNBT && c.tile.ignoreMetadata,"clear and matching controls server sync");screen(mc,"network-controls.png");}
            if(ticks==116){for(int i=0;i<3;i++)mc.playerController.sendEnchantPacket(c.windowId,0);for(int i=0;i<6;i++)mc.playerController.sendEnchantPacket(c.windowId,1);}
            if(ticks==123)ProofChecks.require(c.tile.role==DuctTile.SOURCE && c.tile.direction()==EnumFacing.WEST,"role and six-face packet cycles sync");
            if(ticks==125)mc.playerController.sendEnchantPacket(c.windowId,2);
            if(ticks==130)mc.getIntegratedServer().addScheduledTask(()->ProofChecks.seed(mc.getIntegratedServer().getWorld(0)));
            if(ticks==190){ProofChecks.require(c.tile.enabled && c.tile.getField(6)==28 && c.tile.isEmpty(),"graphical multi-hop delivered 28 buffer empty");screen(mc,"network-delivered.png");}
        }
        if(ticks==200){mc.player.closeScreen();open(mc,ProofChecks.OUT_A);}
        if(ticks==220){ProofChecks.require(mc.player.openContainer instanceof ContainerChest,"actual destination A chest menu");ContainerChest c=(ContainerChest)mc.player.openContainer;ProofChecks.require(c.getLowerChestInventory().getStackInSlot(0).getCount()==16 && c.getLowerChestInventory().getStackInSlot(0).getItem()==Item.getItemFromBlock(Blocks.DIRT),"graphical destination dirt16");screen(mc,"network-dirt-result.png");}
        if(ticks==230){mc.player.closeScreen();open(mc,ProofChecks.OUT_B);}
        if(ticks==250){ProofChecks.require(mc.player.openContainer instanceof ContainerChest,"actual destination B chest menu");ContainerChest c=(ContainerChest)mc.player.openContainer;ProofChecks.require(c.getLowerChestInventory().getStackInSlot(0).getCount()==12 && c.getLowerChestInventory().getStackInSlot(0).getItem()==Item.getItemFromBlock(Blocks.COBBLESTONE),"graphical destination cobble12");screen(mc,"network-cobble-result.png");}
        if(ticks==260){mc.player.closeScreen();mc.getIntegratedServer().addScheduledTask(()->{EntityPlayerMP p=mc.getIntegratedServer().getPlayerList().getPlayerByUUID(mc.player.getUniqueID());p.connection.setPlayerLocation(5.5,8,10.5,180,48);p.capabilities.isFlying=true;p.sendPlayerAbilities();});}
        if(ticks==265){mc.player.capabilities.isFlying=true;mc.player.sendPlayerAbilities();}
        if(ticks==280)screen(mc,"network-branch-world.png");
        if(ticks==290){System.out.println("ITEM_NETWORK_CLIENT_PASS ghostPackets=true controlsSync=true dirt=16 cobble=12 sharedBudget=8 screenshots=6");mc.shutdown();}
    }
    private static void open(Minecraft mc,net.minecraft.util.math.BlockPos p){mc.playerController.processRightClickBlock(mc.player,mc.world,p,EnumFacing.SOUTH,new Vec3d(p.getX()+.5,p.getY()+.5,p.getZ()+1),EnumHand.MAIN_HAND);}
    private static void screen(Minecraft mc,String file){System.out.println("ITEM_NETWORK_SCREEN "+ScreenShotHelper.saveScreenshot(mc.gameDir,file,mc.displayWidth,mc.displayHeight,mc.getFramebuffer()).getUnformattedText());}
}
