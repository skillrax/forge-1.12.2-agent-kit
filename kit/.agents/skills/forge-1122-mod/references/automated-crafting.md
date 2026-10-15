# Saved-pattern automated crafting

Use for a powered machine that repeatedly crafts a supplied 3x3 pattern using registered Minecraft/Forge recipes. Reuse [machines and energy](machines-and-energy.md) for tile inventories, menus, FE and safe dirty marking. Its inventory/menu scaffold supplies the basic plumbing; this reference adds the crafting transaction and matching policy without another machine framework.

## Ghost pattern and actual supplies

Store nine one-item ghost copies separately from real inventory. Label/color them clearly: selecting a pattern never consumes, returns or drops an item. Real input cells occupy the same grid positions; empty ghost cells require empty input cells. Keep ghost storage out of inventory capabilities, shift-click destinations, extraction and block-break drops. Reject taking, dragging into and collecting from ghost slots. Server-check the open container, interaction distance, slot/action bounds and player cursor before changing the saved pattern.

Vanilla accepted click transactions suppress echoed slot updates. A ghost `Container.slotClick` must therefore predict the same cursor-copy/erase operation on the client while the server independently validates and applies it. Leave the cursor untouched and return the same transaction result on both sides. An exclusively server-side copy can be stored correctly but remain visually empty. Run/Pause, Clear Pattern and matching buttons can use `sendEnchantPacket` with validated `Container.enchantItem` button IDs; synchronize settings/progress/RF through container properties. Keep screens and rendering client-only.

## Registered recipe and real data

Build an `InventoryCrafting` 3x3 grid from copies of actual supplies, one item per occupied cell for the ordinary crafting contract. Its callback container must not mutate the real inventory. In the pinned API:

```java
IRecipe recipe = CraftingManager.findMatchingRecipe(actualGrid, world);
if (recipe == null || !recipe.matches(actualGrid, world)) return;
ItemStack result = recipe.getCraftingResult(actualGrid);
NonNullList<ItemStack> returns = recipe.getRemainingItems(actualGrid);
```

Use the matched instance for validity, result and remainders. `getRecipeOutput()` is a display declaration; it does not replace `getCraftingResult` for dynamic data. Reject empty results or malformed remainder lists before work. Recipe methods run on detached copies; do not copy ghost tags onto results, strip real input data or manufacture container returns yourself.

Strict matching can resolve the ghost recipe and require it to match actual supplies. For fuzzy matching, first check the pattern's item identities/positions and enabled comparison rules, then resolve the registered recipe from the actual grid. This permits the valid actual metadata variant: a spruce log against an oak example produces spruce planks. A preview should follow valid supplied inputs; an idle ghost preview is only a hint.

## Per-pattern NBT and metadata policy

Expose independent **Ignore NBT** and **Ignore metadata** settings, default OFF. Persist and synchronize both with the saved pattern. The filter always requires the same item identity and occupancy; ignore settings skip only their corresponding comparison. Define Clear Pattern/new-pattern defaults explicitly; the accepted proof resets both OFF. Do not silently treat metadata ignore as ore-dictionary substitution or different-item equivalence.

Player-handled examples can gain incidental tags absent from freshly crafted ingredients. Ignore NBT allows that mismatch at the pattern filter. It does **not** bypass a registered recipe's own NBT requirements. Validate untouched actual data and preserve whatever result/remainder data the recipe returns. Output merging must still compare item identity, metadata and NBT exactly, even when input matching is fuzzy. Removing unwanted result tags is a separate recipe/integration decision with its own behavior check.

## Plan before spending or consuming

On every processing tick, pack the result and every nonempty remainder into a copy of the output inventory. Merge compatible stacks first, then fill empty cells, respecting both item and inventory stack limits. Consider all returns together: space for a cake alone is insufficient if its three returned buckets cannot fit. Never partially install the plan, discard a return or reroll a recipe while blocked.

Only after the complete plan fits should the machine spend RF and advance progress. Full/incompatible outputs pause both. On completion, consume one from each occupied real input cell and install the complete planned output state together on the logical server thread, with no external handler callbacks between validation and commit. The proof sends returns into a shared output pool; returning them to inputs is another policy that must include their capacity in the same plan. Separate storage keeps ghosts unchanged and prevents accidental recursive crafting.

Define work invalidation. The accepted policy resets progress when any input count/type/NBT changes, a ghost is edited, or a matching setting changes; already spent RF is not refunded. Pause/output blockage retains progress. Save inventory, pattern, matching flags, Run state, RF, progress and a snapshot of the inputs tied to that progress. On restore, clamp progress and revalidate before spending. Snapshot/check all inputs even when matching ignores tags: changing real data can change recipe output. Mark committed state dirty; do not expose client property setters as server control endpoints.

## Small proof and limits

Check one ordinary recipe, one returned-container recipe, output/remainder blockage and unblocking with item/RF accounting. For fuzzy controls, check strict rejection then relaxed success, an actual metadata variant, preserved result/remainder data and a registered NBT-sensitive recipe that still rejects invalid supplies. Observe normal ghost/button packets and results graphically; check normal save/reopen of pattern, flags and pause state. Keep QA outside production JARs and stop the client before building into its checkout.

The accepted Powered Crafter establishes those mechanisms with vanilla log/plank and cake/bucket recipes, plus an isolated registered QA recipe for data retention/rejection. Arbitrary modded recipes, player crafting callbacks/advancements, specialized ingredient-count contracts, logistics, recursive crafting, JEI and large-network behavior need the requested integration's own evidence. Reuse the mechanism without repeating cosmetic, speed or tier variants.
