# Changelog

## 0.5.6 (2026-10-21)

From a large Claude-built port (about 760 files, 57 game tests):
- Game tests: past 36 tests, test areas were reused, and a later test could clear a `-Restart` test's area before the save (it looked like "contents lost after restart"). Restart tests now keep their own area; a reused area is also cleared of entities. Step labels over 96 characters are shortened instead of failing the test.
- `check game` summary counts: `tests 57/57 passed, 0 WARN`.
- `check server` fails on network channel names over 20 characters (clients on LAN or dedicated servers disconnect; single player hides it). `check` warns on a `GuiContainer` screen that never calls `drawDefaultBackground()` (no dimmed background; JEI draws over it).
- `export -Replace` overwrites an already exported jar of the same version that nobody has yet.
- New guide: guides/compat.md (JEI, Baubles and other optional mods: compile-only dependencies, optional code, how to test). Facts added: channel name and packet size limits, `ItemStackHandler.insertItem` skips `isItemValid`, blockless fluids need their sprites stitched, `renderItem(stack, TransformType)` turns blending off, tooltip lines after the first start with `§7`, equip sound from `setHeldItem`, removing XP, `SpecialSpawn` for spawn eggs.

## 0.5.5 (2026-10-20)

- Restart tests report the checks of both halves ("3 assertions, 40 ticks, across a server restart") instead of "0 assertions".
- `new test <id> -Restart` on an existing test says how to add `afterRestart` by hand.
- Docs: `check client` opens no screens (GUIs, Mods > Config, HUDs), so say those are untested; agents that can view images look at the screenshots; player persisted data also survives a death without keepInventory.

## 0.5.4 (2026-10-20)

- Guide fixes found by the new hidden acceptance tests: a command for everyone needs `checkPermission` returning true (level 0 alone refuses non-ops on a server); the ore dictionary has no coal item entry (use `Items.COAL` or `TileEntityFurnace.isItemFuel`); swords, hoes and shears are not `ItemTool` (use `isDamageable` / BREAKABLE for "any tool").

## 0.5.3 (2026-10-19)

- `check client`: starts a real client in a flat world, fails when a block state or item renders purple-black (missing model, blockstate variant or texture), warns on missing names, and saves screenshots of every block and item for you to look at (paths printed). AGENTS.md makes it part of "done".

## 0.5.2 (2026-10-19)

- Restart tests: a game test can add `afterRestart(TestContext t)` (`new test <id> -Restart`). After `plan()` passes, `check game` saves the world, stops the server, starts it again on the same world and runs `afterRestart` in the same area, so tile contents, block states and saved data are checked across a real restart.

## 0.5.1 (2026-10-19)

- Docs for people, not just agents: a "Your first mod" walkthrough in README.md, KNOWN-ISSUES.md (limits, local-model settings, how to report a problem), this changelog, and how to remove the kit (SETUP.md).

## 0.5.0 (2026-10-19)

First release for sharing. MIT license for the kit's own files.

- `agent.ps1` (PowerShell) and `./agent` (Bash): `status`, `init`, `new`, `check`, `api`, `batch`, `version`, `export`.
- Generators: item, block, ore (with world generation), food, tools, armor, recipe, smelting, tile entity, mob, game test. Each one writes code, assets, translations and a matching automatic check.
- Checks: compile with 1.12.2 name hints; dedicated server start with an asset lint (models, textures, blockstates, translations, recipes); behavior tests (`@GameTest`) inside a real server, with area dumps on failure and STUCK/FLAKY detection.
- `api`: signatures and source of any Minecraft/Forge 1.12.2 class, from the pinned mappings.
- Guides for blocks, items, tile entities, fluids, redstone, crops, entities, events, enchantments and effects, GUIs, networking, players, worldgen, rendering and more; every code sample is compiled and tested.
- Field-tested on about 25 unseen mod requests with Claude Code and a local model (Qwen3.8-Flash-Next via OpenCode).

Earlier betas (0.5.0-beta.1 to beta.28) were private test builds.
