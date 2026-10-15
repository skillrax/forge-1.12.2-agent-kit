# Automated crafting (saved 3x3 pattern)

A powered machine that repeatedly crafts a saved pattern using the real registered recipes. Build it on guides/machines.md (inventory, menu, FE, dirty marking).

## Ghost pattern vs real inputs

- Store nine one-item ghost copies separately from the real input grid. Ghosts are never consumed, dropped, extracted, shift-clicked into, or exposed as capabilities. Label them visibly.
- Real inputs occupy the same positions; an empty ghost cell requires an empty input cell.
- Editing ghosts goes through `Container.slotClick`: predict the same copy/erase on the client (vanilla suppresses echoed slot updates, so a server-only change looks empty) while the server validates container, distance, slot and cursor. Never change the cursor stack.
- Buttons (run/pause, clear, matching flags): `sendEnchantPacket` / `Container.enchantItem` with validated ids; sync settings, progress and RF as container properties.

## Use the registered recipe

```java
IRecipe recipe = CraftingManager.findMatchingRecipe(actualGrid, world);   // grid of copies, one item per used cell
if (recipe == null || !recipe.matches(actualGrid, world)) return;
ItemStack result = recipe.getCraftingResult(actualGrid);                 // not getRecipeOutput(): results can depend on inputs
NonNullList<ItemStack> returns = recipe.getRemainingItems(actualGrid);   // buckets, tools, containers
```

The `InventoryCrafting`'s container must not touch the real inventory. Reject empty results or malformed remainder lists before doing work.

## Matching options

"Ignore NBT" and "Ignore metadata" per pattern, default off, saved and synced. The filter always requires the same item and occupancy; ignore flags only skip their comparison. They never bypass the recipe's own requirements (the registered recipe still decides from the actual items). Ignoring metadata is not ore-dictionary substitution. Output stacking still compares item, metadata and NBT exactly.

## Plan before spending

Every tick: pack the result **and every remainder** into a copy of the output inventory (merge then fill empties, respecting item and slot limits). Only if everything fits: spend RF and advance progress. On completion consume one from each used input and install the planned outputs together, on the server thread, without external callbacks in between. Blocked output pauses with progress kept.

Reset progress when any input count/type/NBT changes, a ghost changes, or a matching flag changes (spent RF is not refunded). Save inventory, pattern, flags, run state, RF, progress and a snapshot of the inputs tied to the progress; on load, clamp and revalidate before spending.

## Test

One ordinary recipe (logs to planks), one with returned containers (cake + 3 buckets), blocked output then unblocked with item and RF accounting. For fuzzy flags: strict rejects, relaxed accepts a metadata variant, NBT-sensitive recipe still rejects bad inputs.
