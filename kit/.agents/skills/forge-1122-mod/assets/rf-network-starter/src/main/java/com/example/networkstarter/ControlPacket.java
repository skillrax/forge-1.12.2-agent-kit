package com.example.networkstarter;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.fml.common.network.NetworkRegistry;
import net.minecraftforge.fml.common.network.simpleimpl.*;
import net.minecraftforge.fml.relauncher.Side;
public final class ControlPacket implements IMessage {
    public static final SimpleNetworkWrapper CHANNEL=NetworkRegistry.INSTANCE.newSimpleChannel("networkstarter");
    private int window,action;
    public ControlPacket(){}public ControlPacket(int w,int a){window=w;action=a;}
    public void fromBytes(ByteBuf b){window=b.readInt();action=b.readInt();}public void toBytes(ByteBuf b){b.writeInt(window);b.writeInt(action);}
    public static void register(){CHANNEL.registerMessage(Handler.class,ControlPacket.class,0,Side.SERVER);}
    public static final class Handler implements IMessageHandler<ControlPacket,IMessage> {
        public IMessage onMessage(ControlPacket m,MessageContext ctx){EntityPlayerMP p=ctx.getServerHandler().player;p.getServerWorld().addScheduledTask(()->{
            if(p.openContainer instanceof MachineContainer && p.openContainer.windowId==m.window){MachineContainer c=(MachineContainer)p.openContainer;if(c.canInteractWith(p))c.tile.control(m.action);}
        });return null;}
    }
}
