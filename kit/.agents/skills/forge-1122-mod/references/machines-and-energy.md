# Machines, menus and Forge Energy

Read for block inventories/menus, RF-powered processing or energy transfer. Inspect only the needed starter file; do not load every asset. The optional scaffold is `../assets/machine-starter/src/main` relative to this reference, or `.agents/skills/forge-1122-mod/assets/machine-starter/src/main` from the project root.

For tanks, custom fluid blocks or wrench pickup that carries machine state, use [fluids and pickup](fluids-and-pickup.md), including the placement NBT merge trap.

For automated item extraction and filter splitting, use [item transfer and routing](item-transfer-and-routing.md).

For saved crafting patterns, registered crafting recipes, returned containers and optional NBT/metadata matching, use [automated crafting](automated-crafting.md).

For world-breaking/placing machines, tool enchantments, placement callbacks and source-fluid actions, use [world block actions](world-block-actions.md). Its proof restrictions are not universal production defaults.

## Optional block/menu starter

For an empty mod, copy its `src/main` contents into project `src/main`. It provides one `machinestarter:machine` block, a persisted three-slot inventory, input/output slot rules, shift-click, server GUI routing and separate client rendering. It deliberately has no recipes, energy or ticking. For an existing mod, merge only the needed classes/resources instead of adding another entry point or replacing metadata.

| File | Adapt when needed |
|---|---|
| `MachineStarter.java` | IDs, block/item/tile registration, GUI routing, common proxy |
| `MachineBlock.java` | Activation, tile creation, inventory drops |
| `InventoryTile.java`, `MachineTile.java` | NBT inventory, input rules, requested processing/state |
| `MachineContainer.java` | Slots, shift-click, window-property synchronization |
| `ClientHooks.java` | Client proxy, item model, menu drawing |
| `resources/` | Metadata, translation, blockstate and vanilla-texture models |

Rename package, modid, registry/resource paths and proxy class strings consistently; keep version `1.0.0` aligned with Gradle or change all declared versions. The kit version is independent. The sample leaves right-hand outputs empty until requested processing logic is implemented; it is not a ready-made energy machine.

The scaffold has no comparator output and overrides inventory dirty marking to save its loaded chunk without unnecessary neighbor probes. If adding a comparator feature, preserve its updates through a separately guarded notification path; do not blindly copy save-only marking to machines that need comparator behavior.

## Recipe-driven processing

For GUI-configured item faces, active pull/push, redstone polarity or upgrade-gated two-input recipes, use [configurable machines](configurable-machines.md) and its optional working starter. The inventory/menu starter above remains the smaller option for basic menus.

Keep recipe input/count, primary/secondary outputs, duration, RF cost and chance in recipe entries. Select an entry by input; avoid item-specific branches in the tick loop. Reserve space for outputs before spending RF, then advance progress. On completion consume the specified input and emit primary plus one secondary chance draw. A conservative machine may reserve secondary space even when the roll could fail; state that behavior.

Output text, progress scale and saved-progress clamp must follow the selected recipe. Reset progress when switching input type. Clear incompatible previous outputs when switching recipes. Two distinct entries in the same machine are a small useful reuse check, not a demand to exercise every recipe variant.

## Forge Energy

RF display units alone do not establish interoperability. Expose `CapabilityEnergy.ENERGY` with `IEnergyStorage` on the intended faces. Forge `EnergyStorage` supplies bounded receive/extract/capacity behavior. Keep internal generation/spending separate from external port permissions. Mark committed changes dirty and save RF/progress in tile NBT; simulation must not mutate storage.

A pipe reads its neighbor's opposite face. Simulate source extraction and receiver acceptance, commit only the accepted amount, and retain undelivered power. Input/output views can share one buffer with different permissions. Do not force-load chunks to move energy; `isBlockLoaded` alone does not protect pending unloading (see below). For branching bufferless networks, exact fuel economy and shared tick budgets, use [RF networks and fuel](rf-networks-and-fuel.md) and its accepted optional starter. Simple rate/capacity/recipe variations reuse these mechanisms; named third-party adapters need their own evidence.

Run updates on the logical server. Keep GuiContainer/model/rendering imports in client-only classes. Vanilla window properties carry signed shorts: larger RF buffers/durations need split fields or another synchronization method.

## Chunk safety and dirty marking

Pending unloading is another boundary: pinned `World.getTileEntity` reaches `ChunkProviderServer.getLoadedChunk`, which clears `unloadQueued`. A guarded tile read can therefore cancel an unload already queued that tick. The RF starter's short-lived `LoadedView` indexes the provider's existing `getLoadedChunks()` collection, rejects queued/absent chunks and reads existing tile maps without provider loading calls; it also covers server-side cable-shape queries. Do not cache tiles across unload/replacement. Test queueing after provider ticking but before the mod routes, as well as actual absence. Even a QA `getLoadedChunk` observer can invalidate that test.

Client visibility is not server residency. Spawn chunks plus the player's loaded region can preserve a whole cable route; flight can reconnect it. F3+A reloads client renderers rather than server chunks. The accepted RF replay distinguishes an actually stopped far-from-spawn route from a fully resident near-spawn route. A render-distance or flight experiment without server state observation must not be reported as an actual unload.

`world.isBlockLoaded` before neighbor lookup is necessary but does not cover indirect reads inside later calls. In the pinned implementation, `TileEntity.markDirty` calls `World.updateComparatorOutputLevel`; its second-neighbor probe can read an unloaded chunk. Forge's `VanillaDoubleChestItemHandler.get` also searches adjacent blocks without checking each neighbor's loaded state. Both paths caused actual unintended chunk loads in the item-routing proof.

For a machine with no comparator output, save-only dirty marking avoids that extra work:

```java
@Override public void markDirty() {
    if (world != null && !world.isRemote) world.markChunkDirty(pos, this);
}
```

Pinned `World.markChunkDirty` checks that the owning chunk is loaded but reaches its loading accessor; when it is already queued, that read can clear the flag. Do not mutate a queued/unavailable machine. For queued-sensitive code, mark an existing owner chunk dirty through the same side-effect-free collection access rather than a provider lookup. For comparator-enabled machines, retain required notifications and guard every position they may inspect. Do not blanket-disable comparator behavior, change vanilla chests, or claim that one loaded-neighbor check makes arbitrary handlers safe. The item reference documents the proof's conservative chest-access guard. RF networks now have their own queued/actual-unload evidence; this does not automatically establish other handlers' safety.

Optional focused server check for a concrete cross-chunk feature:

1. Keep one side ticking using Forge chunk tickets, outside spawn and player-held regions. Release the other side's ticket and queue its chunk for unloading. Verify an actual unload event and absence from `ChunkProviderServer.chunkExists`, not just ticket removal.
2. Snapshot counts through the unloading chunk's existing tile map. Do not query absent tile entities to check inventory: the observation itself can load the chunk.
3. Wait briefly on normal server ticks. Check counts including the frozen snapshot, bounded buffers and continued chunk absence. On an unexpected load, capture a bounded call stack from `ChunkEvent.Load` to find the actual caller; exclude intentional setup/reload phases.
4. Reload deliberately, reacquire tiles/handlers, verify retained state and resumed delivery. Test either side of the relevant connection; a corner can cover independent destinations.

Use a fresh disposable test world and clear fixture inventories before cleanup. Loose item entities left by breaking previous fixtures can load neighboring chunks and contaminate attribution. Keep assertion mods outside production JARs. A short tick-duration observation can identify an obvious problem; startup PASS and lack of lag warnings do not establish a performance guarantee. A real modpack chunkloader integration is a separate check when that compatibility is requested.

For an unfamiliar call chain, use bounded pinned-source queries for `TileEntity.markDirty`, `World.markChunkDirty`, `ChunkProviderServer.getLoadedChunk`, `World.updateComparatorOutputLevel`, `VanillaDoubleChestItemHandler.get` and the handler actually being called. Graphs need topology invalidation or a bounded fresh loaded traversal; the optional RF starter rebuilds per offer instead of retaining a graph cache.

## Small check and known trap

Build with `agent.ps1 -Action check -Profile mod`; export with `tools/Export-Release.ps1`. Add a short gameplay/logging scenario for the new mechanism and one nearby failure case when useful, such as blocked output. Report input/output counts, progress and RF on failure. Startup PASS alone is not behavior proof; fault/load/lifecycle campaigns are optional for a specific need.

Pinned stable-39 calls the chunk lookup `World.getChunk`, not `getChunkFromChunkCoords`; verify unfamiliar names with `tools/Read-MappedSource.ps1`. A headless `WorldServer` stops entity/tile updates after 300 ticks with no players or persistent chunks. Keep a simple QA run below that limit, or deliberately provide a ticking fixture. Diagnose this before changing working production code. NBT roundtrip proves serialization, not actual JVM restart.
