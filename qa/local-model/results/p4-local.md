# p4-local - OpenCode CLI, qwen3.8-flash-next IQ3_XXS (Strata), kit 0.5.0-beta.15, 2026-10-17
Limits: opencode.json context 65k, output 32k, compaction keeps 16k (buffer 8k). Strata needed "fit_max_tokens": true mid-run (backend output limit; user asked the model to work in smaller steps)
Outcome: works      Checked by: check game, runClient (user): mob, egg, behavior
Time: ~65 min (41 + 24 after a pause)   Interventions: 0 ("continue" after the backend fix only)
Edited pinned build files: no   Wrote game tests: yes; hostility moved to its own test   Report honest: mostly (one wrong cause, below)
STUCK seen: yes, after 3 identical failures of the hostility check; it isolated the check as instructed. area lines used: yes, a dump proved its ground-fire mechanic worked while its check failed.

## Where it got stuck (from its compaction summary and kit-feedback answer, checked against 1.12.2 source)
- Mob never targeted the test player: `World.getNearestAttackablePlayer` searches only `world.playerEntities`; `t.player()` is never added (category: harness gap; the longest cost, "an hour of vanilla spelunking")
- Also blamed `FakePlayer.isEntityInvulnerable`: wrong, `isSuitableTarget` checks `capabilities.disableDamage` (category: model reasoning)
- Ground-fire check ran as plain Java in `plan()` at tick 0, before the mob ticked (category: docs not found)
- Which vanilla class gives floating movement: took long to arrive at EntityBlaze (category: docs not found)
- Loot rolls by hand: `generateLootForPools` + LootContext found by trial and error (category: harness gap)
- Claimed api fails on inherited members and that -Grep is silent: not reproduced (api walks superclasses; grep says "no source line ... contains"); likely from calling Read-MappedSource.ps1 directly
- api output size (up to 80 lines per member since beta.15) pushed it into the backend output limit (category: tool output)
- Suggested, not done: `new mob` generator (scope decision pending with user), spawn-biome lint, t.explainTargeting, docs/MOB-AI.md (covered by two entities.md lines).

## General fix (would it help a different mod?)
- kit 0.5.0-beta.16: harness `t.targetPlayer(name)` (in playerEntities, damageable, removed after the test; the maintainer's fixture test also found EntityPlayerMP's 60-tick respawn invulnerability never runs out on a FakePlayer, so it is cleared) and `t.loot(table[, entity])`; fixture test mob-targets-and-loot proves a plain t.player is never targeted and a target player is targeted and hurt, and loot ranges; TESTING.md: plan() code runs at tick 0, targetPlayer/loot rows, TestContext source path; entities.md: vanilla movement base classes and the targeting gates; api member reads default to 40 lines (-Full 150).
