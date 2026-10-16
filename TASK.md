# Current maintainer task

Objective: Make the kit efficient and reliable for AI mod authoring, down to small local models (branch claude/kit-refactor, kit 0.5.0-beta.12).

Direction (user, 2026-10-16): the kit must let an AI build mods it has never seen. Turn each field test into general facts, checks or hints where an AI gets stuck, never into a feature or guide for that one mod. Generators stay small, for standard content only.

Result:
- beta.10: Test-GuideSnippets enforces that every ```java block in kit/docs is in qa/fixtures/guide-samples; `-Only` required for tools/armor.
- Local trials (qa/local-model/results): p0-local works (ore drop/rarity slips); p1-local only compiled after 2 h 22 min at 7 tok/s: hand-wrote what `new tile` generates, over-verified signatures, tested last.
- beta.11: start custom content from the closest generator; compile to check names; test as soon as it compiles; vanilla ore facts; comparator/sneak facts; hints for invented names.
- p1-local-b (qwen3.8-flash-next IQ3, 70 tok/s, >150k tokens): mod correct, stuck 40 min on its own test: an earlier comparator powered the hopper. beta.12: TESTING.md test-world facts (one behavior per test, redstone carries, area ticks, hopper enabled=false = powered), fixture test hopper-under-tile, progress hides exception noise.
- Evidence: Test-GuideSnippets, Test-AgentInterface, Test-TestingTools, Test-KitSamples PASS.

Next action: systemic, since dead ends cannot be enumerated: (1) harness prints a snapshot of the blocks a failing test placed (state, redstone power, inventory); (2) agent.ps1 circuit breaker after 3 failures of the same test: isolate it, then report; (3) AGENTS.md: a precise partial report is a valid finish. Then rerun P1 fresh + Claude Code control.

Blocker: None. Not verified: graphical client: manual runClient check per release.
