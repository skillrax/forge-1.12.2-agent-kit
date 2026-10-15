package dev.agentic.crafting;
import java.io.IOException;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;

public final class ProcessorScreen extends GuiContainer {
    private final ProcessorContainer menu;
    private static final String[] STATUS={"Paused","No registered pattern recipe","Supply matching ingredients","Output / remainder space blocked","Needs RF","Crafting","Recipe rejects actual ingredients"};
    public ProcessorScreen(ProcessorContainer menu) {super(menu);this.menu=menu;xSize=256;ySize=251;}
    @Override public void initGui() {
        super.initGui();buttonList.add(new GuiButton(0,guiLeft+8,guiTop+118,68,20,"Run / Pause"));
        buttonList.add(new GuiButton(1,guiLeft+82,guiTop+118,88,20,"Clear Pattern"));
        buttonList.add(new GuiButton(2,guiLeft+8,guiTop+140,116,20,""));
        buttonList.add(new GuiButton(3,guiLeft+130,guiTop+140,118,20,""));
    }
    @Override protected void actionPerformed(GuiButton button) throws IOException {mc.playerController.sendEnchantPacket(menu.windowId,button.id);}
    @Override protected void drawGuiContainerBackgroundLayer(float partial,int mouseX,int mouseY) {
        drawRect(guiLeft,guiTop,guiLeft+xSize,guiTop+ySize,0xFFD0D0D0);
        for(Slot slot:inventorySlots.inventorySlots) {
            int x=guiLeft+slot.xPos,y=guiTop+slot.yPos;
            drawRect(x-1,y-1,x+17,y+17,0xFF404040);drawRect(x,y,x+16,y+16,slot.slotNumber<9?0xFF6088A0:0xFF909090);
        }
        drawRect(guiLeft+178,guiTop+124,guiLeft+246,guiTop+132,0xFF404040);
        drawRect(guiLeft+179,guiTop+125,guiLeft+179+menu.tile.getField(0)*66/20,guiTop+131,0xFF408040);GlStateManager.color(1,1,1,1);
    }
    @Override protected void drawGuiContainerForegroundLayer(int x,int y) {
        fontRenderer.drawString("Powered Crafter",8,6,0x404040);
        fontRenderer.drawString(menu.tile.getField(1)+"/4000 RF | 80 RF/craft",8,18,0x404040);
        fontRenderer.drawString("Ghost pattern",8,32,0x205070);fontRenderer.drawString("Ingredients",82,32,0x404040);
        fontRenderer.drawString("Result + returns",156,32,0x404040);
        fontRenderer.drawString("L: copy held item. R: erase. Ghosts use no items.",8,99,0x205070);
        fontRenderer.drawString(STATUS[Math.max(0,Math.min(6,menu.tile.getField(3)))],8,109,0x404040);
        ItemStack p=menu.tile.preview();fontRenderer.drawString(fontRenderer.trimStringToWidth(p.isEmpty()?"No recipe":p.getCount()+" x "+p.getDisplayName(),92),156,82,0x404040);
    }
    @Override public void drawScreen(int x,int y,float partial) {
        if(!buttonList.isEmpty())buttonList.get(0).displayString=menu.tile.getField(2)==0?"Run":"Pause";
        if(buttonList.size()>3) {
            buttonList.get(2).displayString="Ignore NBT: "+(menu.tile.getField(4)==0?"OFF":"ON");
            buttonList.get(3).displayString="Ignore meta: "+(menu.tile.getField(5)==0?"OFF":"ON");
        }
        drawDefaultBackground();super.drawScreen(x,y,partial);renderHoveredToolTip(x,y);
    }
}
