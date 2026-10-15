# Recipes

Generators: `new recipe` (shaped/shapeless JSON) and `new smelting`. Formats, for hand edits:

```json
{ "type": "minecraft:crafting_shaped",
  "pattern": [ "###", "#S#", " S " ],
  "key": { "#": { "item": "mymod:ruby" }, "S": { "item": "minecraft:stick" } },
  "result": { "item": "mymod:ruby_pickaxe", "count": 1 } }
```

```json
{ "type": "minecraft:crafting_shapeless",
  "ingredients": [ { "item": "minecraft:dye", "data": 4 }, { "type": "forge:ore_dict", "ore": "ingotIron" } ],
  "result": { "item": "mymod:blue_ingot", "count": 2 } }
```

- Files: `assets/<modid>/recipes/<name>.json`; the file name is the recipe id. Not `data/` (that is 1.13+).
- `"data"` = metadata (wool colors, dyes); omit for normal items. Results can carry `"nbt": "{...}"` as a string.
- Ore dictionary ingredients match any registered `ingotIron`, `gemDiamond`, `logWood`... Register your own in init: `OreDictionary.registerOre("gemRuby", ModItems.RUBY)`.
- Group recipes in the recipe book with `"group": "mymod:tools"`.
- A broken recipe file does not stop the game: Forge logs `Parsing error loading recipe` (shown by `check server`) and the recipe is missing.

Smelting (init): `GameRegistry.addSmelting(new ItemStack(input), new ItemStack(output, count), xp)`. Fuel: override `getItemBurnTime` in your item, or `FurnaceFuelBurnTimeEvent` for others.

Custom logic (NBT-dependent results, damaging a tool in the grid): extend `net.minecraftforge.registries.IForgeRegistryEntry.Impl<IRecipe>` implementing `IRecipe` (matches, getCraftingResult, canFit, getRecipeOutput, getRemainingItems), register in `RegistryEvent.Register<IRecipe>`. Removing vanilla recipes: in that event, `((IForgeRegistryModifiable<IRecipe>) event.getRegistry()).remove(new ResourceLocation("minecraft", "name"))` (shows a warning; prefer adding alternatives).

## Test

```java
InventoryCrafting grid = new InventoryCrafting(new Container() {
    @Override public boolean canInteractWith(EntityPlayer player) { return false; }
}, 3, 3);
for (int i = 0; i < 9; i++) grid.setInventorySlotContents(i, new ItemStack(ModItems.RUBY));
ItemStack result = CraftingManager.findMatchingResult(grid, t.world());
ItemStack smelted = FurnaceRecipes.instance().getSmeltingResult(new ItemStack(ModBlocks.RUBY_ORE));
```

Also test one wrong arrangement is rejected when the recipe shape matters.
