# p1-local-b - OpenCode CLI, qwen3.8-flash-next IQ3_XXS (Strata, 64k, temp 0.6, ~70 tok/s), kit 0.5.0-beta.11, 2026-10-17
Outcome: partly (mod correct; 13 of 15 test steps pass; stuck on its own hopper test)      Checked by: its check game output; mod code and test read by the maintainer
Time: ~20 min lost to a thinking loop at output limit 8192, then ~40 min more (16384)   Generated: >150k tokens   Interventions: output limit raised; asked to run its instrumented test
Edited pinned build files: no   Wrote game tests: yes, one test with 15 steps   Report honest: yes, but both diagnoses wrong

## Where it got stuck
- Thought for 8k tokens, hit the output limit, answered nothing, 6 times -> OpenCode output limit below the thinking length (category: setup; trial README fixed)
- Hopper under a second jar pulled one item then stopped (`enabled=false`). Model blamed the harness ("tiles may not tick with no players"), then the hopper's default state. Real cause: an earlier step's comparator, facing west at x+3, outputs east into the second jar at x+4; the powered jar powers the hopper below it. Each guess cost a 25-30 s run (category: docs not found: test-world facts; model reasoning)
- Live progress showed `Caused by: java.lang.IllegalArgumentException` from FML skipping slime-launcher-0.2.3.jar (harmless dev-classpath noise) (category: generator/tool output misleading)
- One invented name: TileEntityHopper.getTotalInventory (cost one compile).
- Correct and tested: deposits, LIFO, sneak semantics, comparator scaling, NBT round trip, insert rejection.

## General fix (would it help a different mod?)
- kit 0.5.0-beta.12: TESTING.md: one behavior per @GameTest (own cleared area); steps in one test share the area and redstone carries; the test area ticks (tiles, hoppers, redstone); hopper `enabled=false` = powered; failure details print the involved block states. Fixture test `hopper-under-tile` proves the hopper facts.
- Progress output skips exception lines (final report already filtered them).
- Systemic, not yet done: harness-side observability and a stuck circuit breaker (see TASK.md).
