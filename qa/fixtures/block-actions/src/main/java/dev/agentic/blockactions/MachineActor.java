package dev.agentic.blockactions;
import com.mojang.authlib.GameProfile;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import net.minecraft.network.*;
import net.minecraft.util.math.*;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.util.FakePlayer;

/** Short-lived survival actor, identified by dimension + machine position. No real login/owner impersonation. */
final class MachineActor extends FakePlayer {
    MachineActor(WorldServer world,BlockPos machine){
        super(world,new GameProfile(UUID.nameUUIDFromBytes(("blockactions:"+world.provider.getDimension()+":"+machine).getBytes(StandardCharsets.UTF_8)),"[BlockActions]"));
        // Pinned ForgeHooks sends correction packets; FakePlayer leaves connection null.
        connection=new NetHandlerPlayServer(world.getMinecraftServer(),new NetworkManager(EnumPacketDirection.SERVERBOUND),this){
            @Override public void sendPacket(Packet<?> packet){} // No socket or recipient exists.
        };
    }
    @Override public Vec3d getPositionVector(){return new Vec3d(posX,posY,posZ);}
}
