# Fluids: tanks, custom fluids, wrench pickup

1.12.2 uses `FluidRegistry`, `FluidTank` and `CapabilityFluidHandler`, not the newer APIs.

## Tanks and transfer

- `FluidTank` exposed as `CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY` (`IFluidHandler`) on intended faces. Input/output wrappers can share one tank; filter fluid type and capacity, and make `getTankProperties()` report the same permissions.
- Bucket right-click: `FluidUtil.interactWithFluidHandler(player, hand, world, pos, side)`.
- Mark dirty on changes (override `FluidTank.onContentsChanged`), save the tank in tile NBT. Simulated `fill`/`drain` (`doFill`/`doDrain` false) must change nothing.
- Transfer: check `world.isBlockLoaded`, get the neighbor's opposite face, simulate the offer and the receiver's acceptance, fill what is accepted, drain only what was delivered.
- Recipes with fluids reserve all output space and fluid before progressing; full outputs keep inputs.

## A custom fluid

- Register the `Fluid` before creating its block: `FluidRegistry.registerFluid(fluid)`, then `FluidRegistry.addBucketForFluid(fluid)`; call `FluidRegistry.enableUniversalBucket()` early (static initializer of the main class).
- `BlockFluidClassic`: viscosity is copied into its tick rate in the constructor (`viscosity / 200`), so set viscosity first. `setTickRate(n)` overrides the interval; `setQuantaPerBlock(n)` controls how far it spreads (4 = three cells beyond the source on flat ground). `canCreateSources` defaults false.
- Textures: the `Fluid` constructor's still and flowing `ResourceLocation`s (`modid:blocks/syrup_still` = textures/blocks/syrup_still.png); vanilla `minecraft:blocks/water_still`/`water_flow` plus `setColor(0xFFRRGGBB)` needs no files.
- Rendering (client, in `ModelRegistryEvent`): every level state maps to one `forge:fluid` model:

```java
ModelLoader.setCustomStateMapper(ModBlocks.SYRUP, new StateMapperBase() {
    @Override protected ModelResourceLocation getModelResourceLocation(IBlockState state) {
        return new ModelResourceLocation(MyMod.MODID + ":syrup", "fluid");
    }
});
```
`blockstates/syrup.json`: `{"forge_marker":1,"variants":{"fluid":{"model":"forge:fluid","custom":{"fluid":"syrup"}}}}` (the fluid's registered name; no model file).
- Contact damage runs on the server with a cadence and a height check.
- Textures are only loaded through models (the fluid block, the universal bucket). A fluid without a block, drawn in a tank or GUI, shows the missing texture until you add its sprites in a client `TextureStitchEvent.Pre` handler: `event.getMap().registerSprite(fluid.getStill())`, same for `getFlowing()`.

## Wrench pickup keeping machine contents

- Use `Item.onItemUseFirst` (runs before sneaking suppresses block activation). Check edit permissions, act on the server, return SUCCESS on the client so the server path runs.
- Write the tile's NBT into the dropped stack's `BlockEntityTag` without `id`, `x`, `y`, `z`; vanilla `ItemBlock.placeBlockAt` merges it into the new tile. Suppress loose inventory drops only during wrench removal. Normal breaking returns a plain item.
- **Trap**: a fresh empty `FluidTank` saves `{Empty:""}`, and the merge keeps that marker next to the carried `FluidName`, so `readFromNBT` discards the fluid. Fix when reading:

```java
NBTTagCompound tankData = nbt.getCompoundTag("Tank").copy();
if (tankData.hasKey("FluidName", 8)) tankData.removeTag("Empty");
tank.readFromNBT(tankData);
```

## Test

Producer, pipe, consumer: generated = stored + consumed; a full-tank case. Pickup: call the wrench hook and placement, compare tank/items/progress before and after, and check normal breaking places an empty machine.
