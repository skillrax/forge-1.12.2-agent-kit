# Current maintainer task

Objective: Make the kit efficient and reliable for AI mod authoring, down to small local models (branch claude/kit-refactor, kit 0.5.0-beta.19).

Direction (user, 2026-10-16): the kit must let an AI build mods it has never seen. Turn each field test into general facts, checks or hints where an AI gets stuck, never into a feature or guide for that one mod. Generators stay small, for standard content only.

Result (trials in qa/local-model/results; feedback claims verified against 1.12.2 source before acting):
- beta.10-12: snippet check, -Only required, generator-first wording, compile-to-check names, test-world facts.
- beta.13 (systemic): failed tests print `area` snapshots; STUCK after 3 identical failures; a partial report is a valid finish.
- P1 (20 min), P2 (33), P3 (9), P4 mob (65; STUCK and area used) pass. beta.14-16 from their feedback: failing scaffold, t.messages, t.targetPlayer, t.loot, api reads whole methods, filter/plan-time/targeting facts.
- beta.17 (user decision): `new mob -Like <9 vanilla bases>` with stats, drops, egg, spawns, renderer and generated checks.
- P5 pads (29 min) partly: needed the partner's chunk loaded, likely from the guides' never-load-chunks advice. beta.18: far-partner rule, empty-hand useOnBlock PASS, other dimensions testable, api TestContext.
- beta.19: every agent.ps1 call logged to reports/agent-log.jsonl; qa/local-model/Read-Trial.ps1 summarizes a trial folder (checks, STUCK, area, api, generators, changed kit files) so runs need no watching.
- Evidence: Test-GuideSnippets, Test-AgentInterface, Test-TestingTools, Test-MappedSource, Test-Packaging PASS; Test-KitSamples last passed on beta.17 (no harness change since).

Next action: summarize P6 (beta.18) with Read-Trial; P7 on beta.19 (P5b fixed chunk loading with tickets on beta.17; recorded). Deferred: client-only lint.

Blocker: None. Not verified: graphical client: manual runClient check per release.
