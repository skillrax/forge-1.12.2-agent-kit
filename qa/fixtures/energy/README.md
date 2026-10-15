# Energy Capability Proof 1.1.0

Three creative/give-only blocks, using vanilla textures:

- **Demo RF Generator:** free 40 RF/t, 4000 RF buffer; Forge Energy output on EAST. Redstone-block appearance. Fuel behavior is outside this proof.
- **RF Pipe:** 80 RF/t, 256 RF buffer; input WEST, output EAST. Gold bar appearance. Multiple pipes may form a straight line; no branches, routing, rotation or legacy CoFH adapter.
- **RF Processor:** 4000 RF buffer, receive up to 128 RF per call. Chooses a recipe from its input, with output names/chance and progress duration read from that recipe. Iron-block appearance.

| Input | Primary output | Secondary output | Duration/cost |
|---|---|---|---|
| Cobblestone | 1 gravel | 20% chance of 1 flint | 20 ticks / 80 RF |
| Stone | 1 cobblestone | 50% chance of 1 extra cobblestone | 20 ticks / 80 RF |

Recipes are Java entries in ProcessingRecipe.java. The processing loop has no stone/cobblestone/gravel-specific branch. External JSON recipe loading is outside this small example.

Both output slots reserve space before work. A full/incompatible output pauses progress and RF spending, including the secondary slot even if that recipe's random roll would fail. Machine inventory and RF/progress have NBT storage. Breaking drops inventory and discards block energy/progress. No restart or break campaign is claimed.

## Focused 1.1.0 in-game check

Use Minecraft 1.12.2 / Forge 14.23.5.2859. Install the exported SRG JAR in an isolated profile; cheats or creative mode are needed.

```text
/give @p energyproof:generator
/give @p energyproof:pipe
/give @p energyproof:processor
/give @p minecraft:cobblestone 64
```

Replace the old JAR with 1.1.0; keep only one energyproof JAR installed. The block IDs are unchanged, so the existing line can be reused. The user passed all four original 1.0.0 checks (line, processing/flint, full-output pause/resume, unpowered machine); repeating that suite is unnecessary.

1. In the powered processor, process a few cobblestone. Confirm the generic RF Processor menu identifies gravel and 20% extra flint, with the expected outputs.
2. Clear both output slots, then put 16 stone in the **same processor** (`/give @p minecraft:stone 16`). Confirm the menu changes to cobblestone and 50% extra cobblestone. Expect 16 cobblestone in the primary slot and a random number of extra cobblestone in the secondary slot. Each recipe takes about one second at 20 TPS; chance is per completion, not a guaranteed alternating cadence.

Leftover gravel or flint blocks the stone recipe because its output types differ. Clear them when switching recipes. The server check processes both recipes sequentially in the same powered tile and verifies resource/energy accounting.

That is the requested gameplay suite; no additional human lifecycle matrix is required.

Acceptance: user confirmed all four original 1.0.0 steps and both 1.1.0 recipes work. Proof complete; the checklist remains a reproduction aid.

## Optional reproduction

From the authoring root: `powershell -NoProfile -File qa/integration/Test-Energy.ps1`. Creates a new disposable kit checkout, builds the mod, runs one server line, checks QA exclusion, and exports. Source is here; server-only checks are under `src/evaluation` and excluded from the JAR. This replays a preserved solution, not a fresh independent generation trial.
