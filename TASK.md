# Current maintainer task

Objective: Make the kit efficient and reliable for AI mod authoring, down to small local models (main, kit 0.5.0-beta.25).

Direction (user, 2026-10-16): the kit must let an AI build mods it has never seen. Turn each field test into general facts, checks or hints where an AI gets stuck, never into a feature or guide for that one mod. Generators stay small, for standard content only.

Result (trials in qa/local-model/results; feedback claims verified against 1.12.2 source before acting):
- Series 1 (beta.10-23): see results/SUMMARY.md and git history.
- Series 2 on beta.23 (results/series2-notes.md): 6 of 9 work, S5/S6 partly or after one round trip (client-side defects), S3 fail (harness bug + old sampler setup).
- beta.24 from series 2: `new ore` +8 offset doubled WorldGenMinable's own (cascading worldgen; 530 -> 0 warnings over 576 real chunks) plus a Nether cascade test; targetPlayer takes player damage; drops() no longer recounts a neighbour's items; recipe lint for ingredients matching nothing; exception type in test failures; client-only call check after build (ERROR in tests, WARN in common code); Items.<block> hint; STUCK also at 5 failures of one test; Read-Trial groups by message; facts: useOnBlock, tinting, particle index, kept-on effects, coloured armor, hopper faces, recipe data, liquids in worldgen, tick-event players, test name grammar, Done covers what is drawn.
- beta.24 reruns (results/beta24-reruns.md): S3 slingshot and S5 linked chest now work (one and two user round trips).
- beta.25 from the reruns: FLAKY after fail-pass-fail at one step; area dump shows mob hp/burning/hurt cooldown; t.craft rejects a short grid with blanks; recipe generator requires @meta for vanilla items with variants; WARN for spawnEntity without isRemote in both-sides hooks (0 false positives on all trial mods); version refuses downgrades; export message names `version`; facts: same-tick steps, hurt cooldown/bounce, no-translate server, vanilla lang keys from stacks, ask about item/permission choices, GUI handler tests.
- Evidence: Test-AgentInterface (12 groups), Test-TestingTools, Test-GuideSnippets, Test-Packaging, Test-KitSamples, Test-KitZip PASS; ZIP releases/0.5.0-beta.25.

Next action: a fresh held-out trial series (series 3) on beta.25 with the S4+ sampler setup.

Blocker: None. Not verified: graphical client: manual runClient check per release.
