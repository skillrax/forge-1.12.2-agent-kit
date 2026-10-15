# Read only for the current task

| Need | First location |
|---|---|
| Complex static model conversion/rendering | [recipient guidance](../kit/.agents/skills/forge-1122-mod/references/static-models.md); original starter and prepared-scene exporter alongside the skill. Accepted source-specific conversion: [model statue proof](../qa/fixtures/model-statue/README.md) |
| Moving custom model parts, saved animation clock and bounded client particles | [recipient guidance](../kit/.agents/skills/forge-1122-mod/references/animation-and-particles.md); accepted [Rotor + Crystal proof](../qa/fixtures/animation-effects/README.md), production `src/main`, isolated checks `src/evaluation` |
| Setup and prerequisites | [kit/START.md](../kit/START.md), then [dependencies](../kit/docs/DEPENDENCIES.md) |
| Check/status/error/export commands | [interfaces](../kit/docs/INTERFACES.md); [workflow](../kit/docs/WORKFLOW.md) for details |
| Project-owned installed-server scenarios and exact optional third-party JARs | [recipient workflow](../kit/docs/INSTALLED-TESTING.md); API under kit/testing/installed-driver; focused lifecycle/discovery checks qa/integration/Test-InstalledHarness.ps1 and Test-InstalledDiscovery.ps1; interaction reproduction Test-InstalledWorkflow.ps1 |
| Item registration and resources | [items](../kit/.agents/skills/forge-1122-mod/references/items.md) |
| Charge, inventory ticking, held-item NBT | [persistent items](../kit/.agents/skills/forge-1122-mod/references/persistent-items.md) |
| Entities, trades, player lifecycle, commands | [entities and players](../kit/.agents/skills/forge-1122-mod/references/entities-and-players.md) |
| Keybinding, standalone menu and server-owned saved player values | [recipient guidance](../kit/.agents/skills/forge-1122-mod/references/personal-menus.md); accepted [Personal Menu proof](../qa/fixtures/player-menu/README.md), production `src/main`, isolated checks `src/evaluation` |
| Crops, world generation, portals | [world content and travel](../kit/.agents/skills/forge-1122-mod/references/world-content-and-travel.md) |
| API/build failure | [repair](../kit/.agents/skills/forge-1122-mod/references/repair.md), then `kit/tools/Read-MappedSource.ps1` with exact class/member and bounded lines |
| Machine/menu/storage example | `qa/fixtures/workshop/src/main/java/dev/agentic/workshop/`: select `ProcessorTile.java`, `InventoryTile.java` or `WorkshopContainer.java` |
| Machine/menu starter and Forge Energy guidance | [recipient reference](../kit/.agents/skills/forge-1122-mod/references/machines-and-energy.md); optional `assets/machine-starter/src/main` under that skill |
| Saved-pattern crafting, registered recipes, remainders and NBT/metadata ignore | [recipient guidance](../kit/.agents/skills/forge-1122-mod/references/automated-crafting.md); accepted [Powered Crafter proof](../qa/fixtures/automated-crafting/README.md), production `src/main`, isolated `src/evaluation` |
| Starter dirty-marking/save check | `qa/fixtures/machine-starter-checks/README.md`; isolated source and `qa/integration/Invoke-StarterServer.ps1` |
| Automated item transfer, filter routing or partial chunk unload | [recipient reference](../kit/.agents/skills/forge-1122-mod/references/item-transfer-and-routing.md); shared chunk-safety section in the machine reference |
| Item-routing source, manual acceptance or chunk-boundary evidence | `qa/fixtures/item-routing/README.md`; select `src/main` or isolated `src/chunkEvaluation` only for the needed behavior |
| Multi-hop item networks, branches, shared budgets and owned remainders | [recipient guidance](../kit/.agents/skills/forge-1122-mod/references/item-networks.md); accepted [Item Network proof](../qa/fixtures/item-network/README.md), production `src/main`, isolated `src/evaluation`. Acceptance: `evidence/item-network/1.0.0/user-acceptance.json` |
| Accepted energy proof and its lessons | [proof notes](ENERGY.md); source in `qa/fixtures/energy/src/main/java/dev/agentic/energy/` |
| Bufferless RF networks, shared tick budgets and solid/liquid fuel economy | [recipient guidance](../kit/.agents/skills/forge-1122-mod/references/rf-networks-and-fuel.md) and its optional RF starter; accepted basis: [RF network proof](../qa/fixtures/rf-network/README.md); candidate checks: `evidence/rf-network-kit/summary.json` |
| Configurable machines, GUI face roles, active item pull/push and upgrade-unlocked recipes | [recipient guidance](../kit/.agents/skills/forge-1122-mod/references/configurable-machines.md) and its optional starter; accepted basis: [smelter proof](../qa/fixtures/configurable-smelter/README.md). Candidate checks: `evidence/configurable-machine-kit/summary.json` |
| Fluid/wrench implementation guidance | [recipient reference](../kit/.agents/skills/forge-1122-mod/references/fluids-and-pickup.md) |
| Accepted fluid/wrench proof, defaults and checklist | [fluid proof](../qa/fixtures/fluids/README.md); source in `qa/fixtures/fluids/src/main/java/dev/agentic/fluids/` |
| World block harvesting, actual tool enchants, player/direct placement and source fluids | [recipient guidance](../kit/.agents/skills/forge-1122-mod/references/world-block-actions.md); accepted [Block Actions proof](../qa/fixtures/block-actions/README.md), production `src/main`, isolated world/client checks `src/evaluation`. Acceptance: `evidence/block-actions/1.0.0/user-acceptance.json` |
| Charged item/furnace labels | `qa/fixtures/clockwork-labels/src/main/java/dev/agentic/clockworkbottle/`: select the relevant class |
| Package contents | [packaging](../packaging/README.md), `packaging/files.json` |
| Previous failure or acceptance | One named report under `archive/2026-10-12-pre-pivot/docs/`; raw `evidence/` only if necessary |

Search a named directory or symbol. Do not read whole examples, all references or giant JSON ledgers for orientation. Use diagnostic summaries before raw logs. Put reusable lessons in their relevant reference; add a route here only if needed.

Recipient docs describe existing tools. Older broad regression/fault wording does not expand the revised maintainer policy in [DEVELOPMENT.md](DEVELOPMENT.md).
