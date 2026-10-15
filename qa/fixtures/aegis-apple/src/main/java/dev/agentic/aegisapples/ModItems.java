package dev.agentic.aegisapples;
import net.minecraft.item.Item;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
@Mod.EventBusSubscriber(modid = AegisApples.MOD_ID)
public final class ModItems {
    public static Item AEGIS_APPLE;
    private ModItems() {}
    @SubscribeEvent
    public static void registerItems(RegistryEvent.Register<Item> event) {
        AEGIS_APPLE = new AegisApple().setRegistryName(AegisApples.MOD_ID, "aegis_apple")
            .setTranslationKey(AegisApples.MOD_ID + ".aegis_apple");
        event.getRegistry().register(AEGIS_APPLE);
    }
}
