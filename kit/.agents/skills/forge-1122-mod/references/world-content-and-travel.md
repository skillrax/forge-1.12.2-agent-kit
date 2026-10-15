# Crops, structures and dimensions

For 1.12.2/stable39 world-content/travel features. Choose policies for the user's mod; accepted small-trial rules are examples.

## Custom crop lifecycle

BlockBush.getPlantType recognizes vanilla crop singletons. A custom BlockCrops subclass defaults to Plains even when ItemSeeds placement requires farmland. Explicitly return EnumPlantType.Crop for wheat-like crops. Inspect net.minecraft.block.BlockBush/getPlantType, net.minecraft.block.BlockFarmland/onFallenUpon and net.minecraft.block.BlockCrops/getDrops.

Exercise the real farmland-to-dirt neighbor notification/removal path for immature/mature crops and drops. Vanilla mature drops at Fortune 0 are one harvest and 0..3 seeds, not a guaranteed seed. Invalid bonemeal may return PASS: assert no success, consumption or mutation. Stub player assertions do not prove recipe networking/Forge handshake or client appearance.

## Complete placement before edits

World.getHeight is a light-opacity height map that can include leaves. A single full support block does not establish ground or support for a whole structure. Define allowed support, replaceable blocks, slope policy and headroom; inspect every footprint/support/occupied cell before edits. Reject without mutations. The accepted marker used level grass/dirt/stone over all nine footprint cells and three blocks of clear/removable low vegetation, rejecting canopy/trunks/water/slopes. Larger structures require their own policy.

Use actual population cases with canopy, unsupported corner and off-center obstacle alongside permitted terrain. Preserve independent ore generation. A changed oracle needs a fault restoring permissive behavior. Do not repair already populated worlds or change generation order merely to conceal an incorrect guard.

## Stable identity and safe travel

Use a stable configurable dimension/type ID, fail explicitly on collisions and match client/server configuration. Never auto-pick different free IDs per machine or change identity for saved worlds. Inspect net.minecraftforge.common.DimensionManager/registerDimension, net.minecraft.entity.player.EntityPlayerMP/changeDimension and net.minecraft.world.WorldProvider/canRespawnHere and getRespawnDimension. For fields/absent members use -Declarations then a bounded Line read.

Return points belong per player under persisted NBT. Before actual changeDimension/ITeleporter transfer, validate support, feet/head and border, and respect Forge travel cancellation. Search bounded safe alternatives or decline with feedback; avoid destructive platform repairs. Generate shared portal terrain only with its origin chunk so later entries preserve edits. State bed/respawn policy and scope support to players unless other entities are implemented/tested.

Check separate players, obstructed/lost returns, cancellation and dimension/return persistence across server JVMs. Dismount may move a player; restore QA start positions before unrelated checks. Only stub QA may clear invulnerableDimensionChange between transfers; real clients clear it through movement. Disk assertions cannot prove real login/death UI, handshake or chunk delivery; retain installed-client checks.
