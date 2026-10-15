package dev.agentic.blockactions;
import net.minecraft.client.gui.*;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.block.model.ModelResourceLocation;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Slot;
import net.minecraft.item.Item;
import net.minecraftforge.client.event.ModelRegistryEvent;
import net.minecraftforge.client.model.*;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
@Mod.EventBusSubscriber(modid=MachineStarter.ID,value=Side.CLIENT)
public final class ClientHooks extends MachineStarter.CommonProxy {
    @SubscribeEvent public static void target(net.minecraftforge.client.event.DrawBlockHighlightEvent e){
        if(e.getTarget().typeOfHit!=net.minecraft.util.math.RayTraceResult.Type.BLOCK)return;
        net.minecraft.client.Minecraft mc=net.minecraft.client.Minecraft.getMinecraft();
        net.minecraft.tileentity.TileEntity tile=mc.world.getTileEntity(e.getTarget().getBlockPos());
        if(!(tile instanceof MachineTile))return;
        MachineTile m=(MachineTile)tile;net.minecraft.util.math.BlockPos p=m.target();
        EntityPlayer viewer=e.getPlayer();float f=e.getPartialTicks();
        double x=viewer.lastTickPosX+(viewer.posX-viewer.lastTickPosX)*f,y=viewer.lastTickPosY+(viewer.posY-viewer.lastTickPosY)*f,z=viewer.lastTickPosZ+(viewer.posZ-viewer.lastTickPosZ)*f;
        GlStateManager.enableBlend();GlStateManager.tryBlendFuncSeparate(770,771,1,0);GlStateManager.disableTexture2D();GlStateManager.depthMask(false);
        org.lwjgl.opengl.GL11.glLineWidth(3F);
        net.minecraft.client.renderer.RenderGlobal.drawSelectionBoundingBox(new net.minecraft.util.math.AxisAlignedBB(p).grow(.005).offset(-x,-y,-z),0,1,1,1);
        org.lwjgl.opengl.GL11.glLineWidth(1F);GlStateManager.depthMask(true);GlStateManager.enableTexture2D();GlStateManager.disableBlend();
    }
    @SubscribeEvent public static void models(ModelRegistryEvent e){
        ModelLoader.setCustomModelResourceLocation(Item.getItemFromBlock(MachineStarter.MACHINE),0,new ModelResourceLocation(MachineStarter.MACHINE.getRegistryName(),"inventory"));
        ModelLoader.setCustomStateMapper(MachineStarter.FLUID_BLOCK,new net.minecraft.client.renderer.block.statemap.StateMapperBase(){
            @Override protected ModelResourceLocation getModelResourceLocation(net.minecraft.block.state.IBlockState s){return new ModelResourceLocation("blockactions:proof_fluid","fluid");}});
    }
    @Override public Object screen(EntityPlayer p,MachineTile t){return new Screen(new MachineContainer(p.inventory,t));}
    public static final class Screen extends GuiContainer {
        public final MachineContainer menu;
        public Screen(MachineContainer m){super(m);menu=m;xSize=306;ySize=280;}
        @Override public void initGui(){super.initGui();buttonList.clear();
            for(int i=0;i<10;i++)buttonList.add(new GuiButton(i,guiLeft+8+(i%2)*150,guiTop+22+(i/2)*21,140,20,""));labels();}
        private void labels(){MachineTile t=menu.tile;String[] s={t.armed?"Pause":"Arm (single steps)","Mode: "+MachineTile.MODES[t.mode],"Target: "+net.minecraft.util.EnumFacing.byIndex(t.direction),"Mining: "+MachineTile.POLICIES[t.policy],"Place: "+(t.context==0?"Player item-use":"Direct state"),"Yaw: "+new String[]{"South","West","North","East"}[t.yaw],"Hit face: "+net.minecraft.util.EnumFacing.byIndex(t.face),"Hit Y: "+(t.high==0?"0.25 bottom":"0.75 top"),"Direct meta: "+t.metadata,"STEP once"};for(GuiButton b:buttonList)b.displayString=s[b.id];}
        @Override protected void actionPerformed(GuiButton b){mc.playerController.sendEnchantPacket(menu.windowId,b.id);}
        @Override protected void drawGuiContainerBackgroundLayer(float p,int x,int y){drawRect(guiLeft,guiTop,guiLeft+xSize,guiTop+ySize,0xFFD0D0D0);for(Slot s:inventorySlots.inventorySlots){int a=guiLeft+s.xPos,b=guiTop+s.yPos;drawRect(a-1,b-1,a+17,b+17,0xFF404040);drawRect(a,b,a+16,b+16,0xFF909090);}GlStateManager.color(1,1,1,1);}
        @Override protected void drawGuiContainerForegroundLayer(int x,int y){MachineTile t=menu.tile;
            fontRenderer.drawString("Block Actions - single target / no auto-run",8,6,0x303030);
            net.minecraft.util.math.BlockPos p=t.target();fontRenderer.drawString("Target XYZ: "+p.getX()+", "+p.getY()+", "+p.getZ(),8,126,0x303030);
            fontRenderer.drawString("Status: "+MachineTile.STATUS[t.status],8,137,0x303030);
            fontRenderer.drawString("Item  Tool       Outputs (6 slots)",12,148,0x303030);
            fontRenderer.drawString("Tank: "+t.tank.getFluidAmount()+" / 4000 mB "+(t.tank.getFluid()==null?"empty":t.tank.getFluid().getFluid().getName()),8,180,0x303030);
            fontRenderer.drawString("Saved XP: "+t.xp,8,190,0x303030);
        }
        @Override public void drawScreen(int x,int y,float p){labels();drawDefaultBackground();super.drawScreen(x,y,p);renderHoveredToolTip(x,y);}
    }
}
