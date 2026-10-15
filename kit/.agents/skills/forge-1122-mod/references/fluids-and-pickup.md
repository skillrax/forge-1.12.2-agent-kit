# Fluids and machine pickup

Read for Forge fluid tanks/transfer, a placeable custom fluid, or a wrench that carries machine state. These are the pinned 1.12.2 APIs, not the newer fluid registry or capability APIs.

For source collection/pouring, exact bucket/world accounting and bucket-style evaporation versus machine rejection in the Nether, use [world block actions](world-block-actions.md).

## Tanks, recipes and transfer

Use `FluidTank` and expose `CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY` as `IFluidHandler` on the intended faces. Input/output wrappers may share one tank while allowing only fill or drain. Filter fluid type as well as capacity; make `IFluidTankProperties` report the same permissions. Bucket clicks can use `FluidUtil.interactWithFluidHandler`. Register a fluid before adding its bucket with `FluidRegistry.addBucketForFluid`; enable the universal bucket early (for example in the mod class's static initializer).

Run machine updates on the logical server. A recipe should reserve the entire fluid output or fluid cost and item output space before advancing/spending. A full tank or full output must retain inputs/fluid. Mark committed tank changes dirty (a `FluidTank.onContentsChanged` override is useful) and save the tank, items and progress in tile NBT. Simulated `fill`/`drain` calls must leave them unchanged.

A straight transfer reads the neighbor's opposite face and checks `world.isBlockLoaded` before looking up the destination. Simulate an offer and receiver acceptance, commit the accepted fill, then drain only the amount actually delivered from the source. Retain the remainder if the receiver is full. This assumes synchronous handlers without intervening changes; a more general network or third-party adapter needs its own accounting design. State rate, capacity and port directions rather than implying automatic routing.

Check [shared chunk-safety and dirty-marking guidance](machines-and-energy.md#chunk-safety-and-dirty-marking) for indirect chunk reads inside notifications and handler calls. The item proof exposed vanilla dirty-marking side effects; this lesson does not retroactively certify the fluid proof's partial chunk-unload behavior.

## Viscosity, timing and distance are distinct

Pinned `BlockFluidBase` copies `fluid.viscosity / 200` into its tick rate during construction. Set fluid viscosity before constructing the block. `setTickRate` can then explicitly override the scheduling interval; later changes to the Fluid object do not automatically update the block's copied value. `setQuantaPerBlock` controls classic horizontal decay/reach, not viscosity.

For example, the accepted Biting Brine uses viscosity 4000, `setTickRate(20)` and `setQuantaPerBlock(4)`. On a flat uninterrupted floor, expect three horizontal cells beyond one source, roughly one scheduled advance per second at 20 TPS. Downward flow can reset the horizontal level and travel farther. `BlockFluidClassic` defaults `canCreateSources` to false. Disable random ticks if the proof needs the configured scheduled cadence to be observable; block addition and neighbor changes still schedule updates.

World rendering needs the fluid block's custom state mapper and a `forge:fluid` model using the registered fluid name. Keep model code in a client-only subscriber. Tinting vanilla fluid textures is a simple option for an original fluid. Contact damage belongs on the server, with a deliberate cadence and a check against the fluid's filled height; normal player immunity/hurt rules still apply.

Verify unfamiliar behavior with bounded `tools/Read-MappedSource.ps1` calls: `BlockFluidBase` constructor, `setTickRate`, `setQuantaPerBlock`, `onBlockAdded`; `BlockFluidClassic.updateTick`; `FluidTank.readFromNBT`.

## Wrench pickup and the empty-tank merge trap

Use `Item.onItemUseFirst` for a sneak-right-click wrench: it runs before held-item sneaking suppresses ordinary block activation. Check edit permissions and mutate only on the server. Return SUCCESS on a recognized client-side interaction so it follows the normal server item-use path. A tool that only changes `Block.onBlockActivated` can miss this interaction.

Serialize the tile state into the dropped machine ItemStack's `BlockEntityTag`, omitting the old `id`, `x`, `y` and `z`. Vanilla `ItemBlock.placeBlockAt` calls `setTileEntityNBT`, merging that tag into the newly created tile and replacing its coordinates. Carry fluid, inventory and progress together; suppress loose inventory drops only during wrench removal so one packed machine does not duplicate its contents. A transient removal flag must not be serialized. Ordinary harvest should return an untagged machine item and follow the chosen loose-inventory/fluid-loss policy. Packed items can have a per-stack limit of one while fresh machines remain stackable.

**Observed failure:** the fresh tile's empty tank serializes as `{Empty:""}`. Vanilla's deep merge retains that marker alongside the carried `FluidName`/`Amount`. `FluidTank.readFromNBT` sees `Empty` and discards the fluid even though other machine fields restore. One verified fix when reading this tank compound is:

```java
NBTTagCompound tankData = nbt.getCompoundTag("Tank").copy();
if (tankData.hasKey("FluidName", 8)) tankData.removeTag("Empty");
tank.readFromNBT(tankData);
```

This preserves a genuinely empty tank's marker while giving explicit fluid data precedence in a merged placement tag. Another valid design is a separate item state tag with explicit restoration instead of vanilla merging; use one coherent approach. A tile-only NBT roundtrip does not exercise this placement merge.

## Bounded evidence

For transfer, check generated = stored + consumed in one short producer/pipe/consumer line and a relevant full/blocked case. For pickup, call the actual wrench and placement hooks, count packed versus loose drops, compare tank/items/progress after relocation, and check ordinary breaking places empty. Item NBT serialization is useful, but actual save/reopen and client rendering/interaction still need observation. Keep assertion classes outside production JARs.

The maintainer's Fluid Proof has server accounting and pickup checks plus user acceptance of production/buckets, world flow, contact damage, server login and single-player restart persistence, and packed-machine relocation/restart. This supports these representative mechanisms, not branching networks, arbitrary handlers or broad modpack compatibility. The proof/QA sources are authoring assets and are intentionally outside the recipient kit.
