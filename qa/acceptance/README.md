# Hidden acceptance tests

An outside score for trial runs: `@GameTest` scenarios written once per prompt by the maintainer, from the prompt text only, that the agent never sees. `qa/local-model/Run-Series.ps1` runs them after the feedback turn (via `Invoke-Acceptance.ps1`) and puts "Acceptance n/m" in summary.md; each trial's per-test results are in `<id>-r<n>.acceptance.json`.

- `common/Accept.java`: shared helpers (message capture with translation arguments, a player that also receives chat broadcasts, death/respawn as the server does it). Copied into every run.
- `<series>/<id>/*.java`: one file per prompt, `package acceptance`, ids `acceptance-<id>-<behavior>`, one public static nested class per behavior.
- Run by hand on a finished trial: `powershell -NoProfile -File qa/acceptance/Invoke-Acceptance.ps1 -Trial <folder> -Id S1`. It copies the tests in, runs `agent.ps1 check game -Test <ids>`, deletes them and their classes, and restores the trial's check history. `-Rename @{old=new}` maps registry ids for trials run before the prompts named them.

Rules for writing them:
- Only registry/string ids (`t.item`, `t.block`, `t.stack`, `t.spawn`, `FluidRegistry.getFluid`, `Enchantment.getEnchantmentByLocation`) and vanilla/Forge code paths. Never the mod's classes, NBT layout or message keys: any correct implementation of the request must pass.
- Drive behavior the way a player does: `useOnBlock`, `breakBlock`, `interactOn`, item placement by a player facing the right way, hoppers for automation, `executeCommand` for commands. Assert on what the player would see (items, blocks, power levels, chat text with numbers), not on internal state.
- Accept every reading the prompt allows (floor or round, translated text or key plus arguments, `addGrassSeed` or a drop event) and say in the detail what was expected and what happened.
- Check each test against a deliberately wrong mod before trusting it (results/series3-acceptance.md shows how).

Pitfalls found while writing series 3 (true of 1.12.2 and this kit, worth knowing for the next series):
- FakePlayer's `getPositionVector()` is always 0,0,0 and its `canUseCommand` is always false; `Accept.Listener` fixes the first, the second makes it a non-op player (what S0 needs).
- `PlayerList.sendMessage` broadcasts go out as packets; `TestContext.messages` misses them, `Accept.Listener` records them when added to the player list.
- Vanilla crops pop off in the dark (`canBlockStay` needs light 8 or sky) and bonemeal adds 2-5 ages at once: count stages with random ticks, not bonemeal.
- Kits before 0.5.0-beta.27 ran the test server with `spawn-animals=false`, which kills every `EntityAnimal` (pigs, cows, wolves) on its first tick (WorldServer.updateEntityWithOptionalForce line 861).
- The kit's client-only lint ignores one-line method declarations, so a helper named like a client-only vanilla member (`all()`) is reported as an ERROR; declare such methods on several lines.
- Check names (`check`, `waitUntil`) must start with a letter.
