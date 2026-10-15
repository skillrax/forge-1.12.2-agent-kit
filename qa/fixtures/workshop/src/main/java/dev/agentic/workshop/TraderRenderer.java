package dev.agentic.workshop;
import net.minecraft.client.model.ModelVillager;
import net.minecraft.client.renderer.entity.RenderLiving;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.util.ResourceLocation;
public final class TraderRenderer extends RenderLiving<TraderNpc> {
    private static final ResourceLocation TEXTURE=new ResourceLocation("minecraft","textures/entity/villager/villager.png");
    public TraderRenderer(RenderManager manager) {super(manager,new ModelVillager(0F),.5F);}
    @Override protected ResourceLocation getEntityTexture(TraderNpc npc) {return TEXTURE;}
}
