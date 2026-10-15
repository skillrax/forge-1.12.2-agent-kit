package dev.agentic.energy;

import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.inventory.Slot;

public final class ProcessorScreen extends GuiContainer {
    private final ProcessorContainer menu;
    public ProcessorScreen(ProcessorContainer menu) {super(menu);this.menu=menu;xSize=176;ySize=166;}
    @Override protected void drawGuiContainerBackgroundLayer(float partial,int mouseX,int mouseY) {
        drawRect(guiLeft,guiTop,guiLeft+xSize,guiTop+ySize,0xFFD0D0D0);
        for(Slot slot:inventorySlots.inventorySlots) {
            int x=guiLeft+slot.xPos,y=guiTop+slot.yPos;
            drawRect(x-1,y-1,x+17,y+17,0xFF404040);drawRect(x,y,x+16,y+16,0xFF909090);
        }
        drawRect(guiLeft+65,guiTop+39,guiLeft+98,guiTop+47,0xFF404040);
        int width=Math.min(31,menu.tile.getField(0)*31/Math.max(1,menu.tile.getField(2)));
        drawRect(guiLeft+66,guiTop+40,guiLeft+66+width,guiTop+46,0xFF408040);
        GlStateManager.color(1,1,1,1);
    }
    @Override protected void drawGuiContainerForegroundLayer(int mouseX,int mouseY) {
        fontRenderer.drawString(menu.tile.getDisplayName().getUnformattedText(),8,6,0x404040);
        fontRenderer.drawString(menu.tile.getField(1)+"/4000 RF",8,19,0x404040);
        ProcessingRecipe recipe=ProcessorTile.recipe(menu.tile.getStackInSlot(0));
        if(recipe==null)fontRenderer.drawString("Insert a recipe input",8,57,0x404040);
        else {
            fontRenderer.drawString("Output: "+recipe.primary.getDisplayName(),8,55,0x404040);
            fontRenderer.drawString(Math.round(recipe.secondaryChance*100)+"% extra "+recipe.secondary.getDisplayName(),8,65,0x404040);
        }
        fontRenderer.drawString("Inventory",8,74,0x404040);
    }
    @Override public void drawScreen(int mouseX,int mouseY,float partial) {
        drawDefaultBackground();super.drawScreen(mouseX,mouseY,partial);renderHoveredToolTip(mouseX,mouseY);
    }
}
