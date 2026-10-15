package dev.agentic.clockworkbottle;
/** Game-time accounting, independent of Minecraft initialization. */
public final class ChargeMath {
    public static final int CAP_SECONDS=60*60*60;
    public static final int CAP_TICKS=CAP_SECONDS*20;
    private ChargeMath() {}
    public static int boundedTicks(long ticks) {return (int)Math.max(0L,Math.min((long)CAP_TICKS,ticks));}
    public static int advance(long ticks) {int safe=boundedTicks(ticks);return safe<CAP_TICKS?safe+1:safe;}
    public static int next(long seconds) {
        long safe=Math.max(15L,seconds);
        if(safe>CAP_SECONDS/2)return CAP_SECONDS+1; // An unaffordable sentinel; never wrap.
        return (int)(safe*2L);
    }
    public static int spend(long ticks,long seconds) {
        int safe=boundedTicks(ticks);
        if(seconds<15 || seconds>CAP_SECONDS || seconds*20L>safe)return -1;
        return safe-(int)(seconds*20L);
    }
}
