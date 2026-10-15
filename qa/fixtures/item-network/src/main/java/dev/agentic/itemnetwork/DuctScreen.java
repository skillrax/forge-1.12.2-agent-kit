package dev.agentic.itemnetwork;
import java.io.IOException;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.inventory.Slot;
import net.minecraft.util.EnumFacing;
public final class DuctScreen extends GuiContainer {
    private final DuctContainer menu;
    public DuctScreen(DuctContainer c){super(c);menu=c;xSize=220;ySize=244;}
    @Override public void initGui(){super.initGui();
        buttonList.add(new GuiButton(0,guiLeft+8,guiTop+66,100,20,""));buttonList.add(new GuiButton(1,guiLeft+112,guiTop+66,100,20,""));
        buttonList.add(new GuiButton(2,guiLeft+8,guiTop+88,204,20,""));
        buttonList.add(new GuiButton(3,guiLeft+8,guiTop+110,100,20,""));buttonList.add(new GuiButton(4,guiLeft+112,guiTop+110,100,20,""));
    }
    @Override protected void actionPerformed(GuiButton b)throws IOException{mc.playerController.sendEnchantPacket(menu.windowId,b.id);}
    @Override protected void drawGuiContainerBackgroundLayer(float p,int x,int y){drawRect(guiLeft,guiTop,guiLeft+xSize,guiTop+ySize,0xFFD0D0D0);for(Slot s:inventorySlots.inventorySlots){int a=guiLeft+s.xPos,b=guiTop+s.yPos;drawRect(a-1,b-1,a+17,b+17,0xFF404040);drawRect(a,b,a+16,b+16,s.slotNumber==0?0xFF6088A0:0xFF909090);}GlStateManager.color(1,1,1,1);}
    @Override protected void drawGuiContainerForegroundLayer(int x,int y){
        fontRenderer.drawString("Network Item Duct",8,6,0x404040);fontRenderer.drawString("Filter",8,18,0x205070);fontRenderer.drawString("Owned buffer",143,18,0x404040);
        fontRenderer.drawString("L: copy cursor  R: clear (ghost)",8,49,0x205070);
        fontRenderer.drawString("8 items / 10 ticks shared per world",8,135,0x404040);fontRenderer.drawString("Delivered: "+menu.tile.getField(6)+" | empty filter = all",8,146,0x404040);
    }
    @Override public void drawScreen(int x,int y,float p){
        if(buttonList.size()==5){DuctTile t=menu.tile;buttonList.get(0).displayString=new String[]{"Transit","Source","Destination"}[t.role];buttonList.get(1).displayString="Face: "+t.direction().getName();buttonList.get(2).displayString=t.enabled?"Enabled (click to pause / disconnect)":"Paused / disconnected";buttonList.get(3).displayString="Ignore NBT: "+(t.ignoreNBT?"ON":"OFF");buttonList.get(4).displayString="Ignore meta: "+(t.ignoreMetadata?"ON":"OFF");}
        drawDefaultBackground();super.drawScreen(x,y,p);renderHoveredToolTip(x,y);
    }
}
