package dev.agentic.energy;
import net.minecraft.entity.player.EntityPlayer;
public final class ClientProxy extends CommonProxy {
    @Override public Object screen(EntityPlayer player,ProcessorTile tile) {return new ProcessorScreen(new ProcessorContainer(player.inventory,tile));}
}
