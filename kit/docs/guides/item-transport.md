# Item transport: extractors, filters, duct networks

Use Forge `IItemHandler` (`CapabilityItemHandler.ITEM_HANDLER_CAPABILITY`). For a single machine with configurable faces see guides/machines.md.

## Moving items correctly

- Expose handlers only on intended faces; read a neighbor through its **opposite** face. Input views allow insert only, output views extract only. `ItemStackHandler` gives limits, simulation and NBT, but its `insertItem` never calls `isItemValid`: override `insertItem` to check it, or hoppers and pipes bypass your slot rules.
- Never lose items: simulate, then commit only what the receiver accepts.
- Vanilla hoppers pull from the block above through its DOWN face and push into the block they face through the opposite face (a hopper above your machine inserts via UP; one below extracts via DOWN): one item per 8 ticks, none while powered.

```java
ItemStack offered = buffer.extractItem(slot, rate, true);                       // simulate
ItemStack remainder = ItemHandlerHelper.insertItem(target, offered.copy(), false);
int accepted = offered.getCount() - remainder.getCount();
if (accepted > 0) buffer.extractItem(slot, accepted, false);                     // commit what moved
```

- Pulling: simulate the source extraction, simulate insertion into your buffer, then extract and insert the accepted count. An unexpected committed remainder must be kept (owned buffer) or dropped as an entity, never deleted.
- Simulation must not change the inventory, the stack argument or saved state. Third-party handlers that change results between simulate and commit are not made safe by simulation.
- Tick on the server; check loaded state before neighbor lookups; re-acquire handlers each time.
- Say the rate and cadence (example: 8 items every 10 ticks, 16-item buffer).
- Filters: copy a one-item ghost from the held item without consuming it; state whether metadata and NBT must match. Save filters and buffers in NBT.
- Vanilla double chests read neighbors: require `world.isAreaLoaded(target.add(-3, 0, -3), target.add(3, 0, 3))` before access (guides/machines.md).

## Duct networks (multi-hop, branches)

Decide and write down these policies first; the tested design used:

| Policy | Choice |
|---|---|
| Node roles | Transit (connects, never extracts), Source (reads one configured face), Destination (inserts into one configured face) |
| Discovery | fresh breadth-first search per cadence, max 128 loaded ducts per component, oversized components pause |
| Budget | one world-wide budget: 8 items per 10 ticks shared by all sources (more ducts or loops cannot multiply throughput) |
| Destination order | fewest hops, then X/Y/Z; skip full receivers, use partial room, try the next |
| Source order | rotate sources between cadences; scan slots ascending |
| Matching | empty ghost = all; else item + metadata + NBT, with optional ignore flags |
| After config edits | buffered items stay owned and retry current destinations |

Each source with a full buffer delivers before extracting more. Otherwise: simulate extraction + filter + receiver space; extract only the planned amount into the private persisted buffer; insert a copy; remove only the accepted count. Deduplicate physical endpoints (two double-chest halves are one inventory), and exclude a source's own inventory from its destinations.

Residency: use existing loaded chunks only (reject absent or unload-queued chunks before traversal) and re-acquire tiles after replacement; `world.getTileEntity` can cancel pending unloads (guides/machines.md). Ducts without comparator output can use save-only dirty marking. Reuse `examples/rf-network/.../LoadedView.java` for residency checks.

## Test

Two item types through a branch with per-item counts conserved; a full destination that resumes after space frees; a config edit with items in flight. Count items in every inventory and buffer, not just the destination.
