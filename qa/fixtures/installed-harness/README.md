# Focused generic installed-harness proof

These are maintainer scenarios, not recipient defaults or production mod features. EntityGrowth exercises a normally ticking vanilla entity, delayed/polled checks and an invariant. HarnessCases checks timeout, invariant failure and empty-plan rejection. The independent optional hopper example exercises the same API with an actual third-party inventory.

For a fresh checkout from kit/tools/New-Checkout.ps1:

1. Run its tools/New-InstalledScenario.ps1 -Name entity-growth, then replace the returned source with EntityGrowth.java. Copy HarnessCases.java to the same project package directory.
2. Copy kit/testing/installed-examples/HopperTransferScenario.java into that directory when reproducing the integration case. Never edit testing/installed-driver for a project scenario.
3. Build the separate driver; use its exact returned manifest. Supply a pinned installed-runtime lock and the existing machine-starter production export explicitly to Test-InstalledHarness.ps1. This runs five focused lifecycle/preflight cases.
4. Test-InstalledDiscovery.ps1 checks duplicate IDs, removal of a scenario's class/catalog entry, restoration and unchanged core hashes. It saves/removes/restores only its named project fixture files. Keep a disposable checkout for this check.
5. Test-InstalledWorkflow.ps1 -SuccessOnly additionally checks the actual Iron Chests release through a naturally ticking hopper. Supply its exact JAR/hash; dependencies are not redistributed.

Normal build/JAR inspection with project tests present verifies they do not enter production. These checks are for changed harness behavior, not a default campaign for every mod. Aggregate outcome/artifact pointers: evidence/installed-harness/summary.json.
