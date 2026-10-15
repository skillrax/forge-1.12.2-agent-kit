# World block actions and placement context

Read when implementing a breaker, placer or source-fluid collector. Use the pinned Minecraft 1.12.2 / Forge sources: newer placement events and loot APIs differ. Accepted Block Actions gameplay demonstrates ordinary/silk harvesting, placement state/context, source/bucket accounting, backpressure and save/reopen. It is a bounded mechanism proof, not universal modpack compatibility. Its GUI policy overrides and vanilla allowlists are test controls, not recommended production defaults.

## Choose the semantics first

State the target, operation and consumption/result policy. Server-side single-step controls, a default pause state, target XYZ/outline, and visible failure/accounting status make a small proof reproducible. Reuse existing inventory/menu/tank patterns rather than building another subsystem. Persist contents/settings; decide whether activation resumes after reload (the accepted console restarts paused).

There are distinct choices for mining: strict harvest eligibility (refuse when the supplied tool cannot harvest), player-style breaking (which can destroy a block without normal harvested drops), or a named mod's custom hand/tool behavior. Define these separately. Hardness, time to break, permission to change the world, and eligibility for ordinary drops are different questions. Do not use namespace or ItemTool-only filters as universal eligibility rules: modded tools, special items and empty hands may have meaningful callbacks. A registry allowlist can bound an initial proof; broaden it only for requested behavior and actual integration evidence.

## Use the supplied tool and its enchantments

Production default: use the actual supplied ItemStack, including metadata, NBT, Silk Touch, Fortune and Unbreaking. Copy it for the actor/reservation stage; preserve the real stack until success. For example (names are application-specific):

```java
ItemStack held = toolSlot.copy();
actor.setHeldItem(EnumHand.MAIN_HAND, held);
boolean canHarvest = state.getBlock().canHarvestBlock(world, target, actor);
int silk = EnchantmentHelper.getEnchantmentLevel(Enchantments.SILK_TOUCH, held);
int fortune = EnchantmentHelper.getEnchantmentLevel(Enchantments.FORTUNE, held);
```

Do not remove or inject Silk/Fortune by default. The accepted console deliberately selected Ordinary/Silk/Fortune I–III on a temporary copy to demonstrate each path; its stored tool's enchantments stayed unchanged and actual Unbreaking/durability still applied. A policy override or Fortune cap is a separate product choice that must be explicit. These snippets are adaptation guidance, not an independently qualified unrestricted starter.

Inspect `PlayerInteractionManager.tryHarvestBlock` for the actual sequence: Forge break event/XP, item onBlockStartBreak, harvest eligibility, item onBlockDestroyed, removal with willHarvest, harvestBlock when eligible, then XP. Ordinary/silk drops and HarvestDropsEvent come through Block.harvestBlock; silk eligibility is block-sensitive and unsupported silk can fall back to ordinary behavior. Empty drops can be correct (ordinary glass is an example). Honor doTileDrops if retaining vanilla drop semantics. Spend real tool durability only when the selected action commits, and emit the destroy-item event if it breaks.

XP should come from the selected vanilla/Forge break path and BreakEvent, which can alter expToDrop; it is not an amount the player must manually configure. No vanilla XP-boost enchantment is required for testing. State whether XP becomes orbs, a saved ledger, a player grant, or is discarded, and ensure only one path credits it. The accepted fixture uses a saved counter; Silk ore gave zero XP. It has no redemption control and discards the ledger on ordinary machine breaking.

### Reservation and compatibility boundary

Reserve output before destructive work. The accepted fixture invokes supported vanilla harvestBlock once while the original state is intact, using Block's protected thread-local captureDrops through a Block subclass. Capture in try/finally, plan all output inserts from copies, and consume the planned result exactly once after removal. Full outputs leave the block, real tool, inventory and XP untouched. A failed reservation may still advance random draws and run harvest event listeners.

That ordering differs from PlayerInteractionManager (removal then harvest). It is not a general transaction for arbitrary blocks/listeners: Block capture catches Block.spawnAsEntity drops, not independently spawned entities, world mutation or unrelated side effects. Do not repeatedly call getDrops to simulate and reroll, or assume capture proves universal rollback. Nested capture must be rejected or explicitly supported. If selecting full player-manager parity instead, solve output ownership/backpressure for that pathway; simply calling tryHarvestBlock does not reserve a machine inventory.

A strict `!canHarvestBlock -> reject` policy intentionally prevents low-tier/hand breaking. Do not apply it to a product that promises a pack's special hand-mining results. Conversely, blindly calling harvestBlock despite false eligibility can create drops that a player would not receive. Identify the actual mod's item/block/event hooks and prove one requested integration with that mod installed. A Stoneblock-style hand-to-rock rule is not established by this vanilla proof.

Tile entities need their own lifecycle/content policy. The accepted console rejects **all** tile-entity targets before harvesting, including empty chests and dispensers; emptiness is irrelevant. Other designs can reject, drop contents, preserve a packed machine, or use normal player callbacks. Do not assume a block item retains inventory/NBT or that removing a container after manually extracting contents cannot duplicate them. Reject unsupported cases clearly until the intended path is demonstrated.

## Player item-use versus direct state

For player-style placement, prepare the actor's hand with the supplied stack and selected yaw/pitch, face and hit coordinates. In the pinned server API, `ItemStack.onItemUse` calls `ForgeHooks.onPlaceItemIntoWorld`; calling bare ItemBlock.onItemUse skips that wrapper. The wrapper captures block snapshots, emits cancellable PlaceEvent/MultiPlaceEvent and restores placement/item count on cancellation. ItemBlock supplies getStateForPlacement, placeBlockAt, onBlockPlacedBy and its tile-NBT merge behavior. RightClickBlock and use-item denial must be handled separately if bypassing the interaction manager. Decide whether block activation, onItemUseFirst, special items and multi-block placements are in scope.

Hit X/Y/Z, clicked face, actor horizontal facing, item metadata and item NBT can each affect placement. There is no universal placed-by-player bit. Representative accepted states: stairs face the actor's selected horizontal direction; side hits above/below 0.5 choose top/bottom stairs/slabs; ItemLeaves.getMetadata sets bit 4 and hence DECAYABLE=false. Raw leaf metadata without that bit gives natural decayable leaves. Leaves demonstrate persistent state; they do not establish arbitrary tile-entity transplantation.

Watch ItemSlab: it tries merging the face-adjacent slab before normal ItemBlock placement, so a nominal empty target can still mutate a neighbor. The accepted single-target console rejects that merge. Other special items can affect multiple cells; either deliberately support their footprint or reject them. Check replaceability, liquids, existing tile entities, collisions and the final affected positions; never report a neighbor action as a change to the visible target.

Direct placement deliberately chooses an IBlockState (for example getStateFromMeta) and omits getStateForPlacement/onBlockPlacedBy/item NBT merge. If honoring PlaceEvent, capture the replacement, temporarily set the state under world.captureBlockSnapshots, emit the event while the new state exists, restore on cancellation, and only then run onBlockAdded and markAndNotifyBlock. Preserve/clear capture state in finally and reject nested snapshots. Cancellation before any item/fluid spending is essential. This is deliberate machine placement, not full player parity; raw metadata may select another subtype, so it is not a survival default.

ItemBlock.setTileEntityNBT merges BlockEntityTag and restores the target coordinates; it does not universally transplant valid, safe machine state. Specify ownership, permitted NBT and activation before enabling it. The accepted fixture rejects BlockEntityTag/BlockStateTag and all placed tile entities.

## Actor, events and loaded-world access

Use a server-side survival FakePlayer context with an explicit identity policy. The accepted console creates a short-lived subclass per step, with stable UUID from dimension + machine position, no real owner impersonation, an otherwise empty inventory, selected hand/yaw, target-local position and sneaking=false. Pinned FakePlayer.getPositionVector returns the origin regardless of setPosition; override it when spatial context matters. Pinned FakePlayer also has a null connection: ForgeHooks.onBlockBreakEvent sends correction packets, so a packet-discarding NetHandlerPlayServer avoids the null dereference for an actor with no socket. This is not a real player session.

Check world/provider isBlockModifiable and canPlayerEdit; honor the cancellable events used by the selected path. Local BreakEvent, RightClickBlock and PlaceEvent behavior demonstrates those paths, not compatibility with every protection mod, owner permission rule, fake-player filter or modified callback. Do not promise protection compatibility from vanilla success.

Follow [loaded access and dirty marking](machines-and-energy.md#chunk-safety-and-dirty-marking). ChunkProviderServer.getLoadedChunk clears unloadQueued; scan its existing getLoadedChunks collection and reject queued/absent chunks before world/actor callbacks. Read existing tile maps and mark the existing owner Chunk dirty. Account for indirect reads in lighting, neighbors and item-specific callbacks. The proof conservatively guards a 32-block X/Z halo; that is a bounded vanilla safety choice, not a universal radius for third-party hooks. Do not cache neighbor tiles or force-load targets. Later normal vanilla fluid/gravity/decay ticks keep their engine semantics.

## Source fluids and Nether behavior

Use [fluid guidance](fluids-and-pickup.md) for tanks and registration. Water/lava source level 0 and the proof's owned BlockFluidClassic source correspond to exactly 1000 mB. Source collection and flowing-fluid collection are distinct semantics; the accepted fixture rejects flowing/falling levels. Simulate tank acceptance before source removal; place before draining; reserve container output before consuming input/volume. Keep bucket/container conversions exact and define supported containers. A custom fluid must be registered before constructing BlockFluidClassic, whose constructor creates a FluidStack.

Nether evaporation is a **placement-path choice**, not a universal cleanup after setBlockState. Pinned ItemBucket.tryPlaceContainedLiquid checks doesWaterVaporize, makes sound/smoke and returns success without creating water; normal bucket use consumes the water. Direct setBlockState bypasses that bucket logic. Choose bucket-style successful evaporation/consumption, machine reject-and-retain, or another explicitly intended policy. The accepted console chose rejection with unchanged 1000 mB. Do not call that an engine requirement or assume raw placement evaporates automatically. Its tank actions use explicit Break/Place events, not bucket ray tracing/FillBucketEvent.

Source regeneration may refill a collected water hole; collection still transferred only one source's 1000 mB. Vanilla water/lava and one Forge source were user accepted, with full/mixed tanks, blocked outputs, Nether policy and persistence. Arbitrary fluid blocks/containers require their actual drain/place callbacks rather than silently replacing world state.

## Bounded source lookups and evidence

Use tools/Read-MappedSource.ps1 for a needed member: PlayerInteractionManager.tryHarvestBlock, Block.harvestBlock/captureDrops/spawnAsEntity/removedByPlayer, ForgeHooks.onBlockBreakEvent/onPlaceItemIntoWorld, ItemStack.onItemUse, ItemBlock.placeBlockAt/setTileEntityNBT, ItemSlab.onItemUse, ItemLeaves.getMetadata, FakePlayer constructor/getPositionVector and ItemBucket.tryPlaceContainedLiquid. Select one bounded excerpt, not whole caches.

The accepted basis has 50 focused world assertions, a separate JVM persistence check, actual graphical GUI/world controls and user-passed steps 1–7 (including all three fluids and save/reopen). Further tests need a changed mechanism, concrete defect or requested integration; no unchanged broad campaign is required. Authoring QA and the semantic console remain outside the recipient ZIP; this reference conveys lessons without making its restrictive policies an unrestricted production template.
