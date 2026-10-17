# Series 2 running notes (kit 0.5.0-beta.23, no kit changes until the series ends)

Setup: OpenCode CLI (context 65k, output 32k) -> Strata (G:\AI\Strata, run-iq3_xxs.bat, strata-iq3_xxs.json): Qwen3.8-Flash-Next GSQ-RCO IQ3_XXS, max context 65536, KV int8, speculative decoding --spec 4 --spec-min-p 0.70, experimental speed-projection control vector (scale 1.0, layers 4-44), fit_max_tokens true; sampling temp 0.6 for S0-S2, 0.7 from S3 (top_p 0.95, top_k 20, min_p 0.05). The control vector and lossy speculative acceptance may affect reasoning; if thinking loops persist, one run without the vector separates model setup from kit.

Trials in `beta23-prompt-set-2/S<n>`; summaries from Read-Trial. Candidate fixes collect here and are applied in one batch after S7b, only if general (seen twice, or plainly a kit bug verified in source).

| Trial | Outcome | Time (checks span) | Checks | STUCK | Kit files changed | Notes |
|---|---|---|---|---|---|---|
| S0 copper | works (user to confirm) | 2 min | EG | never | none | 4 generator calls + batch |
| S1 grinder | works (user to confirm) | 45 min | EBbbgG | never | none | 60 api lookups; one private-access compile error (GuiContainer.hoveredSlot) |
| S2 watchtower | works (user to confirm) | 8 min | EggG | never | none | one failed test (water spot accepted), fixed |
| S3 slingshot | works after ~90 min, several OpenCode restarts | 88 min | EBg x16 GG | once, after check 16 | testing/harness TestContext.java (the model fixed a harness bug) | thinking-loop output cap hits; user raised temperature 0.6 -> 0.7 mid-run |

## Candidate fixes
1. Harness bug (S3, verified): `targetPlayer` cannot take damage whose true source is a player: `EntityPlayerMP.attackEntityFrom` line 676 checks `canAttackPlayer`, and `FakePlayer.canAttackPlayer` returns false. Fix: override `canAttackPlayer` in the target player; fixture test with a player-shot projectile hitting a target player.
2. Circuit breaker (S3): STUCK keys on test + phase; the model changed setups (player target, armor stand) so the key kept changing and 16 consecutive failures of one test printed STUCK once, at check 16. Fix: also STUCK when the same test fails ~5 checks in a row regardless of phase.
3. TESTING.md damage targets (S3, to verify in source): which test targets take damage from players and projectiles (armor stands ignore it?, zombies burn by day, animals vanish).
4. Setup (S3): thinking model hit the 32k output cap repeatedly; not a kit fix; note settings per trial.
