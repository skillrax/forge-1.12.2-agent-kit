package dev.agentic.installedtest;

/** Project-owned, public no-argument scenario. Plan actions; the harness runs them on server ticks. */
public interface InstalledScenario {
    void plan(TestContext test) throws Exception;
}
