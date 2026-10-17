# p5-local - OpenCode CLI, qwen3.8-flash-next IQ3_XXS (Strata), kit 0.5.0-beta.17, 2026-10-17
Limits: opencode.json context 65k, output 32k, compaction keeps 16k (buffer 8k)
Outcome: partly      Checked by: check game, runClient (user): linking and cross-dimension teleport work only while both pads' chunks are loaded; linking to an unloaded pad fails with a chat message
Time: 29 min at ~73 tok/s   Interventions: 0 (follow-up fix requested separately: P5b, below)
Edited pinned build files: no   Wrote game tests: yes   Report honest: yes

## Where it got stuck (from its kit-feedback answer, claims checked against 1.12.2 source)
- Pads required the partner pad to be loaded. Likely kit-caused: tile-entities/machines guides say "check isBlockLoaded first, never load chunks", which is right for per-tick code and wrong for a one-off player action (category: docs wrong)
- `useOnBlock` with an empty hand returned PASS although onBlockActivated ran: confirmed, `processRightClickBlock` returns PASS for an empty stack after setting SUCCESS (category: docs not found)
- Claimed cross-dimension behavior cannot be tested: false; `t.server().getWorld(-1)` is used by a passing fixture test. The docs never said other dimensions exist (category: docs not found)
- Read all 392 lines of TestContext.java (largest cost; second trial in a row): api did not cover the harness (category: tool gap)
- "Generated files changed style mid-session": not reproducible (generators only add at markers; the kit was not changed during the run); most likely stale memory after context compaction. No kit change.
- Suggested, declined: lint for "tile registered but never created by its block" (the existing failure message made it a one-cycle fix); AGENTS.md line about generator churn (see above).
- Its own: ten wrong guessed names, all fixed in one compile each by hints; overwrote the generated hasTileEntity/createTileEntity.

## General fix (would it help a different mod?)
- kit 0.5.0-beta.18: tile-entities guide "Far partners": one-off player actions may load the partner (`server.getWorld(dim)`, `getTileEntity`); store position + dimension; tickets only for unattended work. TESTING.md: empty-hand useOnBlock returns PASS; other dimensions exist and load on demand. `agent.ps1 api TestContext[.member]` lists harness signatures (34 lines instead of 392) and reads members from testing/harness/src.

## P5b (fix on request, still kit beta.17)
- Fixed: linking and teleport now work with the partner pad unloaded (user, runClient).
- Approach: ForgeChunkManager tickets (the model noted a per-mod ticket cap; its figure of 128 is unverified). Works, but keeps chunks loaded permanently; loading the partner on demand is the lighter fix that beta.18's far-partner rule now names. Baseline for the rule: a run on beta.18 should store position + dimension and load on demand.
