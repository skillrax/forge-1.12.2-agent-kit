package dev.agentic.clockworkbottle;

/** Bounds derived from the bottle's existing storage cap, without unchecked doubling. */
public final class BoostMath {
    public static final int DURATION_TICKS = 15 * 20;
    public static final int GLOBAL_EXTRA_TICK_BUDGET = 8192;
    private BoostMath() {}
    public static int nextMultiplier(int previous) {
        if (previous == 0) return 2;
        if (previous < 2 || previous > 8192 || (previous & (previous - 1)) != 0) return -1;
        return previous * 2;
    }
    public static long costSeconds(int multiplier) {
        return multiplier < 2 ? Long.MAX_VALUE : 15L * multiplier;
    }
    public static boolean fitsBudget(long reserved, long replaced, int multiplier) {
        return multiplier >= 2 && multiplier - 1 <= GLOBAL_EXTRA_TICK_BUDGET
            && reserved >= replaced && replaced >= 0
            && reserved - replaced <= GLOBAL_EXTRA_TICK_BUDGET - (long) multiplier + 1;
    }
}
