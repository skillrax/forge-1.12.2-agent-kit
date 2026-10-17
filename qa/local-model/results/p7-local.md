# p7-local - OpenCode CLI, qwen3.8-flash-next IQ3_XXS (Strata), kit 0.5.0-beta.20, 2026-10-18
Limits: opencode.json context 65k, output 32k, compaction keeps 16k (buffer 8k)
Outcome: works      Checked by: check game PASS, runClient (user): all enchantments and mining levels behave as asked; other mods' ores not tested (out of scope)
Time: 45 min   Interventions: 0 (OpenCode stopped once mid-run; "continue" resumed it: a frontend stall, unnoticed for a while)
Read-Trial (first run with the usage log): 62 calls in 45 min, only 2 min inside agent.ps1: api 54, check 5, new 1, init 1, status 1. Checks EbbgG: two build failures, one failed game test (assertion name "16-left"), then PASS. STUCK never. Kit files changed: none.
Observation: 54 api lookups for 5 checks: beta.20's "look up behavior before relying on it" was followed; it credits api with the event-order and survival-harvest findings that let it drop a planned test.

## Where it got stuck (from its kit-feedback answer, claims checked against its own check reports and 1.12.2 source)
- Later-version names: `net.minecraft.block.Blocks`, `EnchantmentHelper.getFortuneModifier`, `Block.onBlockDestroyedByPlayer` (category: model priors). Claimed the hints named no replacement: false per its report; Blocks got `import net.minecraft.init.Blocks`, onBlockDestroyedByPlayer listed `Block.onPlayerDestroy` second; only getFortuneModifier had no useful suggestion.
- No doc mentioned `net.minecraft.init` (true; category: docs not found)
- Enchantment guide lacked restricting to an item class (`canApply`/`canApplyAtEnchantingTable`) and a mining test pattern (true; category: docs not found)
- check server did not lint enchantment/effect translations (true; latent, did not bite)
- AGENTS.md step 3 had no branch for content without a generator (true, minor)
- Read all of TestContext.java against TESTING.md's advice (its own); assertion names starting with digits (documented rule, its own)
- Display bug found while checking: a compile error's code line showed Gradle's trailing "For more on this..." text.

## General fix (would it help a different mod?)
- kit 0.5.0-beta.21: hints `getFortuneModifier` -> `getEnchantmentLevel(Enchantments.FORTUNE, stack)`, `onBlockDestroyedByPlayer` -> `onPlayerDestroy`; Gradle trailer lines filtered from compile errors; AGENTS.md: vanilla constants live in net.minecraft.init, hints name "the replacement or the closest members", no-generator content goes to the guide; enchantment guide: canApply/canApplyAtEnchantingTable, getEnchantmentLevel, mining-enchantment test; AssetLint warns on missing enchantment and effect translations. Read-Trial reports the last check instead of a misleading per-profile result.
