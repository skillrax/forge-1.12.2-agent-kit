# Current maintainer task

Objective: Make the kit efficient and reliable for AI mod authoring, down to small local models (branch claude/kit-refactor, kit 0.5.0-beta.18).

Direction (user, 2026-10-16): the kit must let an AI build mods it has never seen. Turn each field test into general facts, checks or hints where an AI gets stuck, never into a feature or guide for that one mod. Generators stay small, for standard content only.

Result:
- beta.10-11: snippet check, -Only required; generator-first wording, compile-to-check names, ore/comparator/sneak facts.
- p1-local-b: stuck 40 min on its own test (comparator powered a hopper); beta.12 test-world facts.
- beta.13 (systemic): failed game tests print `area` (placed blocks with state, redstone received, inventory; entities); STUCK after 3 identical failures; AGENTS.md step 7: a partial report is a valid finish. Probe: the p1-local-b trap shows the whole power chain in one failure.
- p1-local-c (20 min), p2 (33 min), p3 (9 min) pass; beta.14-15 from their feedback (scaffold fails, messages, api reads, filter fact).
- p4-local (mob, 65 min) passes on beta.15; STUCK fired and was followed; area lines used. beta.16: t.targetPlayer (mobs can target and hurt it), t.loot; plan()-runs-at-tick-0 warning; mob movement bases and targeting gates; api member reads 40 lines by default.
- beta.17 (user decision): `new mob <name> -Like <zombie|skeleton|spider|creeper|blaze|ghast|cow|pig|chicken>` with health/attack/speed, drops range, egg colors, spawn biomes/types, vanilla renderer; generated checks for stats, egg, drop range and spawn entries. All nine bases compiled and checked in a probe.
- p5-local (pads, 29 min) partly: pads needed the partner's chunk loaded, likely from the guides' never-load-chunks advice. beta.18: far-partner rule (one-off actions may load), empty-hand useOnBlock PASS, other dimensions testable, api TestContext.
- Evidence: Test-GuideSnippets, Test-AgentInterface, Test-TestingTools, Test-MappedSource (+folder), Test-Packaging PASS; Test-KitSamples last passed on beta.17 (no harness change since).

Next action: record P5b (user asked the model to fix chunk loading); then P6-P7 on beta.18. Deferred: client-only lint.

Blocker: None. Not verified: graphical client: manual runClient check per release.
