package com.example.configmachine;
import net.minecraft.entity.player.EntityPlayer;
public final class ClientProxy extends CommonProxy {
    @Override public Object screen(EntityPlayer player,MachineTile tile) {return new MachineScreen(new MachineContainer(player.inventory,tile));}
}
