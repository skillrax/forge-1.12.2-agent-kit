# Item transfer and bounded routing

Read for automated extraction, sided item ports or a small filter splitter. Use the pinned Forge 1.12.2 `IItemHandler` API. For multi-hop ducts, branches, configured sources/destinations and one shared budget, use [small item networks](item-networks.md). A fixed neighbor layout remains sufficient for local transfer; do not add graph machinery unless the feature needs it.

## Ports, buffers and filtering

For a machine that assigns inventory slots to six faces and actively pulls/pushes adjacent inventories, use [configurable machines](configurable-machines.md). Its starter shares these accounting/chunk rules and keeps role changes visible to held capability views.

Expose `CapabilityItemHandler.ITEM_HANDLER_CAPABILITY` only on intended faces. Read a neighbor's opposite face. Input views allow insertion but reject extraction; output views do the reverse. Return copies from capability inventory inspection rather than exposing mutable internal stacks. `ItemStackHandler` provides stack limits, simulation and NBT serialization; mark committed changes dirty through `onContentsChanged`. For a generic `<T> getCapability` implementation, use `CapabilityItemHandler.ITEM_HANDLER_CAPABILITY.cast(port)`, not `capability.cast(port)` on the untyped incoming argument.

State the physical directions, transfer cadence and capacities. A small extractor can hold one 16-item stack and move up to eight items every ten server ticks. A splitter can use one such slot per route. Reject incompatible stacks or excess items through the returned remainder. Tick only on the logical server; check loaded state before looking up neighbors and reacquire handlers rather than retaining references across unload/reload.

Choose simple player configuration: held-item click copies a one-item filter without consuming the held stack; empty-hand click shows the filter and buffer counts. State whether matching includes metadata or NBT. The fixed splitter proof matches item + metadata, ignores NBT, and assigns a route at insertion. Changing its filter affects later arrivals; existing buffered items keep their assigned route. The graph proof instead retries current destination filters without discarding owned items. Save the filter and every buffer in tile NBT. A blocked route retains its items; separate route slots do not guarantee that upstream head-of-line blocking is impossible.

## Transfer accounting

Simulation must not mutate inventory, item arguments or saved state. Check source extraction and receiver space before committing. Remove only the amount the receiver actually accepts; keep everything else in the source or an owned buffer. For example, pushing from a private buffer to a synchronous handler:

```java
ItemStack offered = buffer.extractItem(slot, rate, true);
ItemStack remainder = ItemHandlerHelper.insertItem(target, offered.copy(), false);
int accepted = offered.getCount() - remainder.getCount();
if (accepted > 0) buffer.extractItem(slot, accepted, false);
```

For pulling, simulate source extraction, simulate insertion into your buffer, then extract only the accepted count and commit it to that buffer. This assumes serial server execution and handlers honoring the simulation/commit contract. Never silently discard an unexpected committed remainder: preserve it in owned storage or safely return it. A third-party handler that changes results during commit needs a focused integration check; simulation does not make arbitrary handlers transactional.

## Chunk boundaries and small checks

Read [shared chunk-safety guidance](machines-and-energy.md#chunk-safety-and-dirty-marking) before using inventories near boundaries. A loaded neighbor does not mean its capability lookup or inventory writes are free of additional chunk reads.

For the pinned vanilla chest handler, the proof pauses access unless this horizontal area is loaded:

```java
if (tile instanceof TileEntityChest
        && !world.isAreaLoaded(target.add(-3, 0, -3), target.add(3, 0, 3))) {
    return null;
}
```

The reach covers adjacent double-chest discovery and comparator probes from either half. It deliberately permits a loaded chest to pause when nearby chunks are absent. This loaded-area example alone does not protect pending unload: for queued-sensitive networks, inspect existing chunks and reject `unloadQueued` throughout the same area, as described in [small item networks](item-networks.md). It is not a universal radius for modded inventories; do not force-load missing chunks to make a transfer succeed.

Build/check/export through the kit's normal interface. Add one short actual server extraction scenario with two item types, correct routes and per-item count conservation, plus a full-destination case that resumes after clearing space. Test port permissions, non-mutating simulation and saved buffers/filter. For cross-chunk automation, one focused actual unload/reload check is useful; use the optional recipe in the shared guidance rather than a default stress campaign. Client interaction and actual save/close/reopen require their own observations.

Evidence basis: the maintainer's Item Routing Proof has 15 server routing/backpressure assertions and nine actual partial chunk unload/reload cases after correcting indirect vanilla loads. Original gameplay/save-reopen and multiplayer were user accepted; the chunk patch has server evidence. Proof source and assertion mods are maintainer assets outside the recipient ZIP. This supports bounded routing, not universal inventories or large-network performance.
