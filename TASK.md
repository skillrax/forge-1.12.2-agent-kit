# Current maintainer task

Objective: Make the kit efficient and reliable for AI mod authoring, down to small local models (branch claude/kit-refactor, kit 0.5.0-beta.16).

Direction (user, 2026-10-16): the kit must let an AI build mods it has never seen. Turn each field test into general facts, checks or hints where an AI gets stuck, never into a feature or guide for that one mod. Generators stay small, for standard content only.

Result:
- beta.10-11: snippet check, -Only required; generator-first wording, compile-to-check names, ore/comparator/sneak facts.
- p1-local-b: stuck 40 min on its own test (comparator powered a hopper); beta.12 test-world facts.
- beta.13 (systemic): failed game tests print `area` (placed blocks with state, redstone received, inventory; entities); STUCK after 3 identical failures; AGENTS.md step 7: a partial report is a valid finish. Probe: the p1-local-b trap shows the whole power chain in one failure.
- p1-local-c (20 min), p2 (33 min), p3 (9 min) pass; beta.14-15 from their feedback (scaffold fails, messages, api reads, filter fact).
- p4-local (mob, 65 min) passes on beta.15; STUCK fired and was followed; area lines used. beta.16: t.targetPlayer (mobs can target and hurt it), t.loot; plan()-runs-at-tick-0 warning; mob movement bases and targeting gates; api member reads 40 lines by default.
- Evidence: Test-GuideSnippets, Test-AgentInterface, Test-TestingTools, Test-MappedSource, Test-Packaging, Test-KitSamples PASS.

Next action: P5-P7 on beta.16, same model and limits. Pending user decision: a small `new mob` generator (one trial asked). Deferred: client-only lint, spawn-biome lint.

Blocker: None. Not verified: graphical client: manual runClient check per release.
