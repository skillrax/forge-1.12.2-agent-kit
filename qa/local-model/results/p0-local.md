# p0-local - OpenCode CLI, Qwen3.8-27B-GSQ-RCO IQ3_XXS (KV cache Q8, KoboldCpp, custom samplers, 64k), kit 0.5.0-beta.10, 2026-10-16
Outcome: works, with design slips      Checked by: check server, check game, runClient (user)
Time: not recorded   Context peak: not recorded   Overhead before prompt: not recorded   Interventions: 0
Edited pinned build files: no   Wrote game tests: generated only   Report honest: yes

## Where it got stuck
- Ore drops ingots plus XP, although the prompt says "like iron" and "smelt the ore into ingots" -> GENERATORS.md said only "-Drops item (else drops itself)", nothing about which vanilla ores drop themselves (category: docs not found; partly model reasoning)
- Ore rarer than iron -> generator defaults (8 veins x 8, y 5-48) and no vanilla ore numbers in the docs, unlike tools/armor (category: docs not found)
- Correct: only pickaxe + sword, tool numbers between stone and iron, harvest level 1.

## General fix (would it help a different mod?)
- kit 0.5.0-beta.11: GENERATORS.md states which vanilla ores drop themselves vs items + XP, and a vanilla vein/size/height table (verified against ChunkGeneratorSettings and BlockOre source).
