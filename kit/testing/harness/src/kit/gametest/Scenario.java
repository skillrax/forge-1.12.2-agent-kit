package kit.gametest;

/**
 * A behavior test that runs inside the development server. {@link #plan} declares steps;
 * the harness then executes them on normal server ticks. Needs a public no-argument constructor.
 */
public interface Scenario {
    void plan(TestContext t) throws Exception;

    /**
     * Optional second half, run after the server saved the world, stopped and started again on the same
     * world (same area, same {@code t.origin()}). Declare steps here as in plan(); check what must survive
     * a restart: tile contents, block states, saved data. Players from plan() are gone; make new ones.
     */
    default void afterRestart(TestContext t) throws Exception {}
}
