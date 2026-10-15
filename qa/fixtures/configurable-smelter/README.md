# Configurable Smelter proof

Minecraft 1.12.2 / Forge 14.23.5.2859 / Java 8. Four processing slots (main input, secondary input, main output, secondary output) plus one GUI-only **Secondary Output Upgrade** slot. The menu contains a colored six-face selector, separate auto-input/output buttons and a redstone polarity button. All settings are controlled on the server and saved with the inventory, RF and processing progress.

## Install and setup

Install `releases/configurable-smelter/1.0.1/configurable-smelter-1.0.1-srg.jar` in client/server mods folders. Replace the previous smelter JAR; keep one version installed. Existing block IDs and saved configuration remain compatible. All proof items are in the Redstone creative tab; the smelter and upgrade also have crafting recipes.

```text
/give @p smelterproof:smelter
/give @p smelterproof:secondary_output_upgrade
/give @p smelterproof:generator
/give @p smelterproof:pipe
```

For free test power, place **generator -> pipe -> smelter** along increasing X (WEST to EAST). The generator exposes EAST, pipe accepts WEST and sends EAST. Generator produces 40 RF/t; smelter accepts Forge Energy on any face independently of that face's item setting. Buffer: 16000 RF. Operation: 100 ticks / 2000 RF (about five seconds at 20 TPS). The generator is a reused test convenience, not a fuel-conversion proof.

## Recipes and controls

Without an upgrade, the main input uses the current `FurnaceRecipes` registry, including ordinary recipes registered by other mods. The registered result/count is copied to the main output; one main input is consumed, secondary input/output are untouched. Registry-added recipe support has isolated server evidence; arbitrary modpack/furnace-specific hooks and experience payout are not claimed.

With the upgrade, these two recipes take priority when both ingredients are present:

| Main input | Secondary input | Main output | Secondary output |
|---|---|---|---|
| 1 iron ore | 1 normal sand | 2 iron ingots | 1 brick |
| 1 gold ore | 1 normal sand | 2 gold ingots | 1 brick |

Other registered furnace recipes remain available. **Without sand, iron/gold ore falls back to ordinary single-ingot smelting**, even with the upgrade. Red sand is not an ingredient in these two example recipes. No random output. Inputs are consumed on completion; required outputs must fit before RF/progress advances. Full/incompatible outputs pause safely. Changing inputs, recipe or upgrade can reset partial progress; already spent RF is not refunded.

Each world-direction face cycles **Disabled -> Main input (blue) -> Secondary input (yellow) -> Main output (orange) -> Secondary output (purple)**. Labels: `MI`, `SI`, `MO`, `SO`, `--`. The unfolded selector is UP / WEST-NORTH-EAST / DOWN / SOUTH. These are absolute compass directions, not sides relative to the player. Hover a face for its assignment. Multiple faces can use the same slot. The upgrade is never exposed to automation. Input faces accept insertion only; output faces allow extraction only. Disabled faces close item access; they still accept RF.

Defaults: UP main input, WEST secondary input, DOWN main output, EAST secondary output, NORTH/SOUTH disabled; both automatic toggles OFF. Normal hopper/pipe capability access follows assigned faces regardless of the automatic toggles. Auto input actively pulls from the adjacent inventory on each assigned input face; auto output pushes the assigned output into its neighbor. Each face moves up to eight items every ten ticks. Adjacent inventories are accessed on the opposite face. Full destinations retain items; unavailable chunks are not deliberately loaded. Vanilla chest access uses the established conservative loaded-area guard.

Default redstone is **Run: unpowered**: an active signal stops processing and automatic transfers. The button flips to **Run: powered**, requiring an active signal. Pausing retains partial progress. RF reception remains available while paused. Breaking drops the inventory/upgrade and discards RF, progress and configuration; this proof has no wrench pickup.

## User manual check

1. **Menu and ordinary smelting:** build the power line, open the smelter and check RF arrives. With no upgrade, smelt one cobblestone into one stone in main output; secondary output stays empty. If your modpack already adds a basic furnace recipe, one of those can be tried in the same slot.
2. **Upgrade recipes:** clear outputs, install the upgrade, insert one iron ore in main input and one normal sand in secondary input. Expect exactly two iron ingots plus one brick, with both inputs consumed. Clear outputs and repeat with gold: two gold ingots plus one brick.
3. **Face selection and automatic input:** use a chest above for ore and a chest NORTH for sand. UP is already MI; click NORTH twice to select SI. With both automatic toggles OFF, the chests retain items. Put two iron ore and two sand in their respective chests, then enable auto input. After roughly eleven seconds expect four ingots and two bricks inside the machine. Set an empty input face to disabled and verify it rejects hopper/pipe input; restore its role.
4. **Automatic output:** place receiving chests DOWN (main output) and EAST (secondary output), then enable auto output. Expect four ingots in the lower chest and two bricks in the east chest. Disable auto output and verify later outputs remain in the machine. Clear remaining ingredients before the next checks.
5. **Redstone polarity:** with ingredients and RF ready, power the smelter using a lever. Default mode pauses it. Flip to Run: powered and processing resumes; remove the signal and it pauses again. Flip back to Run: unpowered. Check the buttons and colored faces retain their settings when reopening the menu.
6. **Blocked secondary output:** turn both auto toggles OFF, clear main output, fill secondary output with 64 bricks, then insert one ore plus one sand with the upgrade installed. Inputs/progress remain unchanged. Remove the bricks and expect exactly one upgraded operation.
7. **Save/reopen:** choose recognizable face/toggle/redstone settings; start an operation in the enabled mode. Save, fully close and reopen the world. Confirm inventory, upgrade, RF, settings and partial operation persist and work resumes without an extra output.

The user reported all seven steps passed with expected outputs, including actual save/reopen. Follow-up testing also confirmed buttons, diagnostic messages, chest slot changes, double-chest creation/breaking and hoppers work. On 2026-10-13 the user closed the intermittent-output investigation after neither side could reproduce it. The proof is complete and ready for kit extraction. Evidence: `evidence/configurable-smelter/1.0.1/user-acceptance.json`; original report/screenshots remain under `evidence/configurable-smelter/1.0.0/`. The original report included stalls on orange `E MO`, while its screenshot showed blue `E MI`; neither is discarded. Closure does not establish a root cause or a fix.

## Diagnostic patch and closed investigation

The patch adds server-synchronized **Last auto check** information when hovering a face. It does not claim to fix the intermittent stall: targeted reproduction has not reproduced it. Normal transfer logic is retained. The menu now also identifies a machine paused by an unavailable nearby chunk.

No further manual check is pending. If a concrete recurrence is reported later, hover the affected output face before cycling it and retain the complete tooltip reason plus the adjacent chest's free-space state. Reasons distinguish redstone pause, missing neighbor/chest-area chunks, no adjacent inventory/capability, no items, rejection/full destination, and successful movement. Reopen the investigation only with new evidence; the passing gameplay campaign does not need another replay.

## Maintainer reproduction

Source is `src/main`. Isolated assertions/client screenshot helpers are under `src/evaluation` and added to dev runs only by `build-overlay.gradle`; no evaluation classes enter the production JAR. Build/export through the kit in a new source checkout. Focused runners: `qa/integration/Invoke-SmelterServer.ps1`, `Invoke-SmelterClient.ps1`. Original server checks drive production update methods synchronously in a bootstrapped world; their evidence remains `evidence/configurable-smelter/1.0.0/`. For the output investigation also copy `src/outputEvaluation` and apply `build-output-overlay.gradle` after the normal overlay: this selects the real-tick output scenario instead of repeating the broad suite. It runs 420 normal server ticks across four operations, chest replacement, role cycling, full/cleared output and tile restoration, with exact item accounting. A QA-only Forge ticket keeps the headless fixture ticking. Client checks open the real menu, submit window actions, verify synchronized transfer status and capture the actual tooltip. Patch evidence: `evidence/configurable-smelter/1.0.1/`.
