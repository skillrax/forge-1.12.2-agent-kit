# Series 3 local pass: OpenCode + Qwen3.8-Flash-Next IQ3_XXS, kit 0.5.0-beta.27, 2026-10-19

Run unattended with `Run-Series.ps1 -Prompts qa/local-model/PROMPTS-3.md -Runs 2` at 131k context (trials in `kit-trials/series3-opencode-0.5.0-beta.27`). Outcome = the runner's own `check game`; nobody has looked in the client.

| Trial | r1 | r2 | Notes |
|---|---|---|---|
| S0 whereami command | PASS 9 min | PASS 8 min | broadcast not capturable in tests |
| S1 glowberry crop | PASS 34 | PASS 35 | 4-stage crop, light and trampling rules from source |
| S2 pulse counter | PASS 69 | PASS 23 (r2: only the generated test) | diode side conventions re-derived (11 lookups on the comparator alone) |
| S3 soulbound/gathering | FAIL 55: compaction failed, no tests | FAIL 90: time budget, compile error | FakePlayer death, enchantment table rules |
| S4 maple syrup fluid + tank | PASS 61 | FAIL 21: compaction failed, scaffolds left | fluid rendering line too vague |
| S5 fetching wolves | FAIL 22: compaction failed, no tests | FAIL 61: 1 of 8 tests (fetch AI) | vanilla mob AI |
| S6 tool belt + hotkey | PASS 48 | PASS 32 | FakePlayer death; clock |
| S7 blast kiln | PASS 66 | FAIL 34: image read killed the session | recipe and blockstate details |

10 of 16 passed (Claude control: 8 of 8). Of the 6 failures, 5 were the setup, not the model or the kit:
- 4 trials (S2-r2 partly, S3-r1, S4-r2, S5-r1): "Compaction summary reached the output token limit" (or "produced no summary"). OpenCode summarizes at context minus `buffer` (10000); one long turn (13-16k tokens) took the session to ~112k, and the summary request then had ~128k of input, leaving ~3k of the 131k context for a thinking model's summary. The session cannot recover: the nudge and the feedback prompt failed the same way. Fix (runner and global config): `buffer` 32768 at 131k (20000 at 64k); the runner starts one fresh session in the same folder after a failed compaction; Read-OpenCode counts failed compactions.
- S7-r2: the agent read a texture PNG; the server has no vision encoder, OpenCode sent the image and the request failed three times, ending the turn. Fix: `modalities.input ["text"]` on the model; verified that a PNG read now returns an error text to the model and the turn goes on.
- One kit bug made those losses worse: `init` overwrote TASK.md with the placeholder, so agents that wrote the request first (AGENTS.md step 2 comes before init) lost it; 5 trials ended with the placeholder, and nothing on disk said what had been decided.

## Fixed in kit 0.5.0-beta.28 (verified)
- `init` keeps a TASK.md that is not the shipped placeholder; `status` and `check game` say when TASK.md is still the placeholder.
- Parallel generator calls (S1-r2: agents issue tool calls in parallel) interleaved edits of ModItems/lang and lost entries: `init`/`new`/`check` take one project lock, generators wait up to 60 s. Test: three concurrent `new item` calls lose an entry 3 of 3 times without the lock.
- "No @GameTest scenarios matched '*'" when no test exists yet now says to write tests with `new test` (S3-r1, S5-r1).
- blocks.md: redstone side conventions (BlockRedstoneDiode 120-130, 189-204, 247-252, World.getRedstonePower 3555): `getWeakPower`'s `side` points from the reader toward the block; one-side input with `getRedstonePower(pos.offset(side), side)`; diodes name their input side FACING. Seen in S2 r1, r2 and the Claude control.
- blocks.md: a crop with another stage count (BlockCrops 24-40, 76-96, 171-175): static `AGE`, overrides of `getAgeProperty`/`getMaxAge`/`createBlockState`, light rules. Seen in S1 r1, r2 and the Claude control. Fixture `AmberCrop` + game test `crop-four-stages`.
- fluids.md: the actual rendering code (state mapper + `forge_marker` blockstate) instead of one vague line; textures from the Fluid constructor. Seen in S4-r1 and the Claude control. Fixture `SyrupBlock` + game test `syrup-fluid`.
- TESTING.md: test players never die, respawn or change dimension (FakePlayer onDeath/changeDimension), post `PlayerDropsEvent`/`PlayerEvent.Clone` yourself (S3-r2, S6-r1, S6-r2, Claude S3); broadcasts are packets and never reach `messages()` (S0 r1, r2); `getWorldTime` stands still but `getTotalWorldTime` counts (WorldServer 224-229; S6-r1 believed the opposite and redesigned three times).
- effects-enchantments.md: `canApply` calls `canApplyAtEnchantingTable` (Enchantment 188-191), so one override covers table, loot and anvil; books need `isAllowedOnBooks` (S3-r2).
- AGENTS.md: `api ... -Full`, read a vanilla analogue whole instead of member by member (S2-r2, S3-r2).

## Reported, not acted on
- Rejected after checking: S6-r1 "getTotalWorldTime does not advance in game tests" (it does; the doc now says which clock stands still). S2-r1 "BlockPos mutation" was its own mistake (it says so).
- Single sightings left: harness helpers for entity right-click and real player death, `api` suggestion quality (substring matches, nested classes, static constants), vanilla recipe collision lint, generator `stats:` line for food, STUCK keyed per test, `-Find` alternation, HUD overlay guide, vanilla mob AI guide.
