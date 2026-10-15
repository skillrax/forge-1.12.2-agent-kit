package dev.agentic.itemnetwork;
import net.minecraft.entity.player.EntityPlayer;
public final class ClientProxy extends CommonProxy {
    @Override public Object screen(EntityPlayer player,DuctTile tile) {return new DuctScreen(new DuctContainer(player.inventory,tile));}
}
