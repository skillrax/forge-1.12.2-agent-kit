# Energy networks: cables, generators, storage

Copy from `examples/rf-network` (source: `src/main/java/com/example/networkstarter/`): solid/lava fuel generator with an upgrade, bufferless six-way cables, configurable storage and an adjustable sink, each with a GUI. Merge needed classes into your mod and rename package, modid `networkstarter`, registry/resource paths and the packet channel together.

| Change | File |
|---|---|
| Rates, capacities, default faces, fuel costs | `NetworkSettings.java` |
| Fuel selection, item/lava reserves, upgrade | `GeneratorTile.java` |
| Shared per-tick limits | `TickLedger.java` |
| Routing: path/endpoint simulation and commit | `Network.java`, `LoadedView.java` |
| Storage faces and saved RF | `StorageTile.java` |
| Demand, numeric controls, packets | `SinkTile.java`, `ControlPacket.java`, `MachineContainer.java` |
| Cable connections, screens | `CableBlock.java`, `MachineScreen.java` |

Defaults: generator 1,000 RF/t (2,000 upgraded); cable 60,000 RF/t; storage 2,000,000 RF, 60,000 in / 20,000 out per tick; sink 0-100,000 RF/t.

## Design rules the example follows

- **No energy in cables.** Each transfer finds a path through loaded cables, simulates every cable and endpoint allowance (counting the same tile reached by two faces once), then commits; actual acceptance decides what is spent. Removing a cable cannot destroy energy.
- **Budgets are shared.** A cable's per-tick allowance covers all directions and sources; storage output covers both pushing and external extraction. Wrappers, simulations, rebuilds or replacements must not create extra allowance. The ledger resets when the world tick changes.
- **Exact fuel.** Burn time comes from the furnace fuel registry (lava buckets excluded; lava tank 1,000 base RF per mB). Costs are integer units: 4 per RF, 3 per RF upgraded (double output for 1.5x fuel per tick = 0.75x per RF). Keep fractional credit; never round per request or use floating point; no fuel is spent when nothing accepts.
- **Undelivered energy returns** to the source (RF or fuel credit) if a receiver accepts less at commit.
- **Loaded means resident and not queued for unload**; `LoadedView` reads `getLoadedChunks()` and existing tile maps without loading anything (see guides/machines.md, chunk safety).
- Large numbers sync as two unsigned 16-bit halves; numeric input uses a validated custom packet (server checks container, window id, distance, range).

Interop: native Forge Energy (`CapabilityEnergy.ENERGY`) only. Ender IO/Thermal/CoFH-specific behavior is untested.

## Test

One exact accounting scenario (generated = stored + consumed), one blocked/backpressure case and one topology change (break a cable mid-run). Server ticks only; GUIs need the client.
