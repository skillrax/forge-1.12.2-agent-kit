package dev.agentic.workshop;
import net.minecraft.entity.player.EntityPlayer;
public final class ClientProxy extends CommonProxy {
    @Override public void init() {
        net.minecraftforge.fml.client.registry.RenderingRegistry.registerEntityRenderingHandler(TraderNpc.class,TraderRenderer::new);
        net.minecraftforge.fml.client.registry.RenderingRegistry.registerEntityRenderingHandler(TestProjectile.class,
            manager->new net.minecraft.client.renderer.entity.RenderSnowball<TestProjectile>(manager,net.minecraft.init.Items.SNOWBALL,
                net.minecraft.client.Minecraft.getMinecraft().getRenderItem()));
    }
    @Override public Object pouchScreen(EntityPlayer player,int slot) {return new PouchScreen(new PouchContainer(player,slot));}
    @Override public Object screen(EntityPlayer player,InventoryTile tile) {
        return new WorkshopScreen(new WorkshopContainer(player.inventory,tile));
    }
}
