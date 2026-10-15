# Forge Energy and a recipe-machine pattern

Working source: `qa/fixtures/energy/src/main/java/dev/agentic/energy/`. This example uses the kit's pinned Forge 1.12.2 API without a CoFH dependency. RF is the display unit; interoperability is through `IEnergyStorage` and `CapabilityEnergy.ENERGY`, not a private energy counter alone.

Proof complete: server 1.1.0 processed both recipes sequentially in one powered processor: 4 cobble -> 4 gravel + 1 flint, then 4 stone -> 4 primary cobble + 2 extra cobble. 640 RF spent; 8000 generated = 7360 stored + 640 spent. User passed all four original 1.0.0 gameplay steps and confirmed both 1.1.0 recipes work. JAR/source: `releases/examples/energy-proof/1.1.0/`; evidence: `evidence/energy/1.1.0/` (server summary and separate user acceptance).

Read just the relevant class:

- `EnergyBuffer`: Forge `EnergyStorage` plus separate internal generation/spending. External receive/extract limits still apply. A simulation must report acceptance without mutation; committed changes mark the tile dirty.
- `GeneratorTile`: free demonstration production, EAST output, bounded storage and NBT. Fuel conversion is not demonstrated.
- `PipeTile`: WEST input/EAST output views over one buffer. Look up the neighbor's opposite face; simulate acceptance, commit only that amount, retain undelivered power. Avoid loading chunks to find destinations.
- `ProcessingRecipe` and `ProcessorTile`: match input against a recipe list, reserve primary and secondary output space, spend RF while progressing, consume input and emit outputs on completion. Draw the secondary chance once at completion. Full outputs pause; they do not spend RF or reroll.
- `ProcessorContainer`/`ProcessorScreen`: reuse Workshop slots/shift-click and window properties for progress, RF and selected-recipe duration. Display output/chance from the current recipe. Values fit the vanilla signed-short window-property channel; larger buffers/durations need another synchronization representation.

Recipe definitions are Java entries in `ProcessingRecipe.recipes()`, not external JSON. Both current recipes use 20 ticks at 4 RF/t; progress display and saved-progress clamp now derive limits from the selected recipe. Clear previous output types when switching, because incompatible existing stacks intentionally pause the next recipe.

New recipes, RF costs and transfer rates are ordinary extensions of this mechanism. Branching networks, routing, fluid handling and a named third-party energy integration need their own small proof when requested. A straight pipe does not establish arbitrary network behavior.

## Small verification route

Optional runner: `powershell -NoProfile -File qa/integration/Test-Energy.ps1`. Build, one live server line, conservation and blocked-output boundaries, QA exclusion, export. No fault injection or load campaign. Human checklist: `qa/fixtures/energy/README.md`.

Headless test-world pitfall: pinned `WorldServer.updateEntities` returns after 300 empty-player ticks unless persistent chunks are present. This proof stays below that limit. A stalled long QA run may be an idle-world fixture problem; inspect energy/progress counters before changing production ticking. NBT roundtrip checks establish serialization only, not a separate-JVM restart or graphical acceptance.

## Lessons for the next proof

- Core energy/recipe logic was straightforward; most wiring was block/tile registration, menu/proxy/models/resources. At proof time the recipient starter covered only an item/crafting recipe. Alpha7 now includes an optional six-class inventory/block/menu scaffold under the Forge skill's `assets/machine-starter/src/main`.
- I guessed `getChunkFromChunkCoords`; stable-39 uses `World.getChunk`. Use the existing bounded source helper before guessing mapped names. This was an implementation error, not incorrect documentation.
- Two live checks exceeded the empty-world update limit. Include RF/progress/input/output counts in initial failure logs; keep the example naturally ticked and short.
- A recipe-list proof should use two distinct entries in the same machine from the start. Output text, progress scaling and NBT limits must follow recipe data, not the initial sample.
- Setup/build, source lookup and JAR export worked. Alpha7 promotes the reusable guidance into [the recipient reference](../kit/.agents/skills/forge-1122-mod/references/machines-and-energy.md); these notes retain authoring evidence/lessons. The full energy example is not bundled into production or mandatory startup context.
- Bespoke source-ZIP assembly added work. Default a small proof to the exported JAR and source pointer; build a reusable source-bundle helper only if needed. No new framework or benchmark campaign is warranted.
