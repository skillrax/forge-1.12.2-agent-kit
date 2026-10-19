# Series 3 baseline: Claude Code (Sonnet), kit 0.5.4, 2026-10-20

First run with the hidden acceptance tests (qa/acceptance/series3) scored by Run-Series.ps1 itself. Trials in `kit-trials/series3-claude-0.5.4`. The prompts now name the mod ids, so this is a comparison point for later kits, not a fresh test of unseen requests.

| Trial | own check game | Acceptance | Agent min | check client |
|---|---|---|---|---|
| S0 whereami | PASS | 6/6 | 14 | PASS |
| S1 glowberries | PASS | 9/9 | 4 | PASS |
| S2 pulse counter | PASS | 6/6 | 4 | PASS |
| S3 enchantments | PASS | 7/7 | 6 | PASS |
| S4 syrup + tank | PASS | 8/8 | 7 | PASS |
| S5 fetching wolves | PASS | 7/7 | 4 | PASS |
| S6 tool belt | PASS | 6/6 | 3 | PASS |
| S7 blast kiln | PASS | 5/5 | 9 | PASS |

54/54, no nudges or questions, 51 agent minutes. Every agent ran `check client` once (all PASS). The four prompts with "survives a reload" requirements (S2, S4, S5, S7) wrote `afterRestart` tests unprompted.

## Feedback acted on in kit 0.5.5 (verified)
- Restart tests printed "0 assertions" when all checks were in `afterRestart` (S5): the merged result now counts both halves and says "across a server restart".
- `new test <id> -Restart` on an existing test only said "refusing to overwrite" (S2): it now says to add `afterRestart` by hand or delete the file.
- `check client` opens no screens, so the Mods > Config screen stayed unverified (S3): TESTING.md says so. The Config button itself exists for @Config mods (FMLClientHandler 370: DefaultGuiFactory when ConfigManager.hasConfigForMod).
- S7 did not look at its own screenshots: AGENTS.md step 6 asks agents that can view images to open them.
- players.md: PlayerPersisted is copied on every respawn, a death without keepInventory included (EntityPlayerMP.copyFrom 1278-1281, outside the keepInventory branch) (S6).
- Read-Trial shows client checks as `c`.

## Left (single sightings)
- `new tile` placeholder cube model for non-cube blocks is not flagged by `check client` (S2).

## Human playtest (runClient, user, 2026-10-20)
All five mods tried work as requested; nothing found that the checks missed.
- S1 glowberries: plant, grow, harvest, eat for night vision.
- S3 enchantments: Config button in the Mods list opens the screen; changes apply after a restart (normal for @Config); Gathering works, and stops when turned off.
- S4 maple syrup: syrup is brown and flows slowly; the tank keeps its contents when broken and placed again.
- S6 tool belt: R cycles the tools through 3 slots; HUD row shown.
- S7 blast kiln: builds, smelts, takes hopper input and gives hopper output.
