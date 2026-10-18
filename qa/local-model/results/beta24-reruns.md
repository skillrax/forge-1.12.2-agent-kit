# beta.24 reruns (S3, S5 from series 2)

Setup: as series 2 from S4 (no control vector, temp 0.35, top_p 0.9, top_k 40, min_p 0.05). Trials in `beta24-S3-S5-rerun/S<n>`. Candidate fixes are applied in one batch after both reruns.

| Trial | Outcome (user, runClient) | Time | Checks | STUCK | Kit files changed | Notes |
|---|---|---|---|---|---|---|
| S3 slingshot | works after one user round trip (beta.23: FAIL) | 56 min + 41 min fix | EbbbggggggggggGGGgggGgGGGG | 4x, first after check 11 | none | first jar fired two pebbles (client ghost); then 33 min looping on a flaky test until the user said the test was at fault; 80 api lookups; OpenCode edit tool "mangled" until the user cleared its cache |
| S5 linked chest | works after two user round trips, no compromises (beta.23: partly, tint never worked) | 19 min + ~20 + 4 min fixes | EbbgggGgGGGG | 1x, after check 6 | none | v1 had no colour look (not asked) and a GUI title with a hand-built vanilla lang key; the fix round made recolouring move contents (overflow deleted), the model's own unasked gameplay choice; tint worked first try from the rendering facts |

## S3 findings (verified against pinned 1.12.2 source)
- beta.24 fixes held: no harness edit this time (target player and recipes worked), the client-only WARN named `Minecraft.getRenderItem()` in preInit with the side crash, STUCK fired on 6 identical `craft` failures.
- Double pebble (user): the shot was spawned on both sides (no `!world.isRemote` guard; the client copy is a ghost). items.md and FORGE-1.12.md state the rule; no check can see it, since game tests run on a server.
- Flaky test (33 min loop, user had to intervene): the test shot a zombie in the always-day area. `EntityZombie.onUpdate` sets it burning; fire damage starts its hurt cooldown; `EntityArrow.onHit` (lines 391-438) makes a projectile whose damage is refused bounce back instead of dying, so "pebble destroyed on impact" failed about half the time. TESTING.md warns about burning zombies; the model used one anyway. STUCK never fired because a PASS between failures resets the streak.
- `t.craft` with 8 cells shifted the grid silently (6 failed checks): the javadoc says short lists fill from the top-left; the TESTING.md row does not. Generated shapeless checks rely on short lists.
- Steps run back to back in one tick until a wait (`TestContext.tick` line 307): an entity spawned in one `then` has had no update when the next `check` runs.
- Model claims checked and declined: "AGENTS.md lacks `api -Full`" (every truncated read prints the exact `-Full` command; 26 uses show it was found); per-assertion tick timeline (bloat for one case).

## S5 findings (verified)
- beta.24 facts used and held: tint (tintindex, client re-render, near-white texture), recipe `data`, Nether loading, WorldSavedData from the overworld.
- `Missing data for item 'minecraft:planks'` from `new recipe -Key #=minecraft:planks`: the second sighting after S6 (wool). The generator accepts a vanilla item with variants and no `@meta`. From Item.registerItems, 1.12.2 items with subtypes: stone dirt planks sapling sand log log2 leaves leaves2 sponge sandstone tallgrass wool yellow_flower red_flower stone_slab stone_slab2 purpur_slab wooden_slab monster_egg stonebrick cobblestone_wall anvil quartz_block stained_hardened_clay carpet double_plant stained_glass stained_glass_pane prismarine red_sandstone concrete concrete_powder coal golden_apple fish cooked_fish dye bed skull banner.
- GUI title showed a raw key: the model built `item.dye.<colour>.name`; vanilla uses `item.dyePowder.<camelCase>.name` (`api -Find dyePowder` answers it in one call). General rule: take a vanilla name from the stack (`stack.getTranslationKey() + ".name"`, or `getDisplayName()` on the client), never assemble the key.
- Recolour moved and deleted items: an unasked gameplay choice. AGENTS.md says "Ask the user only when a choice changes gameplay"; the model decided anyway.
- Export failure text lists three files to edit instead of `agent.ps1 version x.y.z`, and says `check server` although a passing `check game` also counts; `version` accepted a downgrade (1.0.1 -> 1.0.0) silently.
- Docs: TESTING.md line 17 floor "y=3" (absolute) vs table "y -1" (relative) confused this model too (second sighting); `t.player(name)` UUID is stable per name (`nameUUIDFromBytes`), not stated; testing a GUI built by your `IGuiHandler`: call `getServerGuiElement(...)` and assert on the container; the test server never translates, so assert on keys (TESTING.md says it as a footnote).

## Applied in kit 0.5.0-beta.25
All 11. Fix 11 measured first: "spawnEntity with no isRemote in the method" flagged 4 of 18 spawns in the series 2 mods, reruns, fixtures and examples, all false positives (tile helpers, a dispenser behavior); limited to hooks that run on both sides (item use/release/tick, block activation/collision, entity update/impact) it flags 0 and still catches the original S3 bug. FLAKY does not count toward STUCK; it prints its own stop-and-look message.

## Candidate fixes (apply after the S5 rerun)
1. Flaky signal (S3; the S3 beta.23 run looped too): keep per-test step outcomes across checks (not cleared by PASS); when one test has passed and failed at the same step within the last ~6 checks, print `FLAKY` naming world noise as the likely cause (burning mobs, hurt cooldown, random ticks, mob AI) and count it toward STUCK.
2. Area dump: living entities with health, burning and hurt cooldown (`1x zombie (hp 17/20, burning, hurt 6)`), so a refused hit is visible in one read.
3. `t.craft`: reject a short list that contains `""` (a miscounted shaped grid) with "give all 9 cells"; keep short lists without blanks (shapeless). TESTING.md row says both.
4. TESTING.md facts: steps run back to back in one tick until a `waitTicks`/`waitUntil`; a projectile that hits a mob in its hurt cooldown bounces (`EntityArrow`) - measure damage on a fresh, non-burning mob; `spawn` does not call `onInitialSpawn` (no equipment); floor wording in one frame (line 17 says y=3, the table says y -1 below origin); bow-style release in a test: `stack.getItem().onPlayerStoppedUsing(stack, world, p, stack.getMaxItemUseDuration() - chargeTicks)`.
5. AGENTS.md `export` row: once per version (bump with `version`).
6. `new recipe` (and other generators taking item refs): a vanilla item with variants and no `@meta` is an error naming `@0`, `@32767` (any) or an `ore:` name (S5 planks, S6 wool).
7. Lang fact (FORGE-1.12.md or items.md): never assemble a vanilla translation key; take it from the stack.
8. Export message points at `agent.ps1 version x.y.z` and accepts check game; `version` refuses a lower version without `-Force`.
9. AGENTS.md: ask before choosing what happens to the player's items or who may do what when the request does not say (reword the existing "Ask the user only when..." rule, no added length).
10. TESTING.md/gui.md: `t.player` UUID stable per name; IGuiHandler test route; "the test server never translates: assert on keys" as a rule.
11. To evaluate: a WARN for `spawnEntity` in an item or entity method with no `isRemote` test (ghost entities). Heuristic; measure false positives on the series 2 mods before adding.
