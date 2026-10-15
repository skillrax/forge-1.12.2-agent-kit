package dev.agentic.rfnetwork;
import net.minecraft.block.Block;
import net.minecraft.item.Item;
import net.minecraft.client.renderer.block.model.ModelResourceLocation;
import net.minecraftforge.client.event.ModelRegistryEvent;
import net.minecraftforge.client.model.ModelLoader;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
@Mod.EventBusSubscriber(modid=RfProof.ID,value=Side.CLIENT)
public final class ClientModels {
    @SubscribeEvent public static void models(ModelRegistryEvent e){for(Block b:new Block[]{Content.GENERATOR,Content.STORAGE,Content.SINK,Content.CABLE})model(Item.getItemFromBlock(b));model(Content.UPGRADE);}
    private static void model(Item i){ModelLoader.setCustomModelResourceLocation(i,0,new ModelResourceLocation(i.getRegistryName(),"inventory"));}
}
