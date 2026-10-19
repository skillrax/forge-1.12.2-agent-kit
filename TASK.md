# Current maintainer task

Objective: Make the kit efficient and reliable for AI mod authoring, down to small local models (main, kit 0.5.5).

Direction (user, 2026-10-16): the kit must let an AI build mods it has never seen. Turn each field test into general facts, checks or hints where an AI gets stuck, never into a feature or guide for that one mod. Generators stay small, for standard content only.

Result (trials in qa/local-model/results; feedback claims verified against 1.12.2 source before acting):
- Series 1 (beta.10-23): see results/SUMMARY.md and git history.
- Series 2 on beta.23 (results/series2-notes.md): 6 of 9 work, S5/S6 partly or after one round trip (client-side defects), S3 fail (harness bug + old sampler setup).
- beta.24-26: fixes from series 2, reruns and the S4 setup A/B (git log).
- Setup A/B on S4 (results/beta25-s4-setup-ab.md): identical settings vary ~2x; standard setup fixed in qa/local-model/README.md. beta.26: right-click event facts, step error, test expectations.
- Series 3 (PROMPTS-3.md): Claude control 8/8 PASS (beta.27); local 10/16, 5 of 6 failures were OpenCode setup (compaction buffer, image read), fixed in the runner; beta.28 from it (results/series3-local.md).
- Evidence: Test-AgentInterface (13 groups), Test-TestingTools, Test-GuideSnippets, Test-Packaging, Test-KitSamples, Test-KitZip PASS; ZIP releases/0.5.0 (MIT, tag v0.5.0).

Next action: share kit 0.5.5 with friends (releases/0.5.5; their setups are the first cold-machine test); local-model reruns when the PC is free. Claude 0.5.4 baseline 54/54 and playtest of S1/S3/S4/S6/S7 all as requested (results/series3-claude-baseline-0.5.4.md).

Blocker: None. Not verified: graphical client: manual runClient check per release.
