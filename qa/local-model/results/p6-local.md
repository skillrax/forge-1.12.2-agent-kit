# p6-local - OpenCode CLI, qwen3.8-flash-next IQ3_XXS (Strata), kit 0.5.0-beta.18, 2026-10-17
Limits: opencode.json context 65k, output 32k, compaction keeps 16k (buffer 8k)
Outcome: works      Checked by: check game PASS, runClient (user)
Time: 22 min   Interventions: 0
Read-Trial: 6 checks EbBgGG over 28 min (incl. the user's final checks); one failed test (stamina-persistence at value-is-in-save-nbt) fixed next run; one compile error (LivingUpdateEvent import); STUCK never; kit files changed: none
Design (its own, stated in TASK.md): client-authoritative stamina; the client simulates and gates sprint, the server clamps and stores what the client sends. Defensible for a single-player-feel mod (vanilla's hunger sprint gate is client-side too); a cheating client can claim full stamina. Not a kit issue.

## Where it got stuck (from its kit-feedback answer, claims checked against 1.12.2 source)
- Wrote four throwaway PowerShell scripts to read the source jar (largest cost) instead of `api`: it believed api was signatures only, and `api Entity.writeToNBT` stopped at 40 lines, before Forge's `ForgeData` line (1960) (category: tool output; the "(more: ...)" pointer at the bottom was missed)
- Guessed the save tag from memory ("PersistentNBT"); the game test caught it (category: model reasoning; AGENTS.md step 4 read as "never look at source first")
- Claimed status pointed at `.cache\last-check.log`: false; no kit version prints or contains that path
- Suggested, declined: init without ModItems/ModBlocks/BasicBlock (generators need them; harmless)
- Its own: two edits from memory instead of reading the file first; .NET API guesses in its scripts.

## General fix (would it help a different mod?)
- kit 0.5.0-beta.20: a member read longer than the budget shows its start, its end, and the lines in the skipped middle that name Forge (Forge patches: ForgeData, ForgeCaps, ForgeHooks, ForgeEventFactory). `api Entity.writeToNBT` now shows ForgeData/ForgeCaps and `api EntityPlayerMP.copyFrom` the PERSISTED_NBT_TAG copy, about 48 lines each. AGENTS.md step 4: verify names by compiling, but look up behavior and data layout (NBT keys, what a method saves or copies) with api before relying on it.
