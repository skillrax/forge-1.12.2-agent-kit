# p2-local - OpenCode CLI, qwen3.8-flash-next IQ3_XXS (Strata), kit 0.5.0-beta.13, 2026-10-17
Limits: opencode.json context 65k, output 32k, compaction keeps 16k (buffer 8k); backend 131k (unused)
Outcome: works      Checked by: check game PASS without WARN (14 assertions, 158 ticks), runClient (user)
Time: 33 min 30 s at ~69 tok/s   Interventions: 0
Edited pinned build files: no   Wrote game tests: yes   Report honest: yes
Design note: right-click on doors/levers toggles the ring instead of using the block (model's own choice, not asked)

## Where it got stuck (from its kit-feedback answer, claims checked against 1.12.2 source)
- `new test` scaffold reported PASS (1 assertion, 2 ticks) while still a placeholder; noticed only by the odd counts (category: generator: false PASS)
- `EntityItem.getAge()` compiled but threw NoSuchMethodError on the server: it is @SideOnly(CLIENT); `api` already marks it [client-only] (category: sides/sync; one 25 s run)
- `Item.onUpdate` never fires in tests: `FakePlayer.onUpdate()` is a no-op; learned from TestContext source, not docs (category: docs not found)
- Chat feedback untestable: `FakePlayer.sendMessage`/`sendStatusMessage` are no-ops (category: harness gap)
- Wrote two scripts to read vanilla source before finding that `api` covers any mapped class (category: docs not found)
- Liked: area snapshot, 23 s test loop, invariants for per-tick behavior, api [client-only] tag.

## General fix (would it help a different mod?)
- kit 0.5.0-beta.14: scaffold check fails until replaced; NoSuchMethodError/NoSuchFieldError hint names client-only members (test failures and log); TESTING.md: call `onUpdate` yourself for held-item behavior; harness `t.messages(player)` records chat and action bar; AGENTS.md: `api` covers any Minecraft/Forge class. Fixture test messages-and-held-item-ticks proves the harness facts.
- Deferred until a second occurrence: static lint for client-only member calls from common code.
