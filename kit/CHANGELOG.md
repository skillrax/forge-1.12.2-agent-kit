# Changelog

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
