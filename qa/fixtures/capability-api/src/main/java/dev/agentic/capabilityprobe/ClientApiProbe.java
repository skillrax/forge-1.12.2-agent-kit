package dev.agentic.capabilityprobe;

/** Client API compilation only; never referenced by common probe or distributed. */
public final class ClientApiProbe {
    private ClientApiProbe() {}
    public static Class<?>[] surfaces() {
        return new Class<?>[] {
            net.minecraft.client.gui.inventory.GuiContainer.class,
            net.minecraft.client.gui.GuiScreen.class,
            net.minecraft.client.gui.GuiButton.class,
            net.minecraft.client.renderer.entity.RenderLiving.class,
            net.minecraft.client.model.ModelBiped.class,
            net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer.class,
            net.minecraft.client.particle.Particle.class,
            net.minecraftforge.client.event.RenderGameOverlayEvent.class,
            net.minecraftforge.client.event.RenderWorldLastEvent.class,
            net.minecraftforge.client.event.ModelRegistryEvent.class,
            net.minecraftforge.client.model.ModelLoader.class,
            net.minecraftforge.fml.client.registry.RenderingRegistry.class,
            net.minecraft.client.settings.KeyBinding.class,
            net.minecraftforge.fml.client.registry.ClientRegistry.class
        };
    }
    public static abstract class Menu extends net.minecraft.client.gui.inventory.GuiContainer {
        protected Menu(net.minecraft.inventory.Container container) { super(container); }
    }
}
