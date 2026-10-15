# Current maintainer task

Objective: Make the kit efficient and reliable for AI mod authoring, down to small local models (branch claude/kit-refactor, kit 0.5.0-beta.9).

Direction (user, 2026-10-16): the kit must let an AI build mods it has never seen. Turn each field test into general facts, checks or hints where an AI gets stuck, never into a feature or guide for that one mod. Generators stay small, for standard content only.

Result:
- beta.8: generators honor explicit tool/armor numbers, `-Only`, wrong-kind options rejected, `stats:` line (dense diamond feedback).
- beta.9: lessons from a hard custom item (region capture, ghost preview) added to topic guides: what ticks where and when (FORGE-1.12.md); setBlockState side effects, moving blocks with tiles (world-actions); detaching/copying/moving entities (entities); onItemUseFirst, large stack data (items); WorldSavedData (players); tile lifecycle; in-world previews (rendering). Tested code is in qa/fixtures/guide-samples (RegionMover, BagStore, BagGhost, CubicBagTest). Claims checked against the 1.12.2 source with `api`.
- Evidence: Test-AgentInterface, Test-Packaging, Test-KitSamples PASS.

Known gap: older guide snippets not in guide-samples (persisted player NBT, re-equip animation, keybind, TESR clock); no automated snippet check.

Next action: A held-out set of unseen mod prompts (mostly hard); run a fresh agent (32k local model) on each, record where it gets stuck, fix the general cause.

Blocker: None. Not verified: graphical client (ghost preview), real local-model behavior.
