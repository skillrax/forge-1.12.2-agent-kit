package dev.agentic.blockchecks;
import dev.agentic.blockactions.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.*;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.*;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ScreenShotHelper;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.*;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.Side;
import org.lwjgl.opengl.*;
@Mod.EventBusSubscriber(modid="blockchecks",value=Side.CLIENT)
public final class ClientChecks {
    private static boolean launched,opened,requested;private static int ticks;
    private static final BlockPos POS=new BlockPos(0,6,0);
    private static void ok(boolean b,String s){if(!b)throw new IllegalStateException("BLOCK_ASSERTIONS_FAIL: client "+s);}
    private static MachineTile tile(){return ((MachineContainer)Minecraft.getMinecraft().player.openContainer).tile;}
    private static void click(int id)throws Exception{
        Minecraft mc=Minecraft.getMinecraft();ok(mc.currentScreen instanceof ClientHooks.Screen,"screen");
        ScaledResolution sr=new ScaledResolution(mc);int left=(sr.getScaledWidth()-306)/2,top=(sr.getScaledHeight()-280)/2;
        java.lang.reflect.Method press=GuiContainer.class.getDeclaredMethod("mouseClicked",int.class,int.class,int.class);press.setAccessible(true);
        java.lang.reflect.Method release=GuiContainer.class.getDeclaredMethod("mouseReleased",int.class,int.class,int.class);release.setAccessible(true);
        int x=left+8+(id%2)*150+60,y=top+22+(id/2)*21+10;
        press.invoke(mc.currentScreen,x,y,0);release.invoke(mc.currentScreen,x,y,0);
    }
    private static void shot(String name){Minecraft mc=Minecraft.getMinecraft();System.out.println("BLOCK_CLIENT_SCREEN "+ScreenShotHelper.saveScreenshot(mc.gameDir,name,mc.displayWidth,mc.displayHeight,mc.getFramebuffer()).getUnformattedText());}
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent e)throws Exception{
        if(e.phase!=TickEvent.Phase.END)return;Minecraft mc=Minecraft.getMinecraft();
        if(!launched && mc.currentScreen instanceof GuiMainMenu){launched=true;Display.setDisplayMode(new DisplayMode(1280,900));mc.resize(1280,900);mc.gameSettings.pauseOnLostFocus=false;mc.gameSettings.guiScale=3;mc.gameSettings.renderDistanceChunks=6;mc.launchIntegratedServer("block-preview-"+System.currentTimeMillis(),"Block Actions UI",new WorldSettings(12,GameType.CREATIVE,false,false,WorldType.FLAT));return;}
        if(mc.world==null || mc.player==null || mc.getIntegratedServer()==null)return;
        if(!opened){opened=true;mc.getIntegratedServer().addScheduledTask(()->{
            EntityPlayerMP p=mc.getIntegratedServer().getPlayerList().getPlayerByUUID(mc.player.getUniqueID());WorldServer w=p.getServerWorld();
            for(int x=-3;x<=3;x++)for(int z=-3;z<=3;z++)w.getChunk(x,z);
            w.setBlockState(POS,MachineStarter.MACHINE.getDefaultState(),3);w.setBlockState(POS.east(),Blocks.STONE.getDefaultState(),3);w.setBlockToAir(POS.down());
            MachineTile t=(MachineTile)w.getTileEntity(POS);t.setInventorySlotContents(1,new ItemStack(Items.DIAMOND_PICKAXE));t.setInventorySlotContents(0,new ItemStack(Blocks.LEAVES,2));
            p.capabilities.isFlying=true;p.sendPlayerAbilities();p.connection.setPlayerLocation(.5,6,3.5,180,20);
        });return;}
        if(!requested){if(mc.currentScreen!=null || !(mc.world.getTileEntity(POS) instanceof MachineTile))return;requested=true;mc.getIntegratedServer().addScheduledTask(()->{
            EntityPlayerMP p=mc.getIntegratedServer().getPlayerList().getPlayerByUUID(mc.player.getUniqueID());p.openGui(MachineStarter.instance,0,p.world,POS.getX(),POS.getY(),POS.getZ());});return;}
        ticks++;
        if(ticks==50){ok(mc.currentScreen instanceof ClientHooks.Screen,"open GUI");ok(!tile().armed && tile().status==0,"default paused");shot("block-ui-default.png");click(0);click(3);}
        if(ticks==65){ok(tile().armed && tile().policy==1,"arm / policy synchronization");click(9);}
        if(ticks==85){ok(tile().status==2 && tile().getStackInSlot(2).getItem()==net.minecraft.item.Item.getItemFromBlock(Blocks.STONE) && tile().getStackInSlot(1).getItemDamage()==1,"GUI-driven silk world action / output");shot("block-ui-mined.png");for(int id:new int[]{1,2,4,5,6,7,8})click(id);}
        if(ticks==105){MachineTile t=tile();ok(t.mode==1 && t.direction==0 && t.context==1 && t.yaw==1 && t.face==2 && t.high==1 && t.metadata==1 && t.armed,"all control synchronization");click(9);}
        if(ticks==130){ok(tile().status==2 && tile().getStackInSlot(0).getCount()==1,"GUI placement consumption");ok(mc.world.getBlockState(POS.down()).getBlock()==Blocks.LEAVES,"client received placed target");shot("block-ui-controls.png");}
        if(ticks==135){
            mc.getIntegratedServer().addScheduledTask(()->{EntityPlayerMP p=mc.getIntegratedServer().getPlayerList().getPlayerByUUID(mc.player.getUniqueID());((MachineTile)p.world.getTileEntity(POS)).setInventorySlotContents(0,new ItemStack(Items.WATER_BUCKET));});
            for(int i=0;i<3;i++)click(1);
        }
        if(ticks==155){ok(tile().mode==4,"load bucket mode");click(9);}
        if(ticks==180){ok(tile().tank.getFluidAmount()==1000 && tile().tank.getFluid().getFluid()==net.minecraftforge.fluids.FluidRegistry.WATER && tile().getStackInSlot(0).isEmpty(),"tank properties / bucket action");shot("block-ui-fluid.png");mc.player.closeScreen();mc.player.rotationYaw=180;mc.player.rotationPitch=20;}
        if(ticks==200){ok(mc.objectMouseOver!=null && POS.equals(mc.objectMouseOver.getBlockPos()),"aim at machine for target outline");shot("block-world-target.png");}
        if(ticks==220){System.out.println("BLOCK_CLIENT_PASS screenshots=5 actualGuiMouseCallbacks=10 serverWorldActions=3 synchronized=true");mc.shutdown();}
    }
}
