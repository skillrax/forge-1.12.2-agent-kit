# Setup A/B on S4 (cauldron soaking), kit 0.5.0-beta.25, 2026-10-18

Trials in `beta25-S4-optimization-reruns/` (folders renamed after the runs; Read-OpenCode matches them by time). Strata with `--vram-reserve-mib 1536` throughout. "after" = output tokens after the first `check game`.

| Run | Settings | Outcome (user) | Min | Output | Thinking | Before 1st game | After | Game checks |
|---|---|---|---|---|---|---|---|---|
| 1 | baseline (medium, temp 0.35) | works | 11 | 36k | 67% | 22k | 14k | 4 |
| 2 | baseline | works | 23 | 82k | 81% | 21k | 61k | 3 |
| 3 | effort low | partly: cobblestone soaks into a sandstone slab | 6 | 21k | 64% | 19k | 3k | 3 |
| 4 | low, temp 0.6 / top_k 20 / top_p 0.95 | works | 30 | 113k | 82% | 110k | 3k | 2 |
| 5 | as 4 + prune, keep 12000 | works | 32 | 100k | 84% | 81k | 19k | 3 |
| 6 | effort high, temp 0.6, prune, keep 12000 | works | 13 | 46k | 74% | 44k | 2k | 1 |
| 7 | as 6 | works (asked two gameplay questions first) | 30 | 86k | 81% | 73k | 13k | 3 |

## What it shows
- Run-to-run noise is about 2x: identical settings gave 11 vs 23 min (runs 1, 2) and 13 vs 30 min (runs 6, 7). No setting can be judged from one run; a setting effect smaller than 2x needs 3+ runs per arm.
- Time follows output tokens (60-69 tok/s while generating in every run). Thinking stays 64-84% whatever the effort setting.
- Effort `low` gave the fastest run and the only wrong result: the default config mapped cobblestone to `minecraft:stone_slab:1` (sandstone slab), and its test checked the same wrong mapping (expectations taken from the code, not the request).
- Where the extra tokens went:
  - Runs 4, 5, 7 spent 10-20 min before the first game test re-deriving the right-click pipeline (`Minecraft.rightClickMouse`, `PlayerControllerMP.processRightClickBlock`, `PlayerInteractionManager.processRightClickBlock`, `NetHandlerPlayServer.processTryUseItemOn`, `ItemBlock.onItemUse`, sneaking): which side fires `RightClickBlock`, per hand or once, what cancelling does. The fast runs did not ask.
  - Run 2: 74% of its tokens after the first game test, on test failures (held-stack expectations after a click).
  - Run 7: four tests failed once with "Declare steps in plan(), not while steps run" (a helper adding steps from inside a step).
  - Run 5 (user log): Forge config format.

## Kit candidates (verified against pinned 1.12.2 source)
1. Right-click on a block, the fact the slow runs rebuilt (PlayerControllerMP 429-470, Minecraft.rightClickMouse 1667-1712, PlayerInteractEvent line 55): `RightClickBlock` fires on the client first, then on the server (a cancelled client event still sends the packet); it fires per hand, main hand first; a cancelled event returns its cancellation result, which defaults to PASS, so the client goes on to the off hand and then to the plain item use (`RightClickItem`); `setCancellationResult(EnumActionResult.SUCCESS)` stops that and swings the arm. Cancel on both sides, change the world only on the server. `t.useOnBlock` runs only the server half for the hand given. The events.md example cancels without setting the result: fix it (and its fixture).
2. "Declare steps in plan()" message: say what to do (helpers that add steps are called from `plan()`, never inside a `then`/`check`/`waitUntil` lambda).
3. Tests check the request, not the code: write expected values from TASK.md (vanilla ids looked up with `api -Find`), not copied from the implementation. One sighting (run 3); a sentence in TESTING.md at most.

## Applied in kit 0.5.0-beta.26
All three. The events.md example now sets `setCancellationResult(SUCCESS)`; game test `right-click-cancel-result` fails with "returned PASS" on the old example. The step-in-step error names the fix. TESTING.md: expected values from the request, vanilla ids looked up.

## Setup decision
Stop single-run setup A/B: the noise is larger than any effect seen. Keep the run 6/7 setup (effort high, Qwen thinking samplers, prune, keep 12000) as the standard; it is within the noise of the baseline and follows the model's guidance. Spend trial time on kit dead ends like candidate 1, which cause the 2x swings.
