# Current maintainer task

Objective: Make the kit efficient and reliable for AI mod authoring, down to small local models (branch claude/kit-refactor, kit 0.5.0-beta.2).

Result: Done and verified on this host.
- agent.ps1: compact text output (-Json for programs), compiler errors with 1.12.2 rename/similar-member hints, `api` lookup over an index of the mapped jar (client-only flags), init skeleton, `new` generators (item/block/ore/food/tools/armor/recipe/smelting/tile/test), version, export.
- check server: harness lints registered content vs lang/models/textures/blockstates/sounds/tile registration. check game: @GameTest scenarios in src/gametest.
- Docs: AGENTS.md ~600 tokens startup; INDEX + 20 guides; every guide sample compiled/linted/tested in qa/fixtures/guide-samples.
- Evidence: qa/unit/Test-AgentInterface.ps1, Test-TestingTools.ps1, Test-Packaging.ps1, qa/integration/Test-KitSamples.ps1 (generators 14 items/3 blocks; samples 17 items/5 blocks/entity/enchant/potion; all game tests PASS), extracted 0.5.0-beta.1 ZIP built a generated mod with check server PASS; 0.5.0-beta.2 (MCP removed) passes qa/integration/Test-KitZip.ps1.

Next action: Fresh-agent trial: copy kit/ (or the ZIP) to a short path, open it in Claude Code and in a local-model agent (32k context), give a plain request (e.g. "ruby ore, tools, armor and a ruby apple that heals"), record tokens, interventions and the check/gametest results. Then a human look in runClient at textures/GUI.

Blocker: None. Not verified: cold PC setup, graphical client, real local-model behavior. Historical qa/integration runners still target the old runner (see qa/README.md).
