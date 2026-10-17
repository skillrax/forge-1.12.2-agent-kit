# Current maintainer task

Objective: Make the kit efficient and reliable for AI mod authoring, down to small local models (branch claude/kit-refactor, kit 0.5.0-beta.21).

Direction (user, 2026-10-16): the kit must let an AI build mods it has never seen. Turn each field test into general facts, checks or hints where an AI gets stuck, never into a feature or guide for that one mod. Generators stay small, for standard content only.

Result (trials in qa/local-model/results; feedback claims verified against 1.12.2 source before acting):
- beta.10-12: snippet check, -Only required, generator-first wording, compile-to-check names, test-world facts.
- beta.13 (systemic): failed tests print `area` snapshots; STUCK after 3 identical failures; a partial report is a valid finish.
- P1 (20 min), P2 (33), P3 (9), P4 mob (65; STUCK and area used), P6 stamina (22), P7 vein miner (45; 54 api lookups) pass; series summary in results/SUMMARY.md; P5 pads (29) partly (needed the partner's chunk loaded).
- beta.14-18 from their feedback: failing scaffold, t.messages, t.targetPlayer, t.loot, api reads whole methods and TestContext, filter/plan-time/targeting/far-partner facts. beta.17 (user decision): `new mob -Like <9 vanilla bases>`.
- beta.19: every agent.ps1 call logged (reports/agent-log.jsonl); qa/local-model/Read-Trial.ps1 summarizes a trial folder, so runs need no watching.
- beta.20: long api member reads show start, end and Forge lines from the middle; AGENTS.md: look up behavior and data layout before relying on it.
- beta.21: net.minecraft.init fact, two later-version renames, enchantment restriction guide, enchantment/effect lang lint.
- Evidence: Test-GuideSnippets, Test-AgentInterface, Test-TestingTools, Test-MappedSource, Test-Packaging PASS; Test-KitSamples PASS (beta.21).

Next action: series 1 done. Next: new held-out prompts, ideally a second model family, unattended + Read-Trial; consider trimming AGENTS.md (3.5 KB, budget ~2.5 KB). Deferred: client-only lint.

Blocker: None. Not verified: graphical client: manual runClient check per release.
