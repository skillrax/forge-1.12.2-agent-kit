# Powered Crafter proof

One creative-access **Powered Crafter**, a free Demo RF Generator and the existing straight RF Pipe, using vanilla textures. Registry IDs: `craftingproof:processor`, `craftingproof:generator`, `craftingproof:pipe`. All three appear in the Redstone creative tab. Minecraft 1.12.2 / Forge 14.23.5.2859.

Place generator, pipe, crafter in a line going EAST (increasing X), ideally within one chunk. The generator outputs east; the pipe pulls west and delivers east. The crafter receives Forge Energy on any face, holds 4000 RF and takes 20 ticks / 80 RF per craft. It starts paused.

The blue 3x3 grid is a **saved ghost pattern**, never real inventory. Pick up an item on the cursor and left-click a blue cell to copy one; right-click a blue cell to erase it. The cursor stack remains unchanged. Put actual items into the separate gray Ingredients grid in the same positions. Empty pattern positions must also be empty in that grid. Item identity always matches; metadata and NBT match unless that pattern's corresponding **Ignore NBT** / **Ignore meta** button is ON. Both default OFF, are saved and synchronized independently, and reset OFF when Clear Pattern starts a new pattern. Run/Pause, Clear Pattern and matching controls use server-validated container actions.

Ignore settings relax only the ghost/input filter. With either ON, registered recipe lookup uses the actual supplied grid, allowing a valid metadata variant to produce its own result (spruce logs produce spruce planks even with an oak ghost). The real stacks are not stripped, sanitized or rewritten. The registered recipe must still accept their actual data and determines all result/remainder NBT. A recipe that requires specific NBT can still reject an input. Output merging always compares metadata and NBT strictly. This addresses incidental data on player-handled pattern examples without adding unwanted pattern tags to freshly crafted outputs; it does not repair an incompatible third-party recipe.

Registered recipe lookup uses pinned `CraftingManager.findMatchingRecipe`; the selected `IRecipe` supplies `matches`, `getCraftingResult` and `getRemainingItems`. Each occupied cell supplies one item. The ten output cells share space for results and **all** returned items. The crafter plans the complete next output inventory on copies before each processing tick. Full outputs or insufficient remainder space pause progress and RF spending; making room resumes the same work. At completion it consumes inputs and installs the planned outputs together on the server thread. Returned buckets go to the output cells, not the pattern or input cells.

Any change to input counts/types/NBT resets progress on the next server tick; editing the pattern or either matching setting resets it immediately. RF already spent on abandoned work is not refunded. Pause and output blockage retain progress. Inventory, pattern, matching settings, Run state, RF, progress, input snapshot and counters are saved. Breaking drops only real inventories and discards the pattern/RF/progress.

## Short manual acceptance

Original steps 1–3 below were accepted by the user for 1.0.1, including actual save/reopen and additional RF/NBT pickaxe observations: `evidence/automated-crafting/1.0.1/user-acceptance.json`. Repeating them is unnecessary. Install only `automated-crafting-proof-1.0.2-srg.jar` for the new matching controls; existing worlds/IDs are compatible and missing saved settings default OFF.

1. Open a powered crafter. Hold two oak logs on the cursor, copy one into the top-left blue cell, then place the two real logs into the top-left gray input cell. Confirm copying did not consume a log. Click **Run**: expect exactly eight oak planks and empty ingredients. Pause, clear the pattern and take the planks.
2. Select and supply one vanilla cake recipe, copying each ingredient into the matching blue cell before placing it into its gray cell. Rows: `milk bucket / milk bucket / milk bucket`; `sugar / egg / sugar`; `wheat / wheat / wheat`. Click Run: expect **one cake plus three empty buckets**, with all nine real inputs consumed and all blue ghosts retained. Take the cake and buckets.
3. Pause with the cake pattern retained and place another recipe's real ingredients. Close the menu, save and quit normally, then reopen the world and menu. Confirm the pattern, ingredients and paused state survived. Click Run: expect one cake and three buckets again. The user passed this normal save/reopen step in 1.0.1; automated NBT restoration alone is not a separate JVM restart proof.

Blocked/unblocked output and remainder capacity are already checked automatically in a short real server world; no manual mass-crafting fill campaign is needed.

## Matching-control delta check

1. Give yourself one tagged oak log with `/give @p minecraft:log 1 0 {PlayerTouched:1b}`. Copy it into the top-left blue cell, then return that tagged log to your inventory. Put a plain oak log in the matching gray cell. With both ignore settings OFF, Run should wait without consuming the ingredient. Turn **Ignore NBT ON**: expect four ordinary oak planks.
2. Keep that same oak pattern and NBT setting; supply one spruce log (`/give @p minecraft:log 1 1`). With Ignore meta OFF it should wait. Turn **Ignore meta ON**: expect four **spruce** planks, not oak planks. The preview follows the actual valid inputs.
3. Pause, keep both settings ON, and supply another spruce log. Save/quit normally and reopen. Confirm both ON settings, the ghost and input survived and the machine remains paused. Run should make four spruce planks. Clear Pattern should return both settings to OFF.

## Focused evidence and reproduction

`powershell -NoProfile -File qa/integration/Test-Crafting.ps1` creates an isolated checkout, builds, runs dedicated-world accounting and a real graphical integrated client, checks QA exclusion, and exports. Production is `src/main`; all assertions, automatic world setup and screenshot capture are `src/evaluation` and excluded from the production JAR. Never build into that checkout while its client is running.

Final results and inspected screenshots: `evidence/automated-crafting/1.0.2/summary.json`. User accepted all three matching-control steps, including settings save/reopen, and called the mechanism proven: `evidence/automated-crafting/1.0.2/user-acceptance.json`. Original crafting/remainder steps also accepted. Recipient guidance: `kit/.agents/skills/forge-1122-mod/references/automated-crafting.md`; package checks: `evidence/automated-crafting-kit/summary.json`. Focused QA adds a registered NBT-sensitive recipe outside production to prove actual data retention and rejection despite fuzzy settings. Two vanilla recipes plus that synthetic recipe establish a bounded proof; arbitrary third-party/dynamic recipes, player crafting callbacks/advancements, logistics, recursive crafting, JEI and network/chunk stress are not established.

Implementation lesson: vanilla accepted click transactions suppress echoed slot updates. Ghost-slot copies must participate in ordinary client prediction while the server independently checks the cursor/action and applies the same copy. Ghost slots reject take/shift/drag behavior, preventing them from becoming real items. Pattern controls remain server-owned.
