package dev.agentic.clockworkbottle;

import io.netty.buffer.ByteBuf;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;

/** Complete dimension snapshot; empty snapshots remove the last label. No C2S messages. */
public final class BoostSnapshot implements IMessage {
    public int dimension;
    public long[] positions = new long[0];
    public int[] multipliers = new int[0], ticks = new int[0];
    public BoostSnapshot() {}
    public BoostSnapshot(int dimension, long[] positions, int[] multipliers, int[] ticks) {
        this.dimension = dimension; this.positions = positions;
        this.multipliers = multipliers; this.ticks = ticks;
        validate();
    }
    private void validate() {
        if (positions.length > BoostMath.GLOBAL_EXTRA_TICK_BUDGET
                || multipliers.length != positions.length || ticks.length != positions.length)
            throw new IllegalArgumentException("Invalid boost snapshot length");
        for (int i = 0; i < positions.length; i++) {
            int multiplier = multipliers[i];
            if (multiplier < 2 || multiplier > 8192 || (multiplier & (multiplier - 1)) != 0
                    || ticks[i] < 1 || ticks[i] > BoostMath.DURATION_TICKS)
                throw new IllegalArgumentException("Invalid boost snapshot state");
        }
    }
    @Override public void toBytes(ByteBuf buf) {
        validate(); buf.writeInt(dimension); buf.writeInt(positions.length);
        for (int i = 0; i < positions.length; i++) {
            buf.writeLong(positions[i]); buf.writeInt(multipliers[i]); buf.writeInt(ticks[i]);
        }
    }
    @Override public void fromBytes(ByteBuf buf) {
        if (buf.readableBytes() < 8) throw new IllegalArgumentException("Truncated boost snapshot");
        dimension = buf.readInt(); int count = buf.readInt();
        if (count < 0 || count > BoostMath.GLOBAL_EXTRA_TICK_BUDGET || buf.readableBytes() != count * 16)
            throw new IllegalArgumentException("Invalid boost snapshot payload");
        positions = new long[count]; multipliers = new int[count]; ticks = new int[count];
        for (int i = 0; i < count; i++) {
            positions[i] = buf.readLong(); multipliers[i] = buf.readInt(); ticks[i] = buf.readInt();
        }
        validate();
    }
    public static int seconds(int remainingTicks) { return (remainingTicks + 19) / 20; }
}
