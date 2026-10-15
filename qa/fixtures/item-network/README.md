# Item Network Proof

Production: `src/main`. Isolated server/client checks: `src/evaluation`; these are excluded from the playable JAR. Test checkout: `.work/item-network-1.0.0`. Install the exported `item-network-proof-1.0.0-srg.jar` in a Minecraft 1.12.2 / Forge 14.23.5.2859 profile. Find **Network Item Duct** in the Redstone creative tab, or `/give @p itemnetwork:duct`. This is a creative-access mechanism proof; no survival recipe catalogue.

Every duct connects through its six faces to adjacent enabled ducts. Right-click opens its menu. Cycle the role between Transit, Source and Destination; cycle Face to choose the adjacent inventory. Transit ignores Face. A paused duct disconnects and retains its buffer. Role/face/filter controls are server-owned and saved. The filter is a ghost: pick an item onto the inventory cursor, left-click the blue filter slot to copy one without consumption, then return the cursor stack to your inventory. Right-click the ghost slot to clear it. Empty means all items. Both ignore flags default OFF: item identity, metadata and full item NBT must match; each toggle relaxes only the named comparison. Actual transferred stacks keep their metadata/NBT.

One world-level scheduler shares **8 items every 10 server ticks**, including all components and sources in that dimension. Hop count does not increase throughput. Sources rotate deterministically between cadences. For each source, eligible destinations are ordered by shortest duct hop count, then ascending X/Y/Z. Full destinations are skipped; partial room is used, with later eligible destinations as fallback. Only explicit source faces extract; ducts expose no external item inventory capability. Source endpoints are excluded from destinations, including adjacent vanilla double-chest halves. Each transfer deduplicates destination inventories. Source slot order is ascending; an unroutable item does not prevent searching later slots.

A fresh visited BFS admits at most 128 resident, enabled ducts per component. An oversized component pauses. No missing or queued chunk is loaded or unqueued. Tiles/handlers are reacquired rather than cached. Vanilla chest access additionally requires every chunk within three horizontal blocks to be resident and not queued, covering double-chest and comparator calls. This conservative rule can pause a loaded chest near an unavailable chunk. Only a short, small-network proof is claimed; arbitrary modded-handler compatibility, large-network performance and equal fairness across many disconnected networks are unproven.

Receiver space is simulated before extraction. The entire committed extraction becomes a private, saved source buffer before insertion. Any normal committed remainder stays owned there; no remainder is discarded. Extraction charges the shared budget once; later buffered delivery charges the budget again, conservatively. No new extraction occurs while that source buffer is occupied. Route/filter/role edits keep buffered items: an enabled owner retries against the current reachable destination filters, even after becoming Transit/Destination. The saved origin remains excluded from delivery. A paused/disconnected owner retains its buffer until re-enabled/reconnected. Players can recover items from its gray buffer slot; ordinary breaking drops the buffer and resets configuration. Handler simulation must be non-mutating and extraction/remainder contracts must be honored; this is not a transaction guarantee for arbitrary dishonest handlers.

## Three-step manual checklist

Build on one level, with single chests and room above them. East is +X, south is +Z. Relative coordinates below use the source chest as `(0,0,0)`:

```text
east →
[input] [source] [trunk] [branch] [destination A] [chest A]
                           |
                        [transit]
                           |
                      [destination B]
                           |
                        [chest B]     south ↓
```

| Block | Relative position | Menu |
|---|---|---|
| Source duct | (1,0,0) | Source; west; empty filter |
| Trunk duct | (2,0,0) | Transit |
| Branch duct | (3,0,0) | Transit |
| Destination A | (4,0,0) | Destination; east; dirt ghost |
| South transit | (3,0,1) | Transit |
| Destination B | (3,0,2) | Destination; south; cobblestone ghost |
| Chest A / B | (5,0,0) / (3,0,3) | ordinary single chests |

1. **Multi-hop branch and controls.** Pause the source while configuring. Check role/face buttons, copy a dirt filter without consuming the cursor stack, and toggle each ignore flag ON/OFF; finish with both OFF and the table settings. Enable all ducts. Put 16 dirt in input slot 1 and 12 cobblestone in slot 2. After about five seconds: A = 16 dirt, B = 12 cobble, input/buffers empty; total 28. The menu and item names should render correctly.
2. **Full / partial fallback.** Empty input and destinations. Clear B's ghost (all items). Fill A with 27 stacks of stone. Fill B with stone except its first slot holding 61 dirt. Put 12 dirt in input. After three seconds: input = 9 dirt; B's first slot = 64 dirt (3 new); A unchanged; buffers empty. Clear one slot in A. After three seconds: A = 9 dirt, input empty, B still 64 dirt. Excluding the original 61 dirt and stone, all 12 test dirt remain accounted for.
3. **Topology and normal save/reopen.** Empty chests, restore B's cobble ghost, and remove the trunk at (2,0,0). Supply 16 dirt + 12 cobble: they must stay in input. Record role/face/filters, then Save and Quit, close Minecraft fully, relaunch and reopen. Settings, missing trunk and all 28 source items must remain. Replace the trunk (default Transit): A receives exactly 16 dirt, B 12 cobble, input/buffers empty. Report those three results for acceptance.

## Evidence and reproduction

Current exact results and limits: `evidence/item-network/1.0.0/summary.json`. Automated checks use real server ticks for branch delivery, partial/full fallback, trunk removal/reconnection, a small two-source loop with per-item accounting and the shared budget, source/destination feedback exclusion, buffer/config/origin serialization, and queued/actual destination-only chunk unload/reload. A QA-only receiver accepting less at commit exercises the specific remainder-retention risk; it does not establish third-party compatibility. NBT replacement and chunk reload are distinct from a normal application restart. The user reported all three manual steps passed, including normal save/reopen, and accepted this as a successful proof of concept: `evidence/item-network/1.0.0/user-acceptance.json`. The installed JAR hash was not independently observed.

To reproduce: create a new checkout with `kit/tools/New-Checkout.ps1`, copy this fixture's `src/main`, `src/evaluation` and `build-overlay.gradle` into it, append `base.archivesName = 'item-network-proof'` and `apply from: 'build-overlay.gradle'` to its build, and keep mod/build/metadata version aligned. Use `agent.ps1 -Action check -Profile mod`, `qa/integration/Invoke-ItemNetworkServer.ps1 -Workspace <checkout>` and `Invoke-ItemNetworkClient.ps1` (180-second timeouts). The overlay selects a fresh disposable server world and binds localhost/temporary port. The graphical check uses normal container packets, saves six screenshots, and exits cleanly. Inspect screenshots as well as assertions. Never build into a running-client checkout. Export through its `tools/Export-Release.ps1`. Accepted lessons are packaged in `kit/.agents/skills/forge-1122-mod/references/item-networks.md`; guidance-only promotion/ZIP checks: `evidence/item-network-kit/summary.json`.
