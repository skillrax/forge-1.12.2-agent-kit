# Focused inventory/menu starter check

Production is copied from active kit's optional `assets/machine-starter/src/main` into a fresh source-only checkout. This fixture contains only separate `src/chunkEvaluation` assertions; it is not recipient or production content.

The check places the starter one block inside a chunk, with a normal cube between it and the western boundary. It keeps the owner ticking with a Forge ticket, actually unloads the western chunk, mutates inventory for 40 ticks, and checks that the owning chunk's dirty flag is set without loading its neighbor. It then checks inventory NBT, unloads/reloads the owning chunk through the provider, reacquires the tile and checks retained contents/removal. It tests save-only marking for this comparator-free starter, not a comparator-enabled machine, client menus or an actual JVM restart.

Reproduce: create a new checkout with active kit's `tools/New-Checkout.ps1`; copy the starter `src/main` into the checkout `src/main` and this fixture's `src/chunkEvaluation` into its `src`. Copy `qa/fixtures/item-routing/chunk-overlay.gradle`, append `apply from: 'chunk-overlay.gradle'` to the build, run workspace `agent.ps1 -Action check -Profile mod`, then root `qa/integration/Invoke-StarterServer.ps1 -Workspace <workspace>`. The overlay binds localhost, enables the test server EULA and selects a fresh world. No assertion classes are included in the JAR.

Candidate evidence is under the versioned kit evidence directory; obtain the current version from `kit/kit.json`. No broad regression or fault injection is required by this fixture.
