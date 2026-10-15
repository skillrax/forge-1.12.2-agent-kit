package com.example.machinestarter;

import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.block.model.ModelResourceLocation;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Slot;
import net.minecraft.item.Item;
import net.minecraftforge.client.event.ModelRegistryEvent;
import net.minecraftforge.client.model.ModelLoader;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;

@Mod.EventBusSubscriber(modid=MachineStarter.ID,value=Side.CLIENT)
public final class ClientHooks extends MachineStarter.CommonProxy {
    @SubscribeEvent public static void models(ModelRegistryEvent event) {
        ModelLoader.setCustomModelResourceLocation(Item.getItemFromBlock(MachineStarter.MACHINE),0,
            new ModelResourceLocation(MachineStarter.MACHINE.getRegistryName(),"inventory"));
    }
    @Override public Object screen(EntityPlayer player,MachineTile tile) {return new Screen(new MachineContainer(player.inventory,tile));}
    private static final class Screen extends GuiContainer {
        private final MachineContainer menu;
        Screen(MachineContainer menu) {super(menu);this.menu=menu;xSize=176;ySize=166;}
        @Override protected void drawGuiContainerBackgroundLayer(float partial,int mouseX,int mouseY) {
            drawRect(guiLeft,guiTop,guiLeft+xSize,guiTop+ySize,0xFFD0D0D0);
            for(Slot slot:inventorySlots.inventorySlots) {
                int x=guiLeft+slot.xPos,y=guiTop+slot.yPos;
                drawRect(x-1,y-1,x+17,y+17,0xFF404040);drawRect(x,y,x+16,y+16,0xFF909090);
            }
            GlStateManager.color(1,1,1,1);
        }
        @Override protected void drawGuiContainerForegroundLayer(int mouseX,int mouseY) {
            fontRenderer.drawString(menu.tile.getDisplayName().getUnformattedText(),8,6,0x404040);
            fontRenderer.drawString("Inventory scaffold",8,57,0x404040);
            fontRenderer.drawString("Inventory",8,74,0x404040);
        }
        @Override public void drawScreen(int mouseX,int mouseY,float partial) {
            drawDefaultBackground();super.drawScreen(mouseX,mouseY,partial);renderHoveredToolTip(mouseX,mouseY);
        }
    }
}
