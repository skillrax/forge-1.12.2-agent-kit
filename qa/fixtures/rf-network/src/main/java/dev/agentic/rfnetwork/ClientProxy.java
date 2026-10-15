package dev.agentic.rfnetwork;
import net.minecraft.entity.player.EntityPlayer;
public final class ClientProxy extends CommonProxy {public Object screen(EntityPlayer p,EnergyTile t){return new MachineScreen(new MachineContainer(p.inventory,t));}}
