package dev.agentic.workshop;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.renderer.GlStateManager;
public final class WorkshopScreen extends GuiContainer {
    private final WorkshopContainer menu;
    public WorkshopScreen(WorkshopContainer menu) {super(menu);this.menu=menu;xSize=176;ySize=166;}
    @Override protected void drawGuiContainerBackgroundLayer(float partial,int mouseX,int mouseY) {
        drawRect(guiLeft,guiTop,guiLeft+xSize,guiTop+ySize,0xFFD0D0D0);
        for(net.minecraft.inventory.Slot slot:inventorySlots.inventorySlots) {
            int x=guiLeft+slot.xPos,y=guiTop+slot.yPos;
            drawRect(x-1,y-1,x+17,y+17,0xFF404040);drawRect(x,y,x+16,y+16,0xFF909090);
        }
        if(menu.tile instanceof ProcessorTile) {
            drawRect(guiLeft+80,guiTop+39,guiLeft+108,guiTop+47,0xFF404040);
            int width=menu.tile.getField(0)*26/ProcessorTile.DURATION;
            drawRect(guiLeft+81,guiTop+40,guiLeft+81+width,guiTop+46,0xFFFFFFFF);
        }
        GlStateManager.color(1,1,1,1);
    }
    @Override protected void drawGuiContainerForegroundLayer(int mouseX,int mouseY) {
        fontRenderer.drawString(menu.tile.getDisplayName().getUnformattedText(),8,6,0x404040);
        fontRenderer.drawString("Inventory",8,74,0x404040);
        if(menu.tile instanceof ProcessorTile)fontRenderer.drawString("Cobble -> Stone (5s)",8,58,0x404040);
    }
    @Override public void drawScreen(int mouseX,int mouseY,float partial) {
        drawDefaultBackground();super.drawScreen(mouseX,mouseY,partial);renderHoveredToolTip(mouseX,mouseY);
    }
}
