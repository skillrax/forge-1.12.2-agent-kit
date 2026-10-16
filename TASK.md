# Current maintainer task

Objective: Make the kit efficient and reliable for AI mod authoring, down to small local models (branch claude/kit-refactor, kit 0.5.0-beta.11).

Direction (user, 2026-10-16): the kit must let an AI build mods it has never seen. Turn each field test into general facts, checks or hints where an AI gets stuck, never into a feature or guide for that one mod. Generators stay small, for standard content only.

Result:
- beta.10: Test-GuideSnippets enforces that every ```java block in kit/docs is in qa/fixtures/guide-samples; `-Only` required for tools/armor.
- Local trials (qa/local-model/results): p0-local works (ore drop/rarity slips); p1-local only compiled after 2 h 22 min at 7 tok/s: hand-wrote what `new tile` generates, over-verified signatures, tested last.
- beta.11 from those: start custom content from the closest generator; compile to check names, `api` only when error + hint do not settle it; game test as soon as it compiles; shell rule for `$` one-liners (AGENTS.md, now smaller); vanilla ore drops/XP and generation table (GENERATORS.md); comparator output and sneak-click facts (blocks, tile-entities); hints for four invented NBT/inventory names. Facts checked against 1.12.2 source.
- Evidence: Test-GuideSnippets, Test-AgentInterface, Test-TestingTools, Test-Packaging PASS (docs/hints only; Test-KitSamples last passed on beta.10).

Next action: user reruns P1 clean on releases/0.5.0-beta.11 (thinking off or capped to measure its share), plus a Claude Code control run of P1; then P2+.

Blocker: None. Not verified: graphical client: manual runClient check per release.
