package dev.agentic.clockworkbottle;
import net.minecraft.item.Item;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
@Mod.EventBusSubscriber(modid=ClockworkBottle.MOD_ID)
public final class ModItems {
    public static Item BOTTLE;
    private ModItems() {}
    @SubscribeEvent public static void register(RegistryEvent.Register<Item> event) {
        BOTTLE=new BottleItem();
        event.getRegistry().register(BOTTLE);
    }
}
