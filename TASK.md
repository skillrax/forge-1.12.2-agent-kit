# Current maintainer task

Objective: Make the kit efficient and reliable for AI mod authoring, down to small local models (branch claude/kit-refactor, kit 0.5.0-beta.14).

Direction (user, 2026-10-16): the kit must let an AI build mods it has never seen. Turn each field test into general facts, checks or hints where an AI gets stuck, never into a feature or guide for that one mod. Generators stay small, for standard content only.

Result:
- beta.10-11: snippet check, -Only required; generator-first wording, compile-to-check names, ore/comparator/sneak facts.
- p1-local-b (qwen3.8-flash-next IQ3, 70 tok/s, >150k tokens): mod correct, stuck 40 min on its own test: an earlier comparator powered the hopper. beta.12: TESTING.md test-world facts (one behavior per test, redstone carries, area ticks, hopper enabled=false = powered), fixture test hopper-under-tile, progress hides exception noise.
- beta.13 (systemic): failed game tests print `area` (placed blocks with state, redstone received, inventory; entities); STUCK after 3 identical failures; AGENTS.md step 7: a partial report is a valid finish. Probe: the p1-local-b trap shows the whole power chain in one failure.
- p1-local-c (20 min) and p2-local (33 min) pass on beta.13, runClient verified. beta.14 from P2 feedback: failing test scaffold; client-only NoSuchMethodError hint; t.messages(player); held-item onUpdate note; api covers any class.
- Evidence: Test-GuideSnippets, Test-AgentInterface, Test-TestingTools, Test-Packaging, Test-KitSamples PASS.

Next action: P3-P7 on beta.14, same model and limits (opencode.json 65k/32k); record STUCK/area use. Deferred: static client-only member lint (on a second occurrence).

Blocker: None. Not verified: graphical client: manual runClient check per release.
