# Current maintainer task

Objective: Make the kit efficient and reliable for AI mod authoring, down to small local models (main, kit 0.5.6).

Direction (user, 2026-10-16): the kit must let an AI build mods it has never seen. Turn each field test into general facts, checks or hints where an AI gets stuck, never into a feature or guide for that one mod. Generators stay small, for standard content only.

Result:
- Series 1-3 and the Claude 0.5.4 baseline: qa/local-model/results (SUMMARY.md, series2-notes.md, series3-local.md, series3-claude-baseline-0.5.4.md) and git history.
- 0.5.6 from a large Claude-built port (Sophisticated Backpacks, ~760 files, 57 tests; feedback/backport-sophisticated-backpacks-20261021.md, claims verified with api): restart-test areas no longer reused past 36 tests (reproduced with the old harness: 41 tests, restart block lost; fixed: kept), test count in the check summary, channel-name lint, GuiContainer drawDefaultBackground lint, export -Replace, guides/compat.md, eight verified 1.12 facts.
- Evidence: Test-AgentInterface (13 groups), Test-TestingTools, Test-GuideSnippets, Test-Packaging, Test-KitSamples, Test-KitZip PASS (qa/Run-All.ps1, all 6 suites, 2026-10-21).

Next action: biggest gap from the port is client-side: client container sync test (open a container on the test client, compare slots with the server), configurable client scenes (open screen X, hover item), optional mods at runtime in dev. Then share 0.5.6 with friends; local-model reruns when the PC is free.

Blocker: None. Not verified: graphical client: manual runClient check per release.
