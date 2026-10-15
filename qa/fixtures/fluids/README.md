# Fluid proof

Production source is `src/main`; original fluid assertions are isolated in `src/evaluation`, wrench assertions in `src/wrenchEvaluation`. The disposable build starts from active `kit/` using `tools/New-Checkout.ps1`; pinned dependencies are unchanged. The five original fluid gameplay steps are user accepted, including server logout/login and single-player restart persistence: `evidence/fluids/1.0.0/user-acceptance.json`.

## Wrench follow-up: 1.1.0

Replace the installed 1.0.0 JAR with `fluid-proof-1.1.0-srg.jar`; keep one version installed. Existing machine IDs and saves remain compatible. A Machine Wrench appears in Miscellaneous or can be crafted shapelessly from two iron ingots and a stick. It is reusable and has no durability cost.

Sneak-right-click any of the three machine blocks with the wrench (either hand) to drop one packed machine. It retains its fluid, stored input/output, active progress and counters. Packed machines stack to one and show fluid/item counts in the tooltip; fresh machines still stack to 64. Right-click placement restores the saved state at the new location. Ordinary breaking drops stored items separately and a plain machine item with no saved state; fluid is discarded. Creative breaking follows normal Creative drop rules. Wrench pickup also works in Creative and drops the packed machine.

Manual check for this addition:

1. Fill a disconnected Brine Maker with fluid and leave unused redstone in it. Obtain a wrench; sneak-right-click the maker, collect its single dropped item and inspect the tooltip. Place it elsewhere: the exact fluid and remaining redstone should return. Check a Clay Maker with stored clay/input too; those should travel inside it with no loose duplicate drops.
2. Break that relocated machine with a pickaxe in Survival. Stored items should drop separately; the machine item should have no packed tooltip. Place it: tank, stored items, progress and counters should all be empty/reset.
3. Save/reopen while carrying a packed machine, then place it and confirm its fluid/items survive. Try the wrench in the offhand on a pipe if useful. Do not repeat the accepted fluid-flow/damage campaign.

Focused server-world checks use the actual wrench item hook and actual ItemBlock placement in Survival for all three block types. They preserve 1,234 mB, inventories, progress/counters through pickup, item-NBT serialization and relocation, enforce one packed drop without loose duplicates, test non-sneaking as a no-op, and verify ordinary destruction produces an untagged machine that places empty. Offhand pickup is included for the pipe. These 45 assertions passed. The user accepted all three manual steps and confirmed items, fluids and state persist through world restart: `evidence/fluids/1.1.0/user-acceptance.json`. Both fluid and wrench mechanisms are successful proofs.

Reproduce only this addition: `powershell -NoProfile -File qa/integration/Test-Wrench.ps1`. QA is excluded from the delivered JAR. Evidence/delivery: `evidence/fluids/1.1.0/summary.json`, `releases/fluids/1.1.0/`.

Pinned placement lesson: `Item.onItemUseFirst` handles the wrench before sneaking suppresses block activation. `ItemBlock.placeBlockAt` invokes `setTileEntityNBT`, which merges `BlockEntityTag` into the new tile and replaces coordinates. `FluidTank.writeToNBT` writes an `Empty` sentinel for fresh empty tanks, and `readFromNBT` gives it priority. Deep NBT merge retains that sentinel alongside the carried FluidName/Amount. This proof removes the sentinel when an explicit FluidName exists before reading the tank, so placement restores fluid. Packed data omits old tile id/coordinates. Relevant pinned methods were verified with bounded `Read-MappedSource.ps1` calls.

## Defaults and controls

- Brine Maker: one redstone -> 1,000 mB Biting Brine in 40 ticks (two seconds at 20 TPS).
- Clay Maker: one cobblestone + 250 mB -> one clay ball in 20 ticks.
- Each tank holds 4,000 mB. Maker and pipe push at most 100 mB/t east (+X). The pipe accepts from west and exposes its outlet east; no branching or rotation. Maker drain ports and consumer fill ports are available on every face for buckets and Forge fluid handlers. Only Biting Brine is accepted.
- Right-click with redstone/cobblestone inserts the held stack up to 64, including in Creative. Empty-hand right-click reports tank, items, progress and completions in chat. Sneak + empty hand takes output first, otherwise unused input. Bucket right-click transfers fluid. Breaking a machine drops its items but discards stored fluid.
- Miscellaneous creative tab contains the machines. Shapeless crafting: maker = iron ingot + redstone + bucket; consumer = iron ingot + cobblestone + bucket; pipe = iron ingot + glass.
- Biting Brine is purple, uses vanilla water textures, has density 1,200, viscosity 4,000 and temperature 300 K. Its block uses a 20-tick flow interval and four quanta; expect three horizontal blocks of reach from one source on a flat uninterrupted floor. Downhill flow can travel farther. No spontaneous source creation. Player contact attempts two health points (one heart) of magic damage per second; Creative immunity and normal hurt immunity apply.

## Accepted original fluid checklist (1.0.0)

Install `fluid-proof-1.0.0-srg.jar` in a Forge 1.12.2 instance. Confirm all three blocks and the purple fluid render without missing textures. Use a disposable flat area; cheats/Creative simplify obtaining the blocks.

1. Place Brine Maker -> Straight Fluid Pipe -> Clay Maker in adjacent blocks toward **east (+X)**. Insert exactly two redstone and four cobblestone. After about nine seconds, empty-hand status should show: maker/pipe 0 mB; consumer 1,000 mB, output 4, completed 4. Sneak + empty hand retrieves four clay balls.
2. Leave the maker disconnected and insert five redstone. After about eleven seconds it should show 4,000 mB, one unused redstone and four completions. Right-click it with an empty bucket: it should lose 1,000 mB and then process the remaining redstone. The filled Biting Brine bucket should pour into a consumer (1,000 mB) or into the world.
3. On a broad flat solid floor with no nearby drops, place one source from the bucket. Watch roughly one horizontal advance per second and a final reach of three blocks along each cardinal direction. Pick up the source with an empty bucket; flowing remains should decay. The user accepted this flow check.
4. Switch to Survival with full health and no protective effects. Stand in the placed fluid briefly: expect about one heart lost per second. Leave it and confirm damage stops. The user accepted this damage check.
5. Save/quit and reopen with some tank fluid and items remaining; confirm those persist. The user confirmed persistence after server logout/login and single-player world restart.

Report any failure with the checklist step, status text and a screenshot if rendering/flow differs. Stop here before expanding the proof or improving recipient kit guidance.

## Verified scope and API basis

Original build/static JAR inspection and server behavior passed; the user subsequently accepted rendering/functionality, world flow, contact damage and server logout/login plus single-player restart persistence. In 180 actual server ticks: 2,000 mB generated, 1,000 consumed, 1,000 stored and four clay balls. Nearby full producer, blocked consumer output and full receiver retain inputs/fluid; unblocking resumes. Capability fill/drain simulation leaves storage unchanged, wrong fluid is rejected and pipe directions are enforced. Tank, items and active progress survive an NBT roundtrip. No stress, fault-injection or prior energy campaign was run. Wrench follow-up evidence is separate above.

Pinned source: Forge 14.23.5.2859, stable_39, local mapped-javadoc archive queried with `Read-MappedSource.ps1`. `BlockFluidBase` constructor lines 165-182 copies viscosity / 200 into tickRate; `setTickRate` lines 236-240 changes that independently, `setQuantaPerBlock` lines 212-218 changes quanta. `onBlockAdded`/`neighborChanged` lines 332-340 schedule that rate. `BlockFluidClassic.updateTick` lines 113-190 reduces quanta by one horizontally and stops at flowMeta >= quantaPerBlock; vertical flow resets the horizontal level. Its `canCreateSources` defaults false at line 56. Random ticking is disabled in this proof so scheduled timing is observable. Viscosity alone is not the horizontal distance control.

Evidence: `evidence/fluids/1.0.0/summary.json`; delivered artifact and manual checklist: `releases/fluids/1.0.0/`.
