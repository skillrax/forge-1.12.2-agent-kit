package dev.agentic.statue;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.model.ModelResourceLocation;
import net.minecraft.client.resources.IReloadableResourceManager;
import net.minecraft.item.Item;
import net.minecraftforge.client.event.ModelRegistryEvent;
import net.minecraftforge.client.model.ModelLoader;
import net.minecraftforge.fml.client.registry.ClientRegistry;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;

@Mod.EventBusSubscriber(modid=ModelStatue.ID,value=Side.CLIENT)
public final class ClientHooks {
    @SubscribeEvent public static void models(ModelRegistryEvent e) {
        ModelLoader.setCustomModelResourceLocation(Item.getItemFromBlock(ModelStatue.STATUE),0,new ModelResourceLocation(ModelStatue.STATUE.getRegistryName(),"inventory"));
        StatueRenderer renderer=new StatueRenderer();ClientRegistry.bindTileEntitySpecialRenderer(StatueTile.class,renderer);
        ((IReloadableResourceManager)Minecraft.getMinecraft().getResourceManager()).registerReloadListener(renderer);
    }
}
