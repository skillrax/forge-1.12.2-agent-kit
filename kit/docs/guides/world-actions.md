# Changing the world: blocks, contents, breakers and placers

Breakers, placers, fluid collectors. Decide the semantics first: target position, what counts as success, where results go, what happens when output is full (normally: do nothing and keep everything).

## Breaking with a tool

- Use the actual supplied tool stack (metadata, NBT, Silk Touch, Fortune, Unbreaking). Don't add or strip enchantments unless the user asked.
- A strict rule (`if (!state.getBlock().canHarvestBlock(world, target, actor)) reject`) prevents wrong-tool mining; a player-style rule may break without drops. Pick one deliberately; modded tools may have special behavior.
- Player sequence for reference (`PlayerInteractionManager.tryHarvestBlock`): BreakEvent (can change XP) -> item onBlockStartBreak -> canHarvest -> item onBlockDestroyed -> removedByPlayer -> harvestBlock (drops, HarvestDropsEvent) -> XP. Silk Touch eligibility is per block; empty drops can be correct (glass).
- Reserve output space before destroying: capture drops (Block's protected `captureDrops` via a Block subclass, in try/finally), plan inserts on copies, then remove the block and insert exactly once. Don't call `getDrops` repeatedly (rerolls). Drop capture only catches `spawnAsEntity` drops.
- Spend tool durability only on success. Decide where XP goes (orbs, stored, discarded) and credit it once.
- Tile entities: reject them unless you have a content policy (drop, pack, or normal player callbacks).

## Placing

- Player-style placement: put the stack in a FakePlayer's hand with chosen facing/yaw and hit position, call `stack.onItemUse(...)` (runs Forge place events and restores on cancel; bare `ItemBlock.onItemUse` skips that).
- Hit position, face, yaw, metadata and NBT all affect the placed state (stairs facing, slab top/bottom, player-placed leaves don't decay).
- `ItemSlab` may merge into a neighbor slab: reject or handle multi-cell items deliberately.
- Direct placement (`setBlockState`) skips placement logic; if honoring `PlaceEvent`, set the state under `world.captureBlockSnapshots`, fire the event, restore on cancel, before spending items.

## What `setBlockState` really does

`world.setBlockState(pos, state, flags)` (server) always calls the old block's `breakBlock` (a chest spills its items, a tile is removed) and the new block's `onBlockAdded`, whatever the flags. Flags only control notifications: 1 = neighbor updates (torches, redstone and sand react), 2 = send to clients, 4 = no client re-render, 16 = no observer updates; the 2-argument form is 3. Flag 2 alone still breaks chests.

- Removing a block next to an attached one (torch, ladder, sign, button) with flag 1 pops the attached one off as an item. Remove in an order that leaves no support missing, or don't send neighbor updates.
- `world.getTileEntity(pos)` creates a tile when the block has one and none exists. To look without creating: `world.getChunk(pos).getTileEntityMap().get(pos)`.
- Changing many blocks: check every position first (loaded, `isBlockModifiable`, `getBlockHardness >= 0`, a cancelled `BlockEvent.BreakEvent`), then change them all. A failure halfway leaves a half-built mess and possibly duplicated items.

## Moving blocks with their contents

The tile's position is saved in its NBT. Copy it as NBT, rewrite the position, then build the copy:

```java
tag.setInteger("x", p.getX()); tag.setInteger("y", p.getY()); tag.setInteger("z", p.getZ());   // TE position lives in its NBT
TileEntity te = TileEntity.create(w, tag);
```

`create` returns null when the tile's mod is missing: refuse before changing anything. `NBTUtil.writeBlockState`/`readBlockState` save a state; an unknown block name reads back as air, so check `Block.REGISTRY.containsKey` first.

Removing a block without `breakBlock` side effects (spills, drops, neighbor updates) means writing chunk storage directly:

```java
static void rawSet(World w, BlockPos p, IBlockState state) {
    Chunk c = w.getChunk(p);
    ExtendedBlockStorage[] sections = c.getBlockStorageArray();
    int y = p.getY() >> 4;
    if (sections[y] == Chunk.NULL_BLOCK_STORAGE) sections[y] = new ExtendedBlockStorage(y << 4, w.provider.hasSkyLight());
    sections[y].set(p.getX() & 15, p.getY() & 15, p.getZ() & 15, state);
    c.markDirty();
}
```

Then the tiles need their own steps, because neighbors (cables, multiblocks) react to tiles appearing and disappearing:

```java
for (TileEntity te : tiles) te.onChunkUnload();                        // 1: networks disconnect while blocks still exist
for (int i = 0; i < volume(size); i++) rawSet(w, at(min, size, i), Blocks.AIR.getDefaultState());   // 2: all air
for (int i = 0; i < volume(size); i++) w.removeTileEntity(at(min, size, i));                       // 3: drop tiles
```

Placing goes the other way: `rawSet` every state, `chunk.addTileEntity(pos, te)` for every tile, then `world.addTileEntity(te)` for each (that runs `onLoad` and adds it to the tick list, after all its neighbors exist). Raw writes skip lighting and sync: for each touched chunk, `Arrays.fill(c.getHeightMap(), 0); c.generateSkylightMap();`, then per position `w.checkLight(p); w.notifyBlockUpdate(p, s, s, 2);`. Run all of this from a click or command, or queue it to `WorldTickEvent` END, never inside a tile or entity tick (docs/FORGE-1.12.md). Moving entities too: guides/entities.md.

## Actor (FakePlayer)

Survival FakePlayer, stable UUID per machine (dimension + position), empty inventory except the tool, no real-player impersonation. Give it a packet-discarding `NetHandlerPlayServer` (`new NetHandlerPlayServer(server, new NetworkManager(EnumPacketDirection.SERVERBOUND), fakePlayer)`) because break events send packets. `FakePlayer.getPositionVector` returns the origin; override it if position matters. Check `world.isBlockModifiable` and honor cancellable events. Protection mods are not guaranteed compatible.

## Source fluids

A water/lava source = 1000 mB. Simulate tank acceptance before removing the source; place before draining when pouring. In the Nether, buckets evaporate water (`ItemBucket.tryPlaceContainedLiquid`), but `setBlockState` does not: choose evaporate-and-consume or reject-and-keep explicitly.

## Loaded world

Same rules as guides/machines.md: only act in loaded, not-queued chunks; don't force-load targets; neighbor updates and lighting can read further away.

## Test

Silk vs normal vs Fortune drops on ore, full-output rejection leaves block/tool/XP unchanged, placement orientation, and the Nether/fluid policy if relevant.
