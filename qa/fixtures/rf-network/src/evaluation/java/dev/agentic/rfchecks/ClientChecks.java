package dev.agentic.rfchecks;
import dev.agentic.rfnetwork.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiMainMenu;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.init.Items;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ScreenShotHelper;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.*;
import net.minecraftforge.fluids.*;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.Side;
import org.lwjgl.opengl.Display;
import org.lwjgl.opengl.DisplayMode;

@Mod.EventBusSubscriber(modid="rfchecks",value=Side.CLIENT)
public final class ClientChecks {
    private static boolean launched,setup,requested;
    private static int ticks;
    private static final BlockPos POS=new BlockPos(0,6,0);
    private static void require(boolean ok,String message){if(!ok)throw new IllegalStateException("RF_ASSERTIONS_FAIL: client "+message);}
    private static void shot(Minecraft mc,String name){System.out.println("RF_CLIENT_SCREEN "+ScreenShotHelper.saveScreenshot(mc.gameDir,name,mc.displayWidth,mc.displayHeight,mc.getFramebuffer()).getUnformattedText());}
    private static void open(Minecraft mc,BlockPos pos){mc.getIntegratedServer().addScheduledTask(()->{EntityPlayerMP p=mc.getIntegratedServer().getPlayerList().getPlayerByUUID(mc.player.getUniqueID());p.openGui(RfProof.instance,0,p.world,pos.getX(),pos.getY(),pos.getZ());});}
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent e)throws Exception{
        if(Boolean.getBoolean("rfnetwork.flight"))return;
        if(e.phase!=TickEvent.Phase.END)return;Minecraft mc=Minecraft.getMinecraft();
        if(!launched && mc.currentScreen instanceof GuiMainMenu){launched=true;Display.setDisplayMode(new DisplayMode(1280,900));mc.resize(1280,900);mc.gameSettings.pauseOnLostFocus=false;mc.gameSettings.guiScale=3;mc.gameSettings.renderDistanceChunks=4;mc.launchIntegratedServer("rf-preview-"+System.currentTimeMillis(),"RF Preview",new WorldSettings(12,GameType.CREATIVE,false,false,WorldType.FLAT));return;}
        if(mc.world==null || mc.player==null || mc.getIntegratedServer()==null)return;
        if(!setup){setup=true;mc.getIntegratedServer().addScheduledTask(()->{
            EntityPlayerMP p=mc.getIntegratedServer().getPlayerList().getPlayerByUUID(mc.player.getUniqueID());WorldServer w=p.getServerWorld();
            w.setBlockState(POS,Content.STORAGE.getDefaultState(),3);StorageTile s=(StorageTile)w.getTileEntity(POS);NBTTagCompound n=s.writeToNBT(new NBTTagCompound());n.setInteger("RF",1234567);s.readFromNBT(n);for(EnumFacing f:EnumFacing.values())while(s.role(f)!=0)s.control(f.getIndex());
            w.setBlockState(POS.west(2),Content.GENERATOR.getDefaultState(),3);GeneratorTile g=(GeneratorTile)w.getTileEntity(POS.west(2));g.setInventorySlotContents(0,new ItemStack(Items.COAL,3));g.setInventorySlotContents(1,new ItemStack(Content.UPGRADE));g.fluids().fill(new FluidStack(FluidRegistry.LAVA,3000),true);
            w.setBlockState(POS.west(),Content.CABLE.getDefaultState(),3);w.setBlockState(POS.east(),Content.CABLE.getDefaultState(),3);w.setBlockState(POS.east(2),Content.SINK.getDefaultState(),3);
            w.setBlockState(POS.west().up(),Content.CABLE.getDefaultState(),3);w.setBlockState(POS.west().north(),Content.CABLE.getDefaultState(),3);
            p.connection.setPlayerLocation(.5,6,4.5,180,10);
        });return;}
        if(!requested){if(mc.currentScreen!=null || !(mc.world.getTileEntity(POS) instanceof StorageTile))return;requested=true;open(mc,POS);return;}
        ticks++;
        if(ticks==45){require(mc.currentScreen instanceof MachineScreen,"Storage menu open");StorageTile s=(StorageTile)((MachineContainer)mc.player.openContainer).tile;require(s.stored()==1234567 && s.capacity()==2000000,"Large current/max synchronized");shot(mc,"rf-storage.png");ControlPacket.CHANNEL.sendToServer(new ControlPacket(mc.player.openContainer.windowId,5));}
        if(ticks==65){StorageTile s=(StorageTile)((MachineContainer)mc.player.openContainer).tile;require(s.role(EnumFacing.EAST)==1,"Custom face action server synchronized");shot(mc,"rf-storage-face.png");ControlPacket.CHANNEL.sendToServer(new ControlPacket(mc.player.openContainer.windowId,5));mc.player.closeScreen();}
        if(ticks==70)open(mc,POS.east(2));
        if(ticks==110){
            require(mc.currentScreen instanceof MachineScreen,"Sink menu open");
            java.lang.reflect.Field field=MachineScreen.class.getDeclaredField("demand");field.setAccessible(true);GuiTextField text=(GuiTextField)field.get(mc.currentScreen);
            require(text.getText().equals("1000"),"Numeric text field receives saved default after initial property sync");text.setFocused(true);text.setText("");
            java.lang.reflect.Method key=MachineScreen.class.getDeclaredMethod("keyTyped",char.class,int.class);key.setAccessible(true);for(char c:"87654".toCharArray())key.invoke(mc.currentScreen,c,0);
            ScaledResolution size=new ScaledResolution(mc);java.lang.reflect.Method click=MachineScreen.class.getDeclaredMethod("mouseClicked",int.class,int.class,int.class);click.setAccessible(true);click.invoke(mc.currentScreen,(size.getScaledWidth()-340)/2+150,(size.getScaledHeight()-224)/2+54,0);
        }
        if(ticks==135){SinkTile s=(SinkTile)((MachineContainer)mc.player.openContainer).tile;require(s.requested()==87654,"Five digit sink request synchronized through custom packet");require((s.getField(6)|(s.getField(7)<<16))==20000,"Sink displays actual completed tick drain from capped storage");shot(mc,"rf-sink.png");ControlPacket.CHANNEL.sendToServer(new ControlPacket(mc.player.openContainer.windowId,100+100001));}
        if(ticks==150){require(((SinkTile)((MachineContainer)mc.player.openContainer).tile).requested()==87654,"Invalid numeric server action rejected");mc.player.closeScreen();}
        if(ticks==155)open(mc,POS.west(2));
        if(ticks==195){require(mc.currentScreen instanceof MachineScreen,"Generator menu open");GeneratorTile g=(GeneratorTile)((MachineContainer)mc.player.openContainer).tile;require(g.lava()==3000 && g.upgraded(),"Generator tank and upgrade menu sync");shot(mc,"rf-generator.png");mc.player.closeScreen();}
        if(ticks==225){shot(mc,"rf-cables.png");System.out.println("RF_CLIENT_PASS screenshots=5 storage-large-sync=true face-action=true numeric-action=true invalid-action-rejected=true");mc.shutdown();}
    }
}
