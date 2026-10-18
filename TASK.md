# Current maintainer task

Objective: Make the kit efficient and reliable for AI mod authoring, down to small local models (main, kit 0.5.0-beta.26).

Direction (user, 2026-10-16): the kit must let an AI build mods it has never seen. Turn each field test into general facts, checks or hints where an AI gets stuck, never into a feature or guide for that one mod. Generators stay small, for standard content only.

Result (trials in qa/local-model/results; feedback claims verified against 1.12.2 source before acting):
- Series 1 (beta.10-23): see results/SUMMARY.md and git history.
- Series 2 on beta.23 (results/series2-notes.md): 6 of 9 work, S5/S6 partly or after one round trip (client-side defects), S3 fail (harness bug + old sampler setup).
- beta.24 from series 2: ore cascade fix, targetPlayer damage, drops() fix, recipe lint, client-only call check, STUCK per test, tint/useOnBlock/effect facts (git a4d8e45).
- beta.24 reruns (results/beta24-reruns.md): S3 slingshot and S5 linked chest now work (one and two user round trips).
- beta.25 from the reruns: FLAKY after fail-pass-fail at one step; area dump shows mob hp/burning/hurt cooldown; t.craft rejects a short grid with blanks; recipe generator requires @meta for vanilla items with variants; WARN for spawnEntity without isRemote in both-sides hooks (0 false positives on all trial mods); version refuses downgrades; export message names `version`; test-world and lang-key facts.
- Setup A/B on S4 (results/beta25-s4-setup-ab.md): identical settings vary ~2x; standard setup fixed in qa/local-model/README.md. beta.26: right-click event facts and example (SUCCESS result), clearer step-in-step error, test expectations from the request.
- Evidence: Test-AgentInterface (12 groups), Test-TestingTools, Test-GuideSnippets, Test-Packaging, Test-KitSamples, Test-KitZip PASS; ZIP releases/0.5.0-beta.26.

Next action: a fresh held-out trial series (series 3) on beta.26 with the standard setup.

Blocker: None. Not verified: graphical client: manual runClient check per release.
