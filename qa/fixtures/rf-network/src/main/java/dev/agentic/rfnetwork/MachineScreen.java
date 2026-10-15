package dev.agentic.rfnetwork;
import java.io.IOException;
import net.minecraft.client.gui.*;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.inventory.Slot;
import net.minecraft.util.EnumFacing;
public final class MachineScreen extends GuiContainer {
    private final MachineContainer menu;
    private GuiTextField demand;
    private boolean edited;
    private String feedback="";
    private static final String[] ROLES={"OFF","IN","OUT"};
    public MachineScreen(MachineContainer c){super(c);menu=c;xSize=340;ySize=224;}
    private int value(int i){return menu.tile.getField(i*2)|(menu.tile.getField(i*2+1)<<16);}
    public void initGui(){super.initGui();buttonList.clear();
        if(menu.tile instanceof StorageTile){int[][] xy={{241,70},{241,22},{241,46},{241,94},{196,46},{286,46}};for(int i=0;i<6;i++)buttonList.add(new GuiButton(i,guiLeft+xy[i][0],guiTop+xy[i][1],42,20,""));}
        if(menu.tile instanceof SinkTile){demand=new GuiTextField(10,fontRenderer,guiLeft+12,guiTop+45,108,20);demand.setMaxStringLength(6);demand.setText(Integer.toString(value(2)));buttonList.add(new GuiButton(10,guiLeft+132,guiTop+44,76,22,"Apply"));}
    }
    protected void actionPerformed(GuiButton b)throws IOException{
        int action=b.id;
        if(b.id==10){try{int n=Integer.parseInt(demand.getText());if(n<0 || n>SinkTile.LIMIT)throw new NumberFormatException();action=100+n;feedback="Requested: "+n+" RF/t";}catch(NumberFormatException e){feedback="Enter 0 to 100000";return;}}
        ControlPacket.CHANNEL.sendToServer(new ControlPacket(menu.windowId,action));edited=false;
    }
    protected void mouseClicked(int x,int y,int b)throws IOException{super.mouseClicked(x,y,b);if(demand!=null)demand.mouseClicked(x,y,b);}
    protected void keyTyped(char c,int k)throws IOException{if(demand!=null && demand.textboxKeyTyped(c,k))edited=true;else super.keyTyped(c,k);}
    public void updateScreen(){super.updateScreen();if(demand!=null){demand.updateCursorCounter();if(!edited && !demand.isFocused())demand.setText(Integer.toString(value(2)));}}
    protected void drawGuiContainerBackgroundLayer(float p,int x,int y){drawRect(guiLeft,guiTop,guiLeft+xSize,guiTop+ySize,0xFFCECED2);for(Slot s:inventorySlots.inventorySlots){drawRect(guiLeft+s.xPos-1,guiTop+s.yPos-1,guiLeft+s.xPos+17,guiTop+s.yPos+17,0xFF555555);drawRect(guiLeft+s.xPos,guiTop+s.yPos,guiLeft+s.xPos+16,guiTop+s.yPos+16,0xFF999999);}if(demand!=null)demand.drawTextBox();}
    protected void drawGuiContainerForegroundLayer(int x,int y){
        EnergyTile t=menu.tile;int color=0x303030;
        fontRenderer.drawString(t instanceof GeneratorTile?"Fuel Generator":t instanceof StorageTile?"RF Storage":"RF Sink",8,8,color);
        if(t instanceof GeneratorTile){fontRenderer.drawString("Output: "+value(3)+" RF/t",8,24,color);fontRenderer.drawString("Lava: "+value(2)+" / 8000 mB",8,36,color);fontRenderer.drawString("Solid fuel",14,52,color);fontRenderer.drawString("Upgrade",89,52,color);fontRenderer.drawString("Fuel reserve: "+(value(4)/4)+" base RF",8,94,color);fontRenderer.drawString("Lava bucket: right-click block",8,108,color);fontRenderer.drawString("Upgrade: 2x output",175,52,color);fontRenderer.drawString("uses 1.5x fuel / time",175,66,color);}
        else if(t instanceof StorageTile){fontRenderer.drawString(value(0)+" / "+value(1)+" RF",8,24,color);fontRenderer.drawString("Faces: world compass",196,8,color);fontRenderer.drawString("OUT: 20000 RF/t total",8,119,color);}
        else {fontRenderer.drawString("Requested: "+value(2)+" RF/t",8,24,color);fontRenderer.drawString("Received last tick: "+value(3),8,82,color);fontRenderer.drawString(feedback,8,100,color);}
        fontRenderer.drawString("Inventory",8,130,color);
    }
    public void drawScreen(int x,int y,float p){for(GuiButton b:buttonList)if(b.id<6){int r=Math.max(0,Math.min(2,value(2+b.id)));b.displayString=EnumFacing.values()[b.id].getName().substring(0,1).toUpperCase()+":"+ROLES[r];}drawDefaultBackground();super.drawScreen(x,y,p);renderHoveredToolTip(x,y);}
}
