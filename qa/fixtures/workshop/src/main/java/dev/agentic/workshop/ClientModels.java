package dev.agentic.workshop;
import net.minecraft.item.Item;
import net.minecraft.client.renderer.block.model.ModelResourceLocation;
import net.minecraftforge.client.event.ModelRegistryEvent;
import net.minecraftforge.client.model.ModelLoader;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
@Mod.EventBusSubscriber(modid=Workshop.ID,value=Side.CLIENT)
public final class ClientModels {
    @SubscribeEvent public static void models(ModelRegistryEvent event) {
        ModelLoader.setCustomModelResourceLocation(Content.POUCH,0,new ModelResourceLocation(Content.POUCH.getRegistryName(),"inventory"));
        ModelLoader.setCustomModelResourceLocation(Content.LAUNCHER,0,new ModelResourceLocation(Content.LAUNCHER.getRegistryName(),"inventory"));
        for(Item item:new Item[]{Content.SEEDS,Content.HARVEST,Item.getItemFromBlock(Content.PORTAL),Item.getItemFromBlock(Content.ORE)})
            ModelLoader.setCustomModelResourceLocation(item,0,new ModelResourceLocation(item.getRegistryName(),"inventory"));
        for(WorkshopBlock block:new WorkshopBlock[]{Content.BOX,Content.PROCESSOR})
            ModelLoader.setCustomModelResourceLocation(Item.getItemFromBlock(block),0,
                new ModelResourceLocation(block.getRegistryName(),"inventory"));
    }
}
