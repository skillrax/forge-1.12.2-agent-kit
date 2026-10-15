package dev.agentic.playermenu;

import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.fml.common.network.simpleimpl.*;

public final class Packets {
    public static class Request implements IMessage {
        public int action;
        public Request() {}
        public Request(int action){this.action=action;}
        public void toBytes(ByteBuf b){b.writeByte(action);}
        public void fromBytes(ByteBuf b){action=b.readUnsignedByte();}
    }
    public static class Snapshot implements IMessage {
        public int counter,status;public boolean enabled;
        public Snapshot() {}
        public Snapshot(EntityPlayerMP p,int status){counter=PersonalState.counter(p);enabled=PersonalState.enabled(p);this.status=status;}
        public void toBytes(ByteBuf b){b.writeInt(counter);b.writeBoolean(enabled);b.writeByte(status);}
        public void fromBytes(ByteBuf b){counter=b.readInt();enabled=b.readBoolean();status=b.readUnsignedByte();}
    }
    public static class RequestHandler implements IMessageHandler<Request,IMessage> {
        public IMessage onMessage(Request message,MessageContext context) {
            final EntityPlayerMP player=context.getServerHandler().player;
            final int action=message.action;
            player.getServerWorld().addScheduledTask(()->{
                // Ignore queued requests belonging to an obsolete/disconnected player instance.
                if(player.getServer().getPlayerList().getPlayerByUUID(player.getUniqueID())!=player)return;
                int status=PersonalState.apply(player,action);
                PlayerMenu.NETWORK.sendTo(new Snapshot(player,status),player);
            });
            return null;
        }
    }
    public static class SnapshotHandler implements IMessageHandler<Snapshot,IMessage> {
        public IMessage onMessage(Snapshot message,MessageContext context){PlayerMenu.proxy.receive(message);return null;}
    }
}
