package dev.agentic.workshop;
import net.minecraft.advancements.Advancement;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.ResourceLocation;
public final class TrialAdvancement {
    public static final ResourceLocation ID=new ResourceLocation(Workshop.ID,"test_harvest");
    private TrialAdvancement() {}
    public static void reset(EntityPlayerMP player) {
        Advancement advancement=player.getServerWorld().getMinecraftServer().getAdvancementManager().getAdvancement(ID);
        if(advancement!=null)for(String criterion:player.getAdvancements().getProgress(advancement).getCompletedCriteria())
            player.getAdvancements().revokeCriterion(advancement,criterion);
    }
}
