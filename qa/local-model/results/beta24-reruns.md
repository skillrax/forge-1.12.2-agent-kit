# beta.24 reruns (S3, S5 from series 2)

Setup: as series 2 from S4 (no control vector, temp 0.35, top_p 0.9, top_k 40, min_p 0.05). Trials in `beta24-S3-S5-rerun/S<n>`. Candidate fixes are applied in one batch after both reruns.

| Trial | Outcome (user, runClient) | Time | Checks | STUCK | Kit files changed | Notes |
|---|---|---|---|---|---|---|
| S3 slingshot | works after one user round trip (beta.23: FAIL) | 56 min + 41 min fix | EbbbggggggggggGGGgggGgGGGG | 4x, first after check 11 | none | first jar fired two pebbles (client ghost); then 33 min looping on a flaky test until the user said the test was at fault; 80 api lookups; OpenCode edit tool "mangled" until the user cleared its cache |

## S3 findings (verified against pinned 1.12.2 source)
- beta.24 fixes held: no harness edit this time (target player and recipes worked), the client-only WARN named `Minecraft.getRenderItem()` in preInit with the side crash, STUCK fired on 6 identical `craft` failures.
- Double pebble (user): the shot was spawned on both sides (no `!world.isRemote` guard; the client copy is a ghost). items.md and FORGE-1.12.md state the rule; no check can see it, since game tests run on a server.
- Flaky test (33 min loop, user had to intervene): the test shot a zombie in the always-day area. `EntityZombie.onUpdate` sets it burning; fire damage starts its hurt cooldown; `EntityArrow.onHit` (lines 391-438) makes a projectile whose damage is refused bounce back instead of dying, so "pebble destroyed on impact" failed about half the time. TESTING.md warns about burning zombies; the model used one anyway. STUCK never fired because a PASS between failures resets the streak.
- `t.craft` with 8 cells shifted the grid silently (6 failed checks): the javadoc says short lists fill from the top-left; the TESTING.md row does not. Generated shapeless checks rely on short lists.
- Steps run back to back in one tick until a wait (`TestContext.tick` line 307): an entity spawned in one `then` has had no update when the next `check` runs.
- Model claims checked and declined: "AGENTS.md lacks `api -Full`" (every truncated read prints the exact `-Full` command; 26 uses show it was found); per-assertion tick timeline (bloat for one case).

## Candidate fixes (apply after the S5 rerun)
1. Flaky signal (S3; the S3 beta.23 run looped too): keep per-test step outcomes across checks (not cleared by PASS); when one test has passed and failed at the same step within the last ~6 checks, print `FLAKY` naming world noise as the likely cause (burning mobs, hurt cooldown, random ticks, mob AI) and count it toward STUCK.
2. Area dump: living entities with health, burning and hurt cooldown (`1x zombie (hp 17/20, burning, hurt 6)`), so a refused hit is visible in one read.
3. `t.craft`: reject a short list that contains `""` (a miscounted shaped grid) with "give all 9 cells"; keep short lists without blanks (shapeless). TESTING.md row says both.
4. TESTING.md facts: steps run back to back in one tick until a `waitTicks`/`waitUntil`; a projectile that hits a mob in its hurt cooldown bounces (`EntityArrow`) - measure damage on a fresh, non-burning mob; `spawn` does not call `onInitialSpawn` (no equipment); floor wording in one frame (line 17 says y=3, the table says y -1 below origin); bow-style release in a test: `stack.getItem().onPlayerStoppedUsing(stack, world, p, stack.getMaxItemUseDuration() - chargeTicks)`.
5. AGENTS.md `export` row: once per version (bump with `version`).
6. To evaluate: a WARN for `spawnEntity` in an item or entity method with no `isRemote` test (ghost entities). Heuristic; measure false positives on the series 2 mods before adding.
