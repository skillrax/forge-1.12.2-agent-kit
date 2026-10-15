package dev.agentic.craftingchecks;
import dev.agentic.crafting.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiMainMenu;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.inventory.ClickType;
import net.minecraft.item.*;
import net.minecraft.util.*;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.*;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.Side;
import org.lwjgl.opengl.*;

@Mod.EventBusSubscriber(modid="craftingchecks",value=Side.CLIENT)
public final class ClientChecks {
    private static boolean launched,positioned;private static int ticks;
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent e) throws Exception {
        if(e.phase!=TickEvent.Phase.END)return;Minecraft mc=Minecraft.getMinecraft();
        if(!launched && mc.currentScreen instanceof GuiMainMenu) {
            launched=true;Display.setDisplayMode(new DisplayMode(1100,800));mc.resize(1100,800);
            mc.gameSettings.guiScale=2;mc.gameSettings.pauseOnLostFocus=false;mc.gameSettings.renderDistanceChunks=4;
            mc.launchIntegratedServer("crafting-observation-"+System.currentTimeMillis(),"Crafting Observation",new WorldSettings(12,GameType.CREATIVE,false,false,WorldType.FLAT));return;
        }
        if(mc.world==null || mc.player==null || mc.getIntegratedServer()==null)return;
        if(!positioned){positioned=true;mc.getIntegratedServer().addScheduledTask(()->{
            EntityPlayerMP p=mc.getIntegratedServer().getPlayerList().getPlayerByUUID(mc.player.getUniqueID());
            p.connection.setPlayerLocation(2.5,4,3.5,180,10);p.inventory.setInventorySlotContents(0,new ItemStack(Blocks.LOG,5));
            // Remove seeded pattern so the actual ghost click establishes it.
            ((ProcessorTile)p.world.getTileEntity(ProofChecks.POS)).control(1);
        });return;}
        ticks++;
        if(ticks==40)mc.playerController.processRightClickBlock(mc.player,mc.world,ProofChecks.POS,EnumFacing.SOUTH,new Vec3d(2.5,4.5,1),EnumHand.MAIN_HAND);
        if(!(mc.player.openContainer instanceof ProcessorContainer))return;
        ProcessorContainer c=(ProcessorContainer)mc.player.openContainer;
        if(ticks==55)mc.playerController.windowClick(c.windowId,55,0,ClickType.PICKUP,mc.player); // hotbar 0
        if(ticks==65)mc.playerController.windowClick(c.windowId,0,0,ClickType.PICKUP,mc.player);
        if(ticks==80){screen(mc,"crafting-pattern.png");System.out.println("CRAFTING_GHOST cursor="+mc.player.inventory.getItemStack()+" pattern="+c.tile.pattern.getStackInSlot(0)+" hotbar="+c.getSlot(55).getStack()+" pos="+mc.player.getPosition());ProofChecks.require(mc.player.inventory.getItemStack().getCount()==5 && c.tile.pattern.getStackInSlot(0).getCount()==1,"non-consuming actual ghost packet");}
        if(ticks==85)mc.playerController.windowClick(c.windowId,55,0,ClickType.PICKUP,mc.player);
        if(ticks==95)mc.playerController.sendEnchantPacket(c.windowId,0);
        if(ticks==155){ProofChecks.require(ProofChecks.count(c.tile,Item.getItemFromBlock(Blocks.PLANKS))==8,"graphical ordinary outputs");screen(mc,"crafting-planks.png");mc.playerController.sendEnchantPacket(c.windowId,0);}
        if(ticks==170)mc.playerController.sendEnchantPacket(c.windowId,1);
        if(ticks==180){ProofChecks.require(c.tile.pattern.isEmpty() && c.tile.getField(2)==0,"clear/pause server controls sync");
            mc.getIntegratedServer().addScheduledTask(()->ProofChecks.cake((ProcessorTile)mc.getIntegratedServer().getWorld(0).getTileEntity(ProofChecks.POS)));}
        if(ticks==200)mc.playerController.sendEnchantPacket(c.windowId,0);
        if(ticks==245){ProofChecks.require(ProofChecks.count(c.tile,Items.CAKE)==1 && ProofChecks.count(c.tile,Items.BUCKET)==3,"graphical cake/remainders");screen(mc,"crafting-cake.png");}
        if(ticks==260)mc.playerController.sendEnchantPacket(c.windowId,0);
        if(ticks==270)mc.getIntegratedServer().addScheduledTask(()->{
            ProcessorTile t=(ProcessorTile)mc.getIntegratedServer().getWorld(0).getTileEntity(ProofChecks.POS);
            t.clear();t.control(1);ProofChecks.ordinary(t,1);
            ItemStack tagged=new ItemStack(Blocks.LOG);net.minecraft.nbt.NBTTagCompound data=new net.minecraft.nbt.NBTTagCompound();
            data.setBoolean("PlayerTouched",true);tagged.setTagCompound(data);t.select(0,tagged);
        });
        if(ticks==280)mc.playerController.sendEnchantPacket(c.windowId,0);
        if(ticks==310){ProofChecks.require(ProofChecks.count(c.tile,Item.getItemFromBlock(Blocks.PLANKS))==0 && c.tile.getField(4)==0,"graphical strict NBT blocks");screen(mc,"crafting-fuzzy-strict.png");mc.playerController.sendEnchantPacket(c.windowId,2);}
        if(ticks==350){ProofChecks.require(c.tile.getField(4)==1 && ProofChecks.count(c.tile,Item.getItemFromBlock(Blocks.PLANKS))==4,"NBT toggle packet sync and craft");mc.playerController.sendEnchantPacket(c.windowId,3);}
        if(ticks==375){ProofChecks.require(c.tile.getField(5)==1,"metadata toggle packet sync");screen(mc,"crafting-fuzzy-on.png");}
        if(ticks==390){System.out.println("CRAFTING_CLIENT_PASS ghostCopyNoConsumption=true serverControls=true ordinary=8 cake=1 buckets=3 fuzzyToggles=true strictThenIgnoreNBT=true screenshots=5");mc.shutdown();}
    }
    private static void screen(Minecraft mc,String file) {System.out.println("CRAFTING_SCREEN "+ScreenShotHelper.saveScreenshot(mc.gameDir,file,mc.displayWidth,mc.displayHeight,mc.getFramebuffer()).getUnformattedText());}
}
