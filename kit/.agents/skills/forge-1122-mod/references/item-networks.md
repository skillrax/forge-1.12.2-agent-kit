# Small item-routing networks

Use for configurable extraction into a multi-hop duct graph with branches. For local handler contracts and fixed splitters, read [item transfer](item-transfer-and-routing.md); for residency and indirect vanilla reads, read the [shared chunk-safety section](machines-and-energy.md#chunk-safety-and-dirty-marking). Reuse existing inventories, menus and the [loaded-view helper](../assets/rf-network-starter/src/main/java/com/example/networkstarter/LoadedView.java), adapting its package and chest-area check. A separate full scaffold would duplicate that plumbing.

## Define ownership and policy first

Give nodes explicit Transit, Source and Destination roles, a chosen inventory face and a saved enabled flag. Transit connects ducts but never extracts. Source reads only its configured neighbor's opposite face; Destination inserts only into its configured neighbor. Private transport buffers need no external item capability. Do not discover arbitrary inventories along a path and start extracting from them.

State these choices in the feature checklist:

| Policy | Accepted small-proof choice |
|---|---|
| Connections | Six adjacent enabled ducts; paused ducts disconnect |
| Discovery | Fresh visited BFS, at most 128 resident ducts per component; oversized components pause |
| Cadence / budget | One world scheduler, eight items every ten ticks shared by all sources/components in that dimension |
| Destination order | Shortest duct hop count, then ascending X/Y/Z |
| Full / partial receiver | Skip full eligible receivers; use partial room and try the next eligible receiver |
| Source order | Rotate a stable source order between cadences; scan inventory slots in ascending order |
| Matching | Empty ghost = all; otherwise item + metadata + full NBT, with independent optional ignore flags |
| Buffer after edits | Retain items and saved origin; retry current reachable destination filters while enabled |

The world budget is deliberately conservative: additional nodes, loops, multiple faces and separate components cannot multiply throughput. A per-component policy is a possible adaptation, but must carry spent allowance across same-cadence merges/splits. Merely giving each tile a rate is insufficient. The proof does not establish equal fairness for many disconnected networks.

## Route once, retain everything not delivered

On the logical server's world END event, process the cadence once. Gather eligible loaded nodes, discover each connected component with a visited set, and process each source/owned buffer once. Compute shortest-hop destination order from the owner. Exclude configured source inventories from destinations and retain the original source position while a buffer is occupied. Deduplicate physical endpoints; vanilla double-chest halves can alias one inventory. A visited duct set alone does not prevent duplicate endpoint processing or source/destination feedback.

For each owner:

1. If its buffer is occupied, try delivery first and do not extract another stack. An owner changed to Transit/Destination can still drain its saved buffer; a paused or disconnected owner retains it.
2. Otherwise simulate extraction from the configured source slot, apply the source filter, then simulate eligible receiver space. Skip an unroutable slot so later items can proceed. Never extract just because a destination exists.
3. Reacquire the source handler and extract only the bounded planned amount. Place the entire actual extraction in private persisted storage before attempting receiver insertion. Keep actual metadata/NBT untouched.
4. Commit insertion using a copy. Compute accepted count from the returned remainder and remove only that count from the owned buffer. Less acceptance than simulation leaves the difference persisted, ready for fallback or a later cadence. Never discard it or assume simulation reserved an external inventory.

New extraction spends the shared allowance once, including any immediate delivery; later buffered delivery spends allowance again. This conservative accounting avoids both throughput multiplication and double-charging normal immediate transfers. A synchronous serial server pass plus conforming extraction/remainder contracts is assumed; simulation is not an arbitrary third-party transaction guarantee.

Keep ghost selection separate from real inventory slots. Copy a one-item ghost without consuming the cursor stack; validate menu distance, tile identity and button range on the server. Synchronize ghost slots and controls through the container. Save role, face, flags, ghost, buffer, origin and any scheduling state needed by the chosen policy. Configuration changes must never clear real transport inventory. Decide whether breaking drops the buffer or packs state, and tell the player how to recover blocked items.

## Residency, cost and focused evidence

Use existing loaded chunk collections and tile maps; reject absent, queued and invalid entries before traversal, capability access or mutation. Reacquire tiles/handlers after replacement or residency changes. Menu opening and `canInteractWith` need the same protection: `World.getTileEntity` can clear `unloadQueued` even after `isBlockLoaded`. Save-only dirty marking through an existing owner chunk avoids provider/comparator side effects for ducts without comparator output.

For vanilla chest lookup and writes, require every chunk within three horizontal blocks to be resident **and not queued**. Forge double-chest discovery and vanilla comparator probes can reach beyond the directly adjacent chest. This intentionally pauses some loaded chests near absent chunks; it is not a universal guard radius for modded inventories.

Fresh bounded traversal avoids a persistent topology cache and its invalidation protocol. Keep handlers local to the synchronous transfer. If profiling later shows repeated loaded-chunk indexing is expensive, consider one short-lived residency view per synchronous pass with fresh endpoint checks; do not retain it across ticks/unload or claim an unmeasured speedup. Bound work before adding caches, tiers or a larger logistics catalogue. Reuse the kit's check/export and mapped-source tools rather than adding a transport-specific build runner to recipient workflows.

Prove one branch with two item types and per-item conservation, one full/partial fallback that resumes, and a topology edit. A small loop/two-source check is justified by graph/budget risk. For cross-chunk transport, queue after provider ticking but before routing, observe the flag immediately after routing, then verify actual unload/absence and reload recovery without querying absent inventories. Use a correctly registered QA inventory block when checking committed remainders; a tile attached to a non-tile block can be removed by Minecraft and invalidate the test. Keep QA outside production JARs. Observe the GUI/results and perform normal save/close/reopen separately from NBT roundtrips. Stop once the affected behavior passes; no default topology matrix or stress campaign.

Evidence basis: Item Network Proof 1.0.0 passed a focused build, 162 real-server assertions and graphical container packets/results. The user accepted all three manual steps, including normal save/reopen, and called it a successful proof of concept. Commit acceptance of three from eight retained five in an owned buffer and later delivered all eight after filter edits and tile NBT replacement; a two-source loop conserved 48 with cadence delivery at most eight. Queued and actually unavailable destination chunks paused without unintended loads, then resumed. Maintainer source/checklist: `qa/fixtures/item-network/README.md`; acceptance: `evidence/item-network/1.0.0/user-acceptance.json`. These authoring assets are outside the recipient ZIP. This supports small routing graphs, not large-network performance, arbitrary modpack compatibility or recursive crafting.
