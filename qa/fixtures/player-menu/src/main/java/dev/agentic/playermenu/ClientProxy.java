package dev.agentic.playermenu;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.*;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.client.settings.KeyConflictContext;
import net.minecraftforge.fml.client.registry.ClientRegistry;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.lwjgl.input.Keyboard;
import java.io.IOException;
import java.util.Arrays;

public final class ClientProxy extends PlayerMenu.CommonProxy {
    public static final KeyBinding OPEN=new KeyBinding("key.personalmenu.open",KeyConflictContext.IN_GAME,Keyboard.KEY_N,"key.categories.personalmenu");
    public void init(){ClientRegistry.registerKeyBinding(OPEN);MinecraftForge.EVENT_BUS.register(this);}
    @SubscribeEvent public void tick(TickEvent.ClientTickEvent event) {
        if(event.phase!=TickEvent.Phase.END)return;
        Minecraft mc=Minecraft.getMinecraft();boolean pressed=false;
        while(OPEN.isPressed())pressed=true;
        if(pressed && mc.player!=null && mc.world!=null && mc.currentScreen==null)mc.displayGuiScreen(new Screen());
    }
    public void receive(Packets.Snapshot snapshot) {
        Minecraft mc=Minecraft.getMinecraft();mc.addScheduledTask(()->{
            if(mc.currentScreen instanceof Screen)((Screen)mc.currentScreen).accept(snapshot);
        });
    }
    public static final class Screen extends GuiScreen {
        public int counter;public boolean enabled,ready,pending;public int status;
        private int left,top;
        private static final String[] STATUS={"State received from server","Counter increased","Toggle saved","Counter and toggle reset","Invalid action rejected","Counter limit reached"};
        public void initGui(){
            left=(width-300)/2;top=(height-200)/2;buttonList.clear();
            buttonList.add(new GuiButton(1,left+16,top+96,128,20,"Increment +1"));
            buttonList.add(new GuiButton(2,left+156,top+96,128,20,"Toggle"));
            buttonList.add(new GuiButton(3,left+16,top+123,128,20,"Reset both"));
            buttonList.add(new GuiButton(4,left+156,top+123,128,20,"Close"));
            ready=false;request(0);
        }
        private void request(int action){pending=true;PlayerMenu.NETWORK.sendToServer(new Packets.Request(action));}
        public void accept(Packets.Snapshot s){counter=s.counter;enabled=s.enabled;status=s.status;ready=true;pending=false;}
        protected void actionPerformed(GuiButton button)throws IOException {
            if(button.id==4){mc.displayGuiScreen(null);return;}
            if(ready && !pending)request(button.id);
        }
        public boolean doesGuiPauseGame(){return false;}
        public void drawScreen(int mouseX,int mouseY,float partialTicks){
            GlStateManager.disableLighting();GlStateManager.disableDepth();
            drawDefaultBackground();drawRect(left,top,left+300,top+200,0xEE18232F);
            GlStateManager.color(1,1,1,1);
            drawString(fontRenderer,"Player: "+mc.player.getName(),left+16,top+32,0xBFD9F0);
            drawString(fontRenderer,"Counter: "+(ready?Integer.toString(counter):"..."),left+16,top+52,0xFFFFFF);
            drawString(fontRenderer,"Saved toggle: "+(ready?(enabled?"ON":"OFF"):"..."),left+16,top+68,enabled?0x7DE1A6:0xFFFFFF);
            for(GuiButton b:buttonList)if(b.id!=4)b.enabled=ready && !pending;
            super.drawScreen(mouseX,mouseY,partialTicks);
            drawCenteredString(fontRenderer,"Personal Menu",left+150,top+12,0xFFFFFF);
            String text=pending?"Waiting for server...":STATUS[Math.min(status,STATUS.length-1)];
            drawString(fontRenderer,text,left+16,top+155,0x7DE1A6);
            drawString(fontRenderer,"Hover here: your saved personal state",left+16,top+179,0xBFD9F0);
            if(mouseX>=left+16 && mouseX<left+284 && mouseY>=top+176 && mouseY<top+192)
                drawHoveringText(Arrays.asList("Only your player owns these values.","Buttons change state on the server.","Saved through logout and world restart.","Reset clears your counter and toggle.","Counter limit: 1,000,000. Esc closes."),mouseX,mouseY);
            GlStateManager.disableLighting();GlStateManager.enableDepth();
        }
    }
}
