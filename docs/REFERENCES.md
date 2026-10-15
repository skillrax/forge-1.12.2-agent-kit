# Maintainer lookup routes

Recipient knowledge lives in `kit/docs/` (index: kit/docs/INDEX.md). Accepted proof projects stay in `qa/fixtures/` as history and worked examples.

| Need | Location |
|---|---|
| Recipient workflow and rules | kit/AGENTS.md, kit/docs/GENERATORS.md, kit/docs/TESTING.md, kit/docs/FORGE-1.12.md |
| Topic guidance | kit/docs/guides/*.md (one per topic) |
| Copyable larger mods | kit/examples/machine, configurable-machine, rf-network, static-model |
| Compiled, tested code behind every guide sample | qa/fixtures/guide-samples (run qa/integration/Test-KitSamples.ps1) |
| Generator behavior tests | qa/fixtures/generators/GeneratorTests.java |
| Runner, diagnostics, generators (code) | kit/agent.ps1, kit/testing/Diagnostics.ps1, kit/testing/Test-DevEnvironment.ps1, kit/tools/Initialize-Mod.ps1, kit/tools/New-Content.ps1 |
| Check harness (lint + game tests) | kit/testing/harness/src/kit/gametest/ |
| API index format | kit/testing/testing.gradle, task kitApiIndex |
| Installed-artifact testing | kit/docs/INSTALLED-TESTING.md (recipient), docs/INSTALLED-TESTING.md (design) |
| Which mechanisms have proof | docs/CAPABILITIES.md |
| Energy proof lessons | docs/ENERGY.md, qa/fixtures/energy |
| Proof source for a mechanism | qa/fixtures/<name>/README.md (item-routing, item-network, rf-network, fluids, block-actions, automated-crafting, configurable-smelter, model-statue, animation-effects, player-menu, workshop, clockwork-*, aegis-apple) |
| Packaging | packaging/README.md, packaging/files.json |
| Older history | archive/ in the original workspace (docs/FORK.md); read one named file only for a concrete question |

Search a named directory or symbol rather than reading whole fixtures.
