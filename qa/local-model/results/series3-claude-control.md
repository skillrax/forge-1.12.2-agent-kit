# Series 3 control pass: Claude Code (Sonnet), kit 0.5.0-beta.26, 2026-10-19

Run unattended with `Run-Series.ps1 -Prompts qa/local-model/PROMPTS-3.md -Agent claude -Model sonnet` (trials in `kit-trials/series3-claude-0.5.0-beta.26`). Outcome = the runner's own `check game`; nobody has looked in the client yet.

| Trial | check game | Min | Checks | Notes |
|---|---|---|---|---|
| S0 whereami command | PASS | 5 | bggGGGG | client-only `Biome.getBiomeName` caught by the lint |
| S1 glowberry crop | PASS | 4 | BggGGG | custom stage count needs 6 overrides; `t.player` returned a new object |
| S2 pulse counter | PASS | 3 | BGGG | diode side conventions from memory |
| S3 soulbound/gathering enchantments | PASS | 9 | sSgggggggGSGG | FakePlayer death/drops, `t.player` identity, test class must be public |
| S4 maple syrup fluid + tank | PASS | 8 | ssSggggGGG | `enableUniversalBucket` placement, bucket NBT |
| S5 fetching wolves | PASS | 5 | bggGGGG | animals vanished in the test world |
| S6 tool belt + hotkey | PASS | 3 | BGGG | invisible per-player belt (no item); HUD and key are client-only |
| S7 blast kiln multiblock | PASS | 10 | BSggGGG | tile recreated when the controller's state changed |

All 8 passed with no nudges in 47 min of agent time. A strong model finishes this series, so what remains are the frictions it reported, several seen more than once.

## Fixed in kit 0.5.0-beta.27 (verified)
- `./agent.ps1` run from Bash fails with a PowerShell parse error (S0, S2, S3, S5): new `./agent` shim for Bash; AGENTS.md names both.
- Multi-file shell heredocs failed and forced full rewrites (S2, S6, S7): AGENTS.md rule now says create and change files with the edit/write tool, never heredocs.
- `new tile` left a `ticks` counter and a "ticks:" chat line to remove (S2, S7): the counter only with `-Ticking` (marked as a sample); the right-click stub is a TODO.
- Tile recreated on a block-state change (S7; TileEntity.shouldRefresh line 414: mod tiles refresh on any state change): `new tile` overrides `shouldRefresh`; tile-entities.md states it.
- `t.player(name)` returned a new object per call (S1, S3): one player per name per test (also `targetPlayer`).
- Animals vanished in tests (S5; WorldServer line 861 kills animals while spawn-animals is false): the test server now allows animals; natural spawning stays off (doMobSpawning false, flat world has no generation spawns, ChunkGeneratorOverworld only).
- The first compile error in a fresh project had no hints (S0: "run check environment to build the API index"): `compileJava` now depends on `kitApiIndex`.

## Reported once, left for the local runs to confirm
Crops with other than 8 stages (blocks.md), diode-like redstone blocks, fluid buckets and `enableUniversalBucket` placement, HUD overlays, changing a vanilla mob without subclassing, config GUI factory, FakePlayer death/clone in tests, command output capture in tests, a generator dry run, `init` skeleton noise for command-only mods.
