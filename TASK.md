# Current maintainer task

Objective: Make the kit efficient and reliable for AI mod authoring, down to small local models (branch claude/kit-refactor, kit 0.5.0-beta.10).

Direction (user, 2026-10-16): the kit must let an AI build mods it has never seen. Turn each field test into general facts, checks or hints where an AI gets stuck, never into a feature or guide for that one mod. Generators stay small, for standard content only.

Result:
- beta.8/9: generators honor explicit numbers, `stats:` line; general lessons from a hard custom item in the topic guides.
- beta.10: `qa/unit/Test-GuideSnippets.ps1` (~1 s, run first by Test-KitSamples) fails when a ```java block in kit/docs is not in qa/fixtures/guide-samples. 17 of 30 blocks were not; each was aligned with tested code, added with a game test (player data across respawn copy, item push, tank NBT trap, craftOnce, energy capability, re-equip, recipes by hand) or cut to prose (TESR clock, installed-test fragment). Fixtures renamed by mechanism (ModSavedData, RegionPreview, RegionToolItem). `new tools`/`new armor` require `-Only <pieces>|all`. Lint no longer WARNs on vanilla sound files (they live in the asset index, not the jar).
- Evidence: Test-AgentInterface, Test-Packaging, Test-GuideSnippets, Test-KitSamples PASS.
- Cold machine (empty Gradle cache) verified by the user on a laptop: dense-diamond test, 2026-10-16.

Next action: user runs the held-out trials in qa/local-model (OpenCode, local model up to 27B, 64k context; P0 smoke first) from releases/0.5.0-beta.10; results in qa/local-model/results/; fix general causes only.

Blocker: None. Not verified: graphical client (ghost preview, keybind screen, textures): manual runClient check per release.
