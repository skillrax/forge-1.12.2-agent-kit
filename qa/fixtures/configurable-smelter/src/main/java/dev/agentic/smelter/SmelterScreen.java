package dev.agentic.smelter;
import java.io.IOException;
import java.util.Arrays;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.inventory.Slot;
import net.minecraft.util.EnumFacing;

public final class SmelterScreen extends GuiContainer {
    private static final int[] COLORS={0xFF555555,0xFF408ADA,0xFFE7AE38,0xFFDC623D,0xFFB66ED2};
    private static final String[] ROLE={"Disabled","Main input","Secondary input","Main output","Secondary output"};
    private static final String[] SHORT={"--","MI","SI","MO","SO"};
    private static final String[] STATUS={"Running","Paused by redstone","Insert smeltable input","Output blocked","Needs RF","Nearby chunk unavailable"};
    private static final String[] TRANSFER={"Face disabled","Automatic transfer OFF","Paused by redstone","No items to move","Adjacent chunk unavailable","No adjacent inventory","No item capability on neighbor face","Chest-area chunks unavailable","Destination rejects items / no space","Items transferred"};
    private final SmelterContainer menu;
    public SmelterScreen(SmelterContainer menu){super(menu);this.menu=menu;xSize=344;ySize=238;}
    @Override public void initGui(){
        super.initGui();buttonList.clear();
        // Unfolded world-direction cube: UP / WEST NORTH EAST / DOWN / SOUTH.
        int[][] facePos={{244,78},{244,30},{244,54},{244,102},{202,54},{286,54}};
        for(EnumFacing face:EnumFacing.values()){
            int i=face.getIndex();buttonList.add(new FaceButton(i,guiLeft+facePos[i][0],guiTop+facePos[i][1]));
        }
        buttonList.add(new GuiButton(6,guiLeft+8,guiTop+124,104,20,""));
        buttonList.add(new GuiButton(7,guiLeft+120,guiTop+124,104,20,""));
        buttonList.add(new GuiButton(8,guiLeft+232,guiTop+124,104,20,""));
    }
    private final class FaceButton extends GuiButton {
        FaceButton(int id,int x,int y){super(id,x,y,40,22,"");}
        @Override public void drawButton(Minecraft mc,int mouseX,int mouseY,float partial){
            super.drawButton(mc,mouseX,mouseY,partial);
            if(visible)drawRect(x+2,y+height-4,x+width-2,y+height-2,COLORS[menu.tile.getField(6+id)]);
        }
    }
    @Override protected void actionPerformed(GuiButton button) throws IOException {
        mc.playerController.sendEnchantPacket(menu.windowId,button.id);
    }
    @Override protected void drawGuiContainerBackgroundLayer(float partial,int mouseX,int mouseY){
        drawRect(guiLeft,guiTop,guiLeft+xSize,guiTop+ySize,0xFFCECED2);
        for(int i=0;i<inventorySlots.inventorySlots.size();i++){
            Slot slot=inventorySlots.inventorySlots.get(i);int x=guiLeft+slot.xPos,y=guiTop+slot.yPos;
            int color=i<4?COLORS[i+1]:i==4?0xFF58864D:0xFF444444;
            drawRect(x-2,y-2,x+18,y+18,color);drawRect(x,y,x+16,y+16,0xFF888888);
        }
        drawRect(guiLeft+44,guiTop+47,guiLeft+136,guiTop+56,0xFF555555);
        int width=Math.min(90,menu.tile.getField(0)*90/Math.max(1,menu.tile.getField(2)));
        drawRect(guiLeft+45,guiTop+48,guiLeft+45+width,guiTop+55,0xFF438A48);
        GlStateManager.color(1,1,1,1);
    }
    @Override protected void drawGuiContainerForegroundLayer(int mouseX,int mouseY){
        fontRenderer.drawString("Configurable Smelter",8,8,0x303030);
        fontRenderer.drawString(menu.tile.getField(1)+" / 16000 RF",8,22,0x303030);
        fontRenderer.drawString("Main",16,35,0x303030);fontRenderer.drawString("Main",142,35,0x303030);
        fontRenderer.drawString("Sec",20,67,0x303030);fontRenderer.drawString("Sec",146,67,0x303030);
        fontRenderer.drawString("Upgrade",71,87,0x303030);
        fontRenderer.drawString("Faces: world directions",202,8,0x303030);
        fontRenderer.drawString("Click to cycle; hover for slot",186,20,0x303030);
        int state=Math.max(0,Math.min(5,menu.tile.getField(12)));
        fontRenderer.drawString(STATUS[state],8,99,0x303030);
        SmeltingOperation r=menu.tile.operation();
        fontRenderer.drawString(r==null?"Uses registered furnace recipes":r.usesSand?"Ore + sand: 2 ingots + brick":"Smelts: "+r.main.getDisplayName(),8,110,0x303030);
        fontRenderer.drawString("Inventory",91,146,0x303030);
    }
    @Override public void drawScreen(int mouseX,int mouseY,float partial){
        for(GuiButton b:buttonList){
            if(b.id<6){String direction=EnumFacing.byIndex(b.id).getName().substring(0,1).toUpperCase();b.displayString=direction+" "+SHORT[menu.tile.getField(6+b.id)];}
            else if(b.id==6)b.displayString="Auto input: "+(menu.tile.getField(3)==1?"ON":"OFF");
            else if(b.id==7)b.displayString="Auto output: "+(menu.tile.getField(4)==1?"ON":"OFF");
            else b.displayString="Run: "+(menu.tile.getField(5)==1?"powered":"unpowered");
        }
        drawDefaultBackground();super.drawScreen(mouseX,mouseY,partial);renderHoveredToolTip(mouseX,mouseY);
        for(GuiButton b:buttonList)if(b.isMouseOver()){
            if(b.id<6)drawHoveringText(Arrays.asList(EnumFacing.byIndex(b.id).getName().toUpperCase()+": "+ROLE[menu.tile.getField(6+b.id)],"Last auto check: "+TRANSFER[menu.tile.getField(14+b.id)],"Cycle: disabled > main in > secondary in","> main out > secondary out", "Item faces do not restrict RF intake"),mouseX,mouseY);
            else if(b.id==8)drawHoveringText(Arrays.asList("Run only while "+(menu.tile.getField(5)==1?"powered":"unpowered"),"Signal now: "+(menu.tile.getField(13)==1?"POWERED":"UNPOWERED"),"Pauses work and automatic item transfers"),mouseX,mouseY);
        }
    }
}
