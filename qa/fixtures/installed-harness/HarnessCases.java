package example.installedtests;

import dev.agentic.installedtest.*;

/** Narrow result-integrity cases for the new scheduler, outside recipient content. */
public final class HarnessCases {
    @InstalledTest("condition-timeout")
    public static final class ConditionTimeout implements InstalledScenario {
        @Override public void plan(TestContext t) {
            t.waitUntil("unreachable",3,()->false,()->"condition remains false");
        }
    }
    @InstalledTest("empty-scenario")
    public static final class Empty implements InstalledScenario {
        @Override public void plan(TestContext t) {}
    }
    @InstalledTest("invariant-failure")
    public static final class InvariantFailure implements InstalledScenario {
        @Override public void plan(TestContext t) {
            t.invariant("stable",()->t.ticks()<3,()->"tick="+t.ticks());
            t.waitTicks(4);
        }
    }
}
