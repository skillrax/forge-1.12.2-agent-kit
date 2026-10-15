package dev.agentic.aegisapples;
import net.minecraft.client.renderer.block.model.ModelResourceLocation;
import net.minecraftforge.client.event.ModelRegistryEvent;
import net.minecraftforge.client.model.ModelLoader;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
@Mod.EventBusSubscriber(modid = AegisApples.MOD_ID, value = Side.CLIENT)
public final class ClientModels {
    private ClientModels() {}
    @SubscribeEvent
    public static void registerModels(ModelRegistryEvent event) {
        ModelLoader.setCustomModelResourceLocation(ModItems.AEGIS_APPLE, 0,
            new ModelResourceLocation(ModItems.AEGIS_APPLE.getRegistryName(), "inventory"));
    }
}
