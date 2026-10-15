# Item Routing Proof

Production source is `src/main`; assertions are in separate `src/evaluation` and `src/chunkEvaluation` source sets and are never packaged in the playable JAR. Built from active `kit/` via `tools/New-Checkout.ps1` into `.work/item-routing-1.0.1`; chunk checks use `.work/item-routing-chunk-check`. Kit identity stays in `kit/kit.json`; the proof has its own mod version. Original accepted exports are preserved.

Fixed layout, viewed from above (F3 shows compass direction):

```text
                         east (+X) →
 [source chest] [extractor] [splitter] [matching chest]
                               |
                               v south (+Z)
                         [other chest]
```

Every block is at the same height. Example relative coordinates: source `(0,0,0)`, extractor `(1,0,0)`, splitter `(2,0,0)`, matching chest `(3,0,0)`, other chest `(2,0,1)`. Directions stay fixed regardless of placement orientation. Use single chests and leave room above them.

Extractor: iron sides, hopper-textured west input, green east output. Splitter: gold body, hopper-textured west input, green east matching output, blue south other output. Both are in the Redstone creative tab. Survival recipes use `III / HRH / III`: I = iron ingot, H = hopper, R = redstone for extractor or comparator for splitter.

Right-click splitter with an item in your main hand to copy the filter without consuming it. Empty-hand right-click shows filter and both buffer counts in chat. Extractor right-click shows its buffer count. Default filter is cobblestone. Match uses exact item and metadata; ignores NBT. Existing buffered items retain their assigned output after a filter change. There is no GUI or rotation.

Transfer budget: up to 8 items per 10 ticks per output. Extractor holds up to 16 items; splitter holds up to 16 in each route. Input accepts insertion only; outputs permit extraction only. Other faces have no item capability. A blocked route retains its items. The other route can continue while the extractor is able to reach those items; a full extractor can stall upstream traffic. Breaking a block drops its buffered items, and its filter resets on replacement.

Chunk safety in 1.0.1: machine changes mark only their loaded chunk for saving, without vanilla comparator notifications (these machines have no comparator output). Vanilla chest access pauses if any chunk within three horizontal blocks of that chest is unloaded. This conservative guard covers Forge double-chest discovery and comparator updates from either half. A physically loaded chest may therefore temporarily stop receiving/extracting items while a nearby chunk is absent. The router retains items and resumes once the area loads. Custom inventory implementations can have their own behavior; arbitrary modpack compatibility is not claimed.

## Manual checklist

Install `item-routing-proof-1.0.1-srg.jar` in a Minecraft 1.12.2 / Forge 14.23.5.2859 profile's `mods` folder, replacing the old proof JAR rather than installing both. Load a test world, build the five-block layout above, and stay nearby so it ticks.

1. **Filter and both routes.** Hold dirt and right-click splitter: chat should show Dirt, and your held count must stay unchanged. Empty-hand click should show the same filter. Put 16 dirt in source slot 1 and 12 cobblestone in source slot 2. After about 5 seconds, matching/east chest must contain 16 dirt, other/south chest 12 cobblestone. Source and machine buffers should be empty: total 28. Check models and names render correctly.
2. **Blocked destination.** Empty destination chests, then fill every slot of the matching chest with 64 stone (27 full stacks). Add 40 dirt in source slot 1 and 8 cobblestone in slot 2. After about 6 seconds, expect source 8 dirt, extractor buffer 16, splitter matching buffer 16, and other chest 8 cobblestone. Matching chest keeps its stone. Test-item total remains 48; exclude the stone fillers. Empty-hand clicks show buffer counts.
3. **Actual save/reopen.** With step 2 still blocked, record filter and counts, Save and Quit, close Minecraft fully, relaunch and reopen the world. Dirt filter (different from default cobblestone), buffered items and source/output counts must remain. Remove the stone from the matching chest. After about 5 seconds, matching chest should contain exactly 40 dirt, other chest 8 cobblestone; source and buffers empty. Total remains 48.

All three steps were user accepted on 1.0.0, including actual save/reopen, with multiplayer reported working. The 1.0.1 patch has separate focused server checks; it does not yet have independent user gameplay acceptance. Report any issue with the patch; a repeat broad campaign is unnecessary.

## Recorded checks and reproduction

Evidence: `evidence/item-routing/1.0.1/summary.json`; original user acceptance: `evidence/item-routing/1.0.0/user-acceptance.json`. The focused build and 15-assertion routing/backpressure scenario are checked against the patched source. Chunk QA uses real provider unload/save/reload, Forge tickets and normal server tile ticks. Its nine cases cover each connection with either side absent, plus a corner with both destination chunks absent. It snapshots unloading chunks through the unload event's existing tile map, checks per-item totals without querying absent tiles, and observes server tick durations outside transitions. Actual JVM restart evidence remains the user's original manual acceptance; chunk reload is a different check.

To reproduce in a **new** source-only workspace: run active kit's `New-Checkout.ps1`, copy this fixture's `src/main` and `src/evaluation` into its `src`, set `settings.gradle` name to `item-routing-proof`, copy `build-overlay.gradle` and append `apply from: 'build-overlay.gradle'` to the workspace build. Set Gradle version to `1.0.1`, matching `@Mod` and metadata. Run workspace `agent.ps1 -Action check -Profile mod`, root `qa/integration/Invoke-RoutingServer.ps1 -Workspace <workspace>`, then workspace `tools/Export-Release.ps1`. For chunk checks use `src/chunkEvaluation`, `chunk-overlay.gradle` and `Invoke-RoutingChunkServer.ps1` instead, with timeout 180 seconds. Chunk overlay selects a fresh test-world name on every run; cleanup clears inventories before breaking fixture blocks to avoid dropped-item entities loading neighboring chunks. Both runners enable the local test server EULA, bind localhost with a temporary port and stop after their proof marker. No production QA code is shipped.

## Chunk-boundary source review

User accepted all three manual steps and reports multiplayer working: `evidence/item-routing/1.0.0/user-acceptance.json`. Partial chunk unload/reload has a separate server check in `src/chunkEvaluation`; current results are in `evidence/item-routing/1.0.1/summary.json`.

Reviewed Minecraft 1.12-era upstream source on 2026-10-13; this is code inspection, not an Ender IO runtime test or a claim about the user's installed build:

- Ender IO commit `9249b7e01fd73c7bc92a2fd60e768c1ef5b39fd3`: [AbstractConduit](https://github.com/SleepyTrousers/EnderIO-1.5-1.12/blob/9249b7e01fd73c7bc92a2fd60e768c1ef5b39fd3/enderio-conduits/src/main/java/crazypants/enderio/conduits/conduit/AbstractConduit.java#L384) destroys the network on chunk unload. `setNetworkBuildFailed` defers retry by 200 plus 0–99 ticks. [ConduitUtil](https://github.com/SleepyTrousers/EnderIO-1.5-1.12/blob/9249b7e01fd73c7bc92a2fd60e768c1ef5b39fd3/enderio-base/src/main/java/crazypants/enderio/base/conduit/ConduitUtil.java#L252) checks loaded state when following known conduit connections; unloaded connections abort network construction through a caught exception and mark affected conduits for delayed retry, explicitly to avoid redundant server work.
- Separate endpoint caveat: `NetworkedInventory.getInventory` reacquires the handler through [ItemTools.getExternalInventory](https://github.com/SleepyTrousers/EnderIO-1.5-1.12/blob/9249b7e01fd73c7bc92a2fd60e768c1ef5b39fd3/enderio-base/src/main/java/crazypants/enderio/base/capability/ItemTools.java#L338). That helper directly calls `getTileEntity` without its own `isBlockLoaded` guard. The network guards alone therefore do not establish that every external inventory lookup avoids loading chunks; destination-only unload needs its own actual test.
- Thermal Dynamics commit `c9b70757b1a63753f87d083c0d9106485e768827`: [TileGrid](https://github.com/CoFH/ThermalDynamics-1.12-Legacy/blob/c9b70757b1a63753f87d083c0d9106485e768827/src/main/java/cofh/thermaldynamics/duct/tiles/TileGrid.java#L326) watches weak references to neighboring chunks, refreshes connections on detected unloading, and [DuctUnit](https://github.com/CoFH/ThermalDynamics-1.12-Legacy/blob/c9b70757b1a63753f87d083c0d9106485e768827/src/main/java/cofh/thermaldynamics/duct/tiles/DuctUnit.java#L198) removes itself from its grid on unload and skips a tick pass when a neighbor unload is detected.

For this small proof, retain direct loaded-neighbor checks and fresh handler lookup. Test both conduit/machine unloading and endpoint-only unloading, no unintended chunk loading, bounded retry/work, item conservation and reload recovery. A graph rebuild/backoff system is relevant if a future proof introduces a cached network; it is not required by this fixed local layout.

The local test exposed indirect chunk loading through vanilla `TileEntity.markDirty` comparator updates and through Forge `VanillaDoubleChestItemHandler.get` adjacency lookup. A direct loaded-neighbor guard alone did not cover these paths. Pinned-source queries confirmed the extra reads; the patch avoids unnecessary machine comparator notifications and guards chest lookup/write reach. This lesson is supported by the retained pre-fix traces, not inferred solely from upstream transport mod architecture.
