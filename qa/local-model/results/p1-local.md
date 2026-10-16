# p1-local - OpenCode CLI, Qwen3.8-27B-GSQ-RCO IQ3_XXS (KV cache Q8, KoboldCpp, custom samplers, 64k), kit 0.5.0-beta.10, 2026-10-16
Outcome: partly (compiles; game test written but never run; no check server, no export)      Checked by: user stopped it; evidence = last compaction summary + model's self-report
Time: 2 h 22 min at ~7 tokens/s   Generated: ~60k tokens (thinking share unknown)   Context: compacted at least once   Interventions: 0 (stopped by user)
Edited pinned build files: no   Wrote game tests: scaffold only, last   Report honest: yes ("I over-verified")

## Where it got stuck
- Hand-wrote block, tile, NBT, capability, registration, blockstate, models, lang and a System.Drawing texture script: all of which `new tile coin_jar -Inventory 1` generates -> AGENTS.md/GENERATORS.md (beta.8 wording) said a "machine with custom rules is hand-written; do not bend a generator into it", read as "no generators for custom content" (category: docs wrong)
- Re-checked every signature and read source (7 docs, an example, full TestContext.java) before running checks -> AGENTS.md "unsure: api, never guess" with no cheaper path named (category: docs wrong)
- Seven API mistakes, each a check cycle: setCount returns void, getCapability arg order, invented dropItemStack / appendItem / addItemToInventory / NBTTagList.getCompoundTag, comparator thought to be on the tile (category: wrong-version API / docs not found)
- Had to confirm from source that sneak + empty-hand right-click still reaches onBlockActivated (category: docs not found)
- PowerShell one-liners with `$` mangled by OpenCode's shell (category: shell/tool use)
- Game test left to the end, so behavior was never checked in 2 h (category: docs wrong: loop did not say when)
- Design itself was sound: LIFO runs, extract-only handler on DOWN, comparator formula, server-side changes.

## General fix (would it help a different mod?)
- kit 0.5.0-beta.11: start custom content from the closest generator (`new tile` etc.) and add behavior there; compile to check names, `api` only when error + hint do not settle it; game test as soon as it compiles; comparator output and sneak-click facts in blocks/tile-entities guides; compiler hints for the four invented names; shell rule for `$` one-liners.
- Not a kit fix: 7 tokens/s; try thinking off or capped to measure its share.
- Rerun P1 clean on beta.11; it failed locally, so also run the Claude Code control.
