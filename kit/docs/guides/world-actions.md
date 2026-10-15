# Machines that break, place or collect in the world

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

## Actor (FakePlayer)

Survival FakePlayer, stable UUID per machine (dimension + position), empty inventory except the tool, no real-player impersonation. Give it a packet-discarding `NetHandlerPlayServer` (`new NetHandlerPlayServer(server, new NetworkManager(EnumPacketDirection.SERVERBOUND), fakePlayer)`) because break events send packets. `FakePlayer.getPositionVector` returns the origin; override it if position matters. Check `world.isBlockModifiable` and honor cancellable events. Protection mods are not guaranteed compatible.

## Source fluids

A water/lava source = 1000 mB. Simulate tank acceptance before removing the source; place before draining when pouring. In the Nether, buckets evaporate water (`ItemBucket.tryPlaceContainedLiquid`), but `setBlockState` does not: choose evaporate-and-consume or reject-and-keep explicitly.

## Loaded world

Same rules as guides/machines.md: only act in loaded, not-queued chunks; don't force-load targets; neighbor updates and lighting can read further away.

## Test

Silk vs normal vs Fortune drops on ore, full-output rejection leaves block/tool/XP unchanged, placement orientation, and the Nether/fluid policy if relevant.
