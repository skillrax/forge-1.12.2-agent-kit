package dev.agentic.workshop;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.inventory.Slot;
public final class PouchScreen extends GuiContainer {
    public PouchScreen(PouchContainer menu) {super(menu);xSize=176;ySize=166;}
    @Override protected void drawGuiContainerBackgroundLayer(float partial,int mouseX,int mouseY) {
        drawRect(guiLeft,guiTop,guiLeft+xSize,guiTop+ySize,0xFFD0D0D0);
        for(Slot slot:inventorySlots.inventorySlots) {
            int x=guiLeft+slot.xPos,y=guiTop+slot.yPos;
            drawRect(x-1,y-1,x+17,y+17,0xFF404040);drawRect(x,y,x+16,y+16,0xFF909090);
        }
        GlStateManager.color(1,1,1,1);
    }
    @Override protected void drawGuiContainerForegroundLayer(int x,int y) {
        fontRenderer.drawString("Test Pouch",8,6,0x404040);fontRenderer.drawString("Inventory",8,74,0x404040);
        fontRenderer.drawString("Active pouch slot locked",8,58,0x404040);
    }
    @Override public void drawScreen(int x,int y,float partial) {
        drawDefaultBackground();super.drawScreen(x,y,partial);renderHoveredToolTip(x,y);
    }
}
