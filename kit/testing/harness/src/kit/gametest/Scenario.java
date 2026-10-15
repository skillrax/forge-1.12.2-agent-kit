package kit.gametest;

/**
 * A behavior test that runs inside the development server. {@link #plan} declares steps;
 * the harness then executes them on normal server ticks. Needs a public no-argument constructor.
 */
public interface Scenario {
    void plan(TestContext t) throws Exception;
}
