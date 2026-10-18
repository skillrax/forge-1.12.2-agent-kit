# Tile entities (block entities)

Start with `./agent.ps1 new tile <name> [-Ticking] [-Inventory n]`. It writes `<Name>Block` (hasTileEntity/createTileEntity, right-click, drops inventory on break), `<Name>Tile` and the `GameRegistry.registerTileEntity` line. Read the generated files; this guide explains the rules they follow.

## Rules

- **Register** every tile class once: `GameRegistry.registerTileEntity(MyTile.class, new ResourceLocation(MODID, "name"))` (generated at `// kit:tiles`). Unregistered tiles crash on save; the server lint reports them as ERROR.
- **Constructor** must be public and take no arguments (the game recreates tiles from NBT).
- **Block state changes**: Forge replaces a mod tile (and loses its data) whenever its block's state changes, even a `lit` or `facing` property. Override `shouldRefresh(world, pos, oldState, newState)` to `return oldState.getBlock() != newState.getBlock();` (`new tile` does).
- **Save**: write every field in `writeToNBT` (call `super` first) and read it in `readFromNBT`. Call `markDirty()` after changing saved state, or the chunk may not save it.
- **Tick**: implement `net.minecraft.util.ITickable` (`update()` runs on both sides every tick, only while the chunk is loaded; docs/FORGE-1.12.md "What ticks"). Return early on the client unless you animate. A tile without `ITickable` never ticks: react in block callbacks or schedule a block tick instead.
- **Lifecycle**: `onLoad` once when added to the world (neighbors may not exist yet; defer neighbor lookups to the first `update`); `onChunkUnload` when its chunk unloads; `invalidate` when removed. Leave networks and caches in both of the last two. `world` and `pos` are not set in the constructor.
- **Client copy**: the client tile only knows what you send. `getUpdateTag` (chunk load) and `getUpdatePacket`/`onDataPacket` (changes) carry NBT; call `sync()` (`world.notifyBlockUpdate(pos, state, state, 3)`) after server changes the client must see. Do not sync every tick.
- **Neighbors**: `world.getTileEntity(pos.offset(facing))` can load chunks; check `world.isBlockLoaded(pos)` first, and never keep references to other tiles across ticks.
- **Far partners** (teleporters, linked storage, remotes): the rule above is for code that runs every tick. A one-off action a player triggers may load what it needs: `server.getWorld(dim)` loads the dimension, `getTileEntity(pos)` loads the chunk. Store the partner's position and dimension in your own NBT, never require it to be loaded already, and check it still exists after loading. Chunk-loading tickets (`ForgeChunkManager`) are only for things that must keep running unattended.
- **Inventories**: use `ItemStackHandler` and expose it as `CapabilityItemHandler.ITEM_HANDLER_CAPABILITY` (generated with `-Inventory`). Hoppers and pipes then work. Mark dirty in `onContentsChanged`.
- **Filtered slots**: in this Forge, `ItemStackHandler.insertItem` never calls `isItemValid`, so overriding `isItemValid` alone lets hoppers insert anything. Check in `insertItem`:

```java
public final ItemStackHandler fuel = new ItemStackHandler(1) {
    @Override public boolean isItemValid(int slot, ItemStack stack) { return stack.getItem() == Items.COAL; }
    @Override public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
        return isItemValid(slot, stack) ? super.insertItem(slot, stack, simulate) : stack;   // hoppers and pipes call insertItem
    }
    @Override protected void onContentsChanged(int slot) { markDirty(); }
};
```
- **Comparators**: output is on the Block (`hasComparatorInputOverride`/`getComparatorInputOverride`, guides/blocks.md); the tile's `markDirty()` makes comparators re-read it. That update probes neighbors, so chunk-edge machines without comparator output can skip it (guides/machines.md).

## Capabilities in one place

```java
@Override public boolean hasCapability(Capability<?> cap, EnumFacing side) {
    return cap == CapabilityItemHandler.ITEM_HANDLER_CAPABILITY || cap == CapabilityEnergy.ENERGY || super.hasCapability(cap, side);
}
@Override public <T> T getCapability(Capability<T> cap, EnumFacing side) {
    if (cap == CapabilityItemHandler.ITEM_HANDLER_CAPABILITY) return CapabilityItemHandler.ITEM_HANDLER_CAPABILITY.cast(inventory);
    if (cap == CapabilityEnergy.ENERGY) return CapabilityEnergy.ENERGY.cast(energy);   // energy = new EnergyStorage(capacity, maxIn, maxOut)
    return super.getCapability(cap, side);
}
```

Cast with the capability constant (`CapabilityItemHandler.ITEM_HANDLER_CAPABILITY.cast`), not the incoming `cap` argument. `side` may be null (internal access). Expose different views per side by returning wrapper handlers. Fluids: `CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY` with a `FluidTank` (guides/fluids.md).

## Menus

To open a GUI on right-click see guides/gui.md (container + screen + IGuiHandler).

## Rendering

Animated or custom-drawn tiles use a `TileEntitySpecialRenderer` bound in a client-only class: guides/rendering.md.

## Test

`t.place(pos, "mymod:name")`, `waitTicks(n)`, then `t.tile(pos, MyTile.class)` fields; inventories through `.inventory`. NBT round trip: `NBTTagCompound tag = tile.writeToNBT(new NBTTagCompound()); MyTile copy = new MyTile(); copy.readFromNBT(tag);`.
