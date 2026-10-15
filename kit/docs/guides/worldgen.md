# World generation, structures, dimensions

## Ores

`./agent.ps1 new ore ...` creates ModWorldGen (an `IWorldGenerator` registered in preInit) and adds `ore(block, dimension, veins, size, minY, maxY)`. Veins are offset +8 blocks into the chunk so generation stays inside already generated neighbors (prevents runaway chunk generation). Only new chunks get ores.

Other features in the same generator: any `WorldGenerator` (`WorldGenMinable`, `WorldGenTrees`, your own) called with a position at `chunkX * 16 + 8 + random.nextInt(16)`. Biome checks: `world.getBiome(pos)`, `BiomeDictionary.hasType(biome, BiomeDictionary.Type.HOT)`.

## Placing a structure safely

- Decide the rule first: which blocks count as ground, what may be replaced, slope and headroom. Check every footprint and support cell before changing anything; reject the spot otherwise (no partial structures).
- `world.getHeight(x, z)` counts leaves as height. Scan down to real ground instead.
- Templates: save with a vanilla structure block in a dev world, put the `.nbt` file in `assets/<modid>/structures/<name>.nbt`, load with `world.getSaveHandler().getStructureTemplateManager().getTemplate(server, new ResourceLocation(MODID, name))` and place with `template.addBlocksToWorld(world, pos, new PlacementSettings())` (server only, during generation or later).
- Generate shared features only from their origin chunk, so they are not regenerated over player edits.

## Crops

See guides/blocks.md (custom crops must return `EnumPlantType.Crop`). Mature drops: 1 crop item + 0-3 seeds.

## Dimensions

- Use a fixed, configurable dimension id and fail clearly on collisions; never pick a free id at runtime (saved worlds depend on it). `DimensionType.register(...)`, `DimensionManager.registerDimension(id, type)` with your `WorldProvider`.
- Teleport with `player.changeDimension(id, teleporter)` and an `ITeleporter` that finds or makes a safe spot (solid floor, two air blocks, inside the border). Respect cancellation.
- Store return positions per player in persisted NBT (guides/players.md). Decide bed/respawn behavior (`WorldProvider.canRespawnHere`, `getRespawnDimension`).

## Test

Run your generator directly in a game test: `new ModWorldGen().generate(random, chunkX, chunkZ, t.world(), null, null)` on a chunk near origin, then count placed blocks. Structure placement: call your placement method on a prepared area, including one spot that must be rejected.
