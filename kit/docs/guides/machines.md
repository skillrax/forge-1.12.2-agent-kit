# Machines: processing, Forge Energy, configurable sides

Working examples to copy from (each is a complete mod with its own `@Mod`; merge the classes you need into your mod, renaming package, modid and resource paths consistently, never add a second entry point):

| Example | Has |
|---|---|
| `examples/machine` | block + tile + 3-slot inventory, input/output slot rules, shift-click, GUI handler, client screen. No recipes or energy. |
| `examples/configurable-machine` | 4 processing slots + upgrade slot, FE storage, recipe table with furnace fallback, per-face item roles, auto pull/push, redstone mode, GUI buttons, hover diagnostics |

Configurable machine adaptation points (`examples/configurable-machine/src/main/java/com/example/configmachine/`): `MachineSettings` (capacity, rates, durations, default faces), `MachineRecipes` (entries, upgrade requirement, furnace fallback), `MachineTile` (inventory, accounting, face views, transfer, NBT), `MachineContainer` (slots, buttons, synced fields), `MachineScreen` (face selector, controls).

## Processing rules

- Keep recipe data (inputs and counts, outputs, duration, energy, chance) in recipe entries; select an entry by input. No item-specific branches in the tick loop.
- Each tick (server): check the output has room for **all** results first, then spend energy and advance progress. Full output = pause, keep progress, spend nothing.
- On completion consume the inputs and insert outputs together. Reset progress when the input changes to a different recipe; partial energy is not refunded.
- Progress, energy, inventory and settings go in NBT; `markDirty()` after changes.
- Window properties (container sync) are signed 16-bit: split values above 32767 into two fields.

## Forge Energy (RF/FE)

- Expose `CapabilityEnergy.ENERGY` with an `IEnergyStorage` (`net.minecraftforge.energy.EnergyStorage(capacity, maxReceive, maxExtract)`). RF is just the display unit; legacy CoFH `IEnergyReceiver` is a different API not implemented by FE.
- Separate internal generation/use from external permissions: input-only and output-only views can wrap one buffer.
- Push to a neighbor: get its capability on the **opposite** face, simulate extract + simulate receive, then commit only the accepted amount. Simulation must not change anything.
- Run on the logical server. Never force-load chunks to move energy.

## Configurable faces

Face roles in the example: 0 disabled, 1 main input, 2 secondary input, 3 main output, 4 secondary output; faces in `EnumFacing` order (DOWN, UP, NORTH, SOUTH, WEST, EAST) and world-relative. Capability views read the current role on every call, so held handlers never use stale permissions. Buttons use `sendEnchantPacket`/`Container.enchantItem` with validated ids; only the server changes settings.

## Chunk safety and dirty marking

Matters for anything that reads neighbors or other chunks (pipes, cables, machines at chunk edges):

- Check `world.isBlockLoaded(pos)` before `world.getTileEntity(pos)` on another block. Re-get neighbor tiles/capabilities each time; never cache them across ticks.
- In 1.12.2, `World.getTileEntity` goes through `ChunkProviderServer.getLoadedChunk`, which **cancels a pending unload** (`unloadQueued`). Networks that must not keep chunks alive should index `getLoadedChunks()` and read existing tile maps instead: `examples/rf-network/.../LoadedView.java`.
- `TileEntity.markDirty()` also updates comparators, which probes neighbor positions and can load an unloaded chunk. A machine with no comparator output can save without that:

```java
@Override public void markDirty() {
    if (world != null && !world.isRemote) world.markChunkDirty(pos, this);
}
```

- Vanilla double chests: Forge's `VanillaDoubleChestItemHandler` searches adjacent blocks without load checks. Before touching a chest, require the area around it loaded: `world.isAreaLoaded(target.add(-3, 0, -3), target.add(3, 0, 3))`.
- Visible on screen is not the same as loaded on the server: spawn chunks (within ~128 blocks of spawn) and player view distance keep chunks loaded.

## Test

Place the machine, insert inputs and energy directly (`tile.inventory`, `tile.energy.receiveEnergy`), `waitTicks(duration + 5)`, assert outputs, consumed inputs and energy. Add one blocked-output case. GUI layout needs the client.
