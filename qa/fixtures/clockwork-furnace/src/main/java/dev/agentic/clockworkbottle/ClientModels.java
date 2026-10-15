package dev.agentic.clockworkbottle;
import net.minecraft.client.renderer.block.model.ModelResourceLocation;
import net.minecraftforge.client.event.ModelRegistryEvent;
import net.minecraftforge.client.model.ModelLoader;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
@Mod.EventBusSubscriber(modid=ClockworkBottle.MOD_ID,value=Side.CLIENT)
public final class ClientModels {
    private ClientModels() {}
    @SubscribeEvent public static void register(ModelRegistryEvent event) {
        ModelLoader.setCustomModelResourceLocation(ModItems.BOTTLE,0,new ModelResourceLocation(ModItems.BOTTLE.getRegistryName(),"inventory"));
    }
}
