# Series 3 acceptance tests: first outside score, 2026-10-19

Hidden acceptance tests (`qa/acceptance/series3`, 54 `@GameTest`s for S0-S7, written from the prompts only, string ids, no mod classes) run against the series 3 trials that already exist, then against deliberately broken copies of the Claude trials to show each test can fail. No new agent runs: the fresh Claude and local baselines with the id-naming prompts are still to do (see "Next").

## Headline

| Trial | Own `check game` | Acceptance |
|---|---|---|
| Claude (sonnet, kit beta.26), S0-S7 | 8/8 PASS | **54/54** (every prompt full marks) |
| Local r1 (Qwen3.8 IQ3_XXS, kit beta.27) | 6/8 PASS | **34/54** |
| Local r2 | 4/8 PASS | **32/54** |

| Prompt | Claude | Local r1 | Local r2 | Local own check (r1/r2) |
|---|---|---|---|---|
| S0 whereami | 6/6 | 1/6 | 1/6 | PASS / PASS |
| S1 glowberries | 9/9 | 9/9 | 9/9 | PASS / PASS |
| S2 pulse counter | 6/6 | 6/6 | 1/6 | PASS / PASS |
| S3 enchantments | 7/7 | 0/7 | 6/7 | FAIL (no tests) / FAIL (tests do not compile) |
| S4 syrup + tank | 8/8 | 7/8 | 0/8 | PASS / FAIL |
| S5 fetching wolves | 7/7 | 3/7 | 7/7 | FAIL / FAIL |
| S6 tool belt | 6/6 | 3/6 | 6/6 | PASS / PASS |
| S7 blast kiln | 5/5 | 5/5 | 2/5 | PASS / FAIL |

What the outside score shows that "PASS" did not:
- **S0 local r1 and r2 (own PASS, 1/6):** both return permission level 0 but keep CommandBase's `checkPermission`, and `EntityPlayerMP.canUseCommand` (line 1404) is false for every level for a non-op on a server. Non-ops get "You do not have permission" from both. Their own tests ran as a player the kit treats as allowed.
- **S2 local r2 (own PASS, 1/6):** the block has no facing and no redstone code at all (266 lines in total); only the recipe test passes.
- **S6 local r1 (own PASS, 3/6):** a per-UUID "one press per 2 ticks" throttle compares `ticksExisted`, which restarts at 0 after relog, respawn or leaving the End, so the first press after each is swallowed (ToolBelt.java line 73).
- **S4 local r1 (own PASS, 7/8):** the tank drops empty when broken.
- **S3 local r2 (own FAIL, 6/7):** the mod itself is nearly right; only its own tests did not compile. Soulbound cannot go on swords, hoes or shears: it checks `instanceof ItemTool`, which in 1.12.2 those classes are not.
- **S7 local r2 (own FAIL, 2/5):** fuel must be ore dictionary `"coal"`, but Forge 1.12.2 never registers vanilla coal there (OreDictionary.initVanillaEntries registers `oreCoal` and `blockCoal` only), so the kiln never burns.
- Empty mods still collect the "nothing should happen" tests: local S5-r1 has no wolf code and scores 3/7 (sitting, strangers, far items). Read low scores with that in mind; 0-3 of 7 there means "does nothing".

## How it was run

`powershell -NoProfile -File qa/acceptance/Invoke-Acceptance.ps1 -Trial <copy> -Id <S#> [-Rename @{...}]`, the same script `Run-Series.ps1` now calls after the feedback turn. It sets the agent's `src/gametest/java` aside (restored byte-identical; checked with file hashes), copies the tests in, runs `check game -Test <the acceptance ids>` (the filter takes exact ids or `*`, so the script lists them), removes them and restores the trial's check history. All runs used copies in `..\kit-trials\acceptance-scratch\` (1.5 GB, safe to delete); the original trial folders were not touched.

Two changes to the copies, not to the mods:
- Kits before beta.27 started the test server with `spawn-animals=false`; WorldServer.updateEntityWithOptionalForce (line 861) then kills every `EntityAnimal` on its first tick, so no pig, cow or wolf test could run. The copies' `testing/Test-DevEnvironment.ps1` got the beta.27 value (`spawn-animals=true`).
- The trials ran before the prompts named their ids, so the tests' ids were mapped per trial:

| Trial | Mapping (prompt name -> trial name) |
|---|---|
| Claude S1 | `glowberries:glowberry_seeds` -> `glowberry:glowberry_seeds`, `glowberries:glowberry` -> `glowberry:glowberries`, `glowberries:glowberry_bush` -> `glowberry:glowberry_bush` |
| Local S2 r1, r2 | `pulsecounter:pulse_counter` -> `pulse_counter:pulse_counter` |
| Local S3 r1, r2 | `soulgather:` -> `enchantextras:` (r1, registers no enchantments at all), `soulbound:` (r2) |
| Claude S4 / local S4 r1 / r2 | mod `maplesyrup` -> `syrup` / `maple` / `mapletank` (fluid `maple_syrup` and block `tank` as named) |
| everything else | none (S0 is the command name, S5 and S6 register nothing, S7 and Claude S3 already match; local S1 matches exactly) |

Run time per trial: 26-60 s, S5 90-300 s and S7 80-210 s (wolves and smelting run in real ticks).

## Each test fails on a wrong mod

The mutants are copies of the Claude trials (all 54 pass) with targeted bugs. Each test failed on at least one mutant or on a real trial, and for its own reason (the failing step's message names the bug).

| Mutant | Bugs put in | Tests that then fail |
|---|---|---|
| m-S0 | `(int)` coordinates, no biome or light, raw dimension ids, share only to the sender, extra args ignored, console cast crashes | info, dimensions, share, wrong-usage, console (not-just-ops at the info step) |
| m-S0p | `checkPermission` override removed (level 0 kept) | not-just-ops (plus every test that runs the command as a non-op) |
| m-S1a | no grass seeds, 2 stages, bonemeal +0, grows in the dark, no light, 4 berries, no night vision, survives on dirt | all except plant |
| m-S1b | seeds and crop accept dirt (EnumPlantType.Plains) | plant-on-farmland, trampled |
| m-S2a | counts both edges | counts, wraps, chat-and-reset, comparator, survives-reload |
| m-S2b | output on all sides, stops at 15, no reset, count not saved, no recipe | counts (only-front), wraps (16th), chat-and-reset (reset), survives-reload, recipe |
| m-S2c | no comparator override | comparator |
| m-S3a | leftovers deleted, kills not gathered, soulbound stash never restored | gathering-blocks (full inventory), gathering-kills, soulbound-death, soulbound-keepinventory |
| m-S3b | Gathering not on swords, Soulbound uncommon and not on armor, both treasure | which-items, table-and-books, anvil |
| m-S4a | no recipe, water's tick rate and spread, 8-bucket tank, drops empty, fluid capability only on top | bucket-recipe, flows-slower-shorter, tank-buckets, tank-keeps-liquid, tank-pipes-comparator |
| m-S4b | no bucket, syrup speeds entities up (non-liquid material), no empty-hand message, water only | bucket-recipe, pour-out, slows-mobs, tank-buckets (message), tank-any-liquid |
| m-S5a | stick also sits the wolf, anyone's stick toggles it | stick-toggles-mode, strangers, fetches (sits instead), death-drop and reload (never picks up) |
| m-S5b | range 40, carried item lost on load, nothing dropped on death | ignores-far-items, drops-on-death, keeps-on-reload |
| m-S5c | m-S5b plus the fetch AI runs beside EntityAISit (mutex 0, no sit check) | no-fetch-while-sitting (and m-S5b's three) |
| m-S6a | rotation reversed, an ItemStack field on the key message | key-message-carries-no-items (the reversal is invisible, see limits) |
| m-S6b | belt stored outside the persisted tag, not dropped on death | dimension (End copy), death-keepinventory, death-drops-belt |
| m-S6c | belt only in memory, wiped on dimension change | survives-relog, dimension (Nether), death-keepinventory |
| m-S6d | hand swaps with slot 1 only | cycles-four-ways (and every test that loads the belt) |
| m-S7a | messages without coordinates, centre not checked, multiplier 1 | says-what-is-wrong, smelts-two-per-ore, broken-stops-rebuilt-resumes |
| m-S7b | centre always assumed south of the controller | any-facing, broken (east wall), controller-drops (south wall) |
| m-S7c | taking the kiln apart wipes its inventory and fuel | broken-stops-rebuilt-resumes (resume step), controller-drops-contents |

Per-test results (pass / **FAIL**; c = Claude, l = local, m = mutant):

| S0 | c | l-r1 | l-r2 | m-S0 | m-S0p |
|---|---|---|---|---|---|
| info | pass | **FAIL** | **FAIL** | **FAIL** | **FAIL** |
| dimensions | pass | **FAIL** | **FAIL** | **FAIL** | **FAIL** |
| not-just-ops | pass | **FAIL** | **FAIL** | **FAIL** | **FAIL** |
| share | pass | **FAIL** | **FAIL** | **FAIL** | **FAIL** |
| wrong-usage | pass | **FAIL** | **FAIL** | **FAIL** | **FAIL** |
| console | pass | pass | pass | **FAIL** | pass |

| S1 | c | l-r1 | l-r2 | m-S1a | m-S1b |
|---|---|---|---|---|---|
| grass-seeds | pass | pass | pass | **FAIL** | pass |
| plant-on-farmland | pass | pass | pass | pass | **FAIL** |
| four-stages | pass | pass | pass | **FAIL** | pass |
| bonemeal | pass | pass | pass | **FAIL** | pass |
| needs-light | pass | pass | pass | **FAIL** | pass |
| glows-when-grown | pass | pass | pass | **FAIL** | pass |
| drops | pass | pass | pass | **FAIL** | pass |
| eat-night-vision | pass | pass | pass | **FAIL** | pass |
| trampled | pass | pass | pass | **FAIL** | **FAIL** |

| S2 | c | l-r1 | l-r2 | m-S2a | m-S2b | m-S2c |
|---|---|---|---|---|---|---|
| counts-rising-edges | pass | pass | **FAIL** | **FAIL** | **FAIL** | pass |
| wraps-at-16 | pass | pass | **FAIL** | **FAIL** | **FAIL** | pass |
| chat-and-reset | pass | pass | **FAIL** | **FAIL** | **FAIL** | pass |
| comparator | pass | pass | **FAIL** | **FAIL** | pass | **FAIL** |
| survives-reload | pass | pass | **FAIL** | **FAIL** | **FAIL** | pass |
| recipe | pass | pass | pass | pass | **FAIL** | pass |

| S3 | c | l-r1 | l-r2 | m-S3a | m-S3b |
|---|---|---|---|---|---|
| which-items | pass | **FAIL** | **FAIL** | pass | **FAIL** |
| table-and-books | pass | **FAIL** | pass | pass | **FAIL** |
| anvil | pass | **FAIL** | pass | pass | **FAIL** |
| gathering-blocks | pass | **FAIL** | pass | **FAIL** | pass |
| gathering-kills | pass | **FAIL** | pass | **FAIL** | pass |
| soulbound-death | pass | **FAIL** | pass | **FAIL** | pass |
| soulbound-keepinventory | pass | **FAIL** | pass | **FAIL** | pass |

| S4 | c | l-r1 | l-r2 | m-S4a | m-S4b |
|---|---|---|---|---|---|
| bucket-recipe | pass | pass | **FAIL** | **FAIL** | **FAIL** |
| pour-out | pass | pass | **FAIL** | pass | **FAIL** |
| flows-slower-shorter | pass | pass | **FAIL** | **FAIL** | pass |
| slows-mobs | pass | pass | **FAIL** | pass | **FAIL** |
| tank-buckets | pass | pass | **FAIL** | **FAIL** | **FAIL** |
| tank-any-liquid | pass | pass | **FAIL** | pass | **FAIL** |
| tank-keeps-liquid | pass | **FAIL** | **FAIL** | **FAIL** | pass |
| tank-pipes-comparator | pass | pass | **FAIL** | **FAIL** | pass |

| S5 | c | l-r1 | l-r2 | m-S5a | m-S5b | m-S5c |
|---|---|---|---|---|---|---|
| stick-toggles-mode | pass | **FAIL** | pass | **FAIL** | pass | pass |
| fetches-items | pass | **FAIL** | pass | **FAIL** | pass | pass |
| no-fetch-while-sitting | pass | pass | pass | pass | pass | **FAIL** |
| strangers-and-wild-wolves | pass | pass | pass | **FAIL** | pass | pass |
| ignores-far-items | pass | pass | pass | pass | **FAIL** | **FAIL** |
| drops-carried-item-on-death | pass | **FAIL** | pass | **FAIL** | **FAIL** | **FAIL** |
| keeps-carried-item-on-reload | pass | **FAIL** | pass | **FAIL** | **FAIL** | **FAIL** |

| S6 | c | l-r1 | l-r2 | m-S6a | m-S6b | m-S6c | m-S6d |
|---|---|---|---|---|---|---|---|
| cycles-four-ways | pass | pass | pass | pass | pass | pass | **FAIL** |
| key-message-carries-no-items | pass | pass | pass | **FAIL** | pass | pass | pass |
| survives-relog | pass | **FAIL** | pass | pass | pass | **FAIL** | **FAIL** |
| survives-dimension-change | pass | **FAIL** | pass | pass | **FAIL** | **FAIL** | **FAIL** |
| death-keepinventory | pass | **FAIL** | pass | pass | **FAIL** | **FAIL** | **FAIL** |
| death-drops-belt | pass | pass | pass | pass | **FAIL** | pass | **FAIL** |

| S7 | c | l-r1 | l-r2 | m-S7a | m-S7b | m-S7c |
|---|---|---|---|---|---|---|
| says-what-is-wrong | pass | pass | pass | **FAIL** | **FAIL** | pass |
| smelts-two-ingots-per-ore | pass | pass | **FAIL** | **FAIL** | pass | pass |
| any-facing | pass | pass | pass | pass | **FAIL** | pass |
| broken-stops-rebuilt-resumes | pass | pass | **FAIL** | **FAIL** | **FAIL** | **FAIL** |
| controller-drops-contents | pass | pass | **FAIL** | pass | **FAIL** | **FAIL** |

## Test mistakes found by this check (fixed before the numbers above)

A test that fails a correct mod is as bad as one that passes a broken one; these came up on the trials and were fixed in the tests:
- S1: grass seeds via a drop event instead of `addGrassSeed` is fine (now: break tall grass 800 times); vanilla crops pop off in the dark rather than sit still (popping counts as "not growing"); bonemeal skips stages, so stages are counted with random ticks.
- S5: wolves of earlier tests followed the next test's owner because owners shared a name (same UUID) and teleported over; items left by a neighbouring test were picked up; sitting is read from EntityAISit's flag (isSitting() lags); a square 12-block search box is a fair reading of "about 12", so the far item sits 15 blocks away on a straight line.
- S6: a mod may ignore repeated presses within a tick (sound against key spam), so presses are 5 ticks apart and the player's `ticksExisted` advances as a real player's would.
- S4: check names must start with a letter (`17th-...` broke the whole test).

## Limits (what these tests cannot judge)

- Client side: models, textures, HUD (S6 icons), key binding and Controls section, config GUIs (S3, S7), the S7 glow. PLAYTEST.md covers these by hand.
- S6 rotation direction: loading the belt by pressing the key gives the same hand sequence for either direction; only the HUD shows the slot order. A wrong cycle length is caught (m-S6d).
- Save and restart are emulated (tile NBT plus metadata round trip for S2, entity NBT for S5, player NBT for S6), not a real restart; Phase 2 adds that to the harness.
- Players are FakePlayers (non-op; never tick). A mod that deliberately ignores FakePlayers (as some Gathering implementations would, to skip quarries) scores 0 on S3 gathering; none of the series 3 trials did.
- Text is checked for numbers and words, translated or as key plus arguments, so a mod whose messages carry no numbers fails S0 and S2's chat checks even if a human would understand them.

## Next

- User: rerun series 3 once per agent with the updated PROMPTS-3.md (ids named), e.g. `Run-Series.ps1 -Prompts qa/local-model/PROMPTS-3.md -Agent claude -Model sonnet` and the local default; summary.md now has an Acceptance column and each trial an `.acceptance.json`. Those are the baseline numbers for kit 0.5.0 onwards.
- Look at the passing trials with PLAYTEST.md (5 minutes each).
