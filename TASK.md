# Current maintainer task

Objective: Make the kit efficient and reliable for AI mod authoring, down to small local models (branch claude/kit-refactor, kit 0.5.0-beta.23; baseline on main).

Direction (user, 2026-10-16): the kit must let an AI build mods it has never seen. Turn each field test into general facts, checks or hints where an AI gets stuck, never into a feature or guide for that one mod. Generators stay small, for standard content only.

Result (trials in qa/local-model/results; feedback claims verified against 1.12.2 source before acting):
- beta.10-12: snippet check, -Only required, generator-first wording, compile-to-check names, test-world facts.
- beta.13 (systemic): failed tests print `area` snapshots; STUCK after 3 identical failures; a partial report is a valid finish.
- P1 (20 min), P2 (33), P3 (9), P4 mob (65; STUCK and area used), P6 stamina (22), P7 vein miner (45; 54 api lookups) pass; series summary in results/SUMMARY.md; P5 pads (29) partly (needed the partner's chunk loaded).
- beta.14-18 from their feedback: failing scaffold, t.messages, t.targetPlayer, t.loot, api reads whole methods and TestContext, filter/plan-time/targeting/far-partner facts. beta.17 (user decision): `new mob -Like <9 vanilla bases>`.
- beta.19-21: usage log + Read-Trial (no watching needed); long api reads show start, end and Forge lines; look up behavior first; net.minecraft.init fact; enchantment/effect lang lint.
- beta.23: consolidation pass: contradictions fixed (troubleshooting vs STUCK, world-actions vs far partners), mob and lint facts carried into INDEX/GENERATORS/TESTING, HOSTS.md updated from trials.
- Evidence: all qa/unit suites and Test-KitSamples PASS (beta.23).

Next action: trial series 2 from qa/local-model/PROMPTS-2.md (written without reading the kit) on beta.23, unattended + Read-Trial. Deferred: client-only lint.

Blocker: None. Not verified: graphical client: manual runClient check per release.
