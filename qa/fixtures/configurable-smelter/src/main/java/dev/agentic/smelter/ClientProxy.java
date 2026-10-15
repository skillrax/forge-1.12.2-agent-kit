package dev.agentic.smelter;
import net.minecraft.entity.player.EntityPlayer;
public final class ClientProxy extends CommonProxy {
    @Override public Object screen(EntityPlayer player,SmelterTile tile) {return new SmelterScreen(new SmelterContainer(player.inventory,tile));}
}
