package dev.agentic.smelter;
import net.minecraft.item.Item;
import net.minecraft.client.renderer.block.model.ModelResourceLocation;
import net.minecraftforge.client.event.ModelRegistryEvent;
import net.minecraftforge.client.model.ModelLoader;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
@Mod.EventBusSubscriber(modid=SmelterProof.ID,value=Side.CLIENT)
public final class ClientModels {
    @SubscribeEvent public static void models(ModelRegistryEvent e){
        for(ProofBlock b:new ProofBlock[]{Content.GENERATOR,Content.PIPE,Content.SMELTER})model(Item.getItemFromBlock(b));
        model(Content.UPGRADE);
    }
    private static void model(Item item){ModelLoader.setCustomModelResourceLocation(item,0,new ModelResourceLocation(item.getRegistryName(),"inventory"));}
}
