# Generators

`./agent.ps1 init <modid> "<Name>" [-Package com.x.y] [-Version 1.0.0]` creates the skeleton in an empty src/main:

| File | Role |
|---|---|
| `<Name>Mod.java` | `@Mod` class: MODID, NAME, VERSION, LOGGER, INSTANCE, preInit/init with `// kit:preinit`, `// kit:init` |
| `ModItems.java` | `item("name", new Item())` registers and sets translation key `item.<modid>.<name>`; `// kit:items` |
| `ModBlocks.java` | `block("name", ...)` registers block + ItemBlock (skip with `implements ModBlocks.NoItem`); `// kit:blocks`, `// kit:tiles` |
| `BasicBlock.java` | full block with material, sound, hardness, resistance; `.harvest("pickaxe", 2)`, `.light(1.0F)` |
| `ClientEvents.java` | client only; gives every item of the mod the model `models/item/<name>.json` (opt out: `implements ClientEvents.CustomModel`); `// kit:models` |

Generators insert code above those `// kit:` markers; keep the markers. Hand-written code next to generated code is fine.

**Scope.** Generators make standard vanilla-shaped content and nothing else. Make only what the user asked for: "armor and a sword" is `new armor x ...` plus `new tools x -Only sword ...`, not a full tool set. Anything with its own behavior (an item that copies a region with its tile entities and entities, a preview while holding it, a machine with custom rules) is hand-written from the matching guide; do not bend a generator into it. Generated items are ordinary ModItems entries, so hand-written behavior can be added next to them.

`./agent.ps1 new <kind> <name> [options]`. Names are lowercase registry paths (`ruby_ore`). Item references are `name` (this mod), `modid:name` or `minecraft:name`; unknown names are rejected (create the item first, fix the spelling, or `-Force`). Every kind writes lang entries (`-Display "Text"` overrides the title-cased name) and placeholder textures shaped like the item, one color per material (`-Color ff0000` overrides). Output is one summary line (`-List` lists every file); tools and armor add a `stats:` line with the values actually written. An option that does not belong to the kind is an error, never ignored.

| Kind | Options (defaults) | Creates |
|---|---|---|
| `item` | `-Tab MATERIALS` `-StackSize n` `-Look gem\|ingot\|raw\|nugget\|dust\|rod` (guessed from the name) | item, model, texture |
| `block` | `-Material ROCK` (IRON WOOD GROUND SAND GLASS CLOTH PLANTS ...) `-Hardness 3` `-Resistance` `-Tool pickaxe` `-Level 0` `-Light 0..1` | block + item, blockstate, models, texture |
| `ore` | `-Drops item` (else drops itself) `-Count 1-2` `-Xp 3-7` `-Level 1` `-Hardness 3` `-Veins 8` `-VeinSize 8` `-Height 5-48` `-Dimension 0` (-1 Nether, 1 End) | OreBlock (Fortune, Silk Touch, XP), ModWorldGen entry |
| `food` | `-Hunger 4` `-Saturation 0.3` `-Wolf` `-AlwaysEdible` `-Effect name:ticks[:amplifier[:chance]]` | ItemFood; amplifier 0 = level I |
| `tools` | `-Only sword,pickaxe,axe,shovel,hoe` (all) `-Like iron` (wood stone iron diamond gold) `-Level` `-Durability n\|material` `-Speed` `-Damage` `-Enchant` `-AxeDamage` `-AxeSpeed` `-Repair item` `-Recipes` `-Handle minecraft:stick` | material + the pieces (`<name>_sword` ...); `-Recipes` adds vanilla-shaped recipes from `-Repair` |
| `armor` | `-Only helmet,chestplate,leggings,boots` (all) `-Like iron` (leather chainmail iron gold diamond) `-Durability n\|material` `-Protection h,c,l,b` `-Toughness` `-Enchant` `-Repair item` `-Recipes` | material + the pieces, item and worn textures |
| `recipe` | shaped: `-Shape "###,#S#,###" -Key "#=ruby,S=minecraft:stick"`; shapeless: `-Ingredients "a,b"`; both: `-Result item -Count n` | `recipes/<name>.json`. Ingredients may be `minecraft:dye@4` (metadata) or `ore:ingotIron` (ore dictionary) |
| `smelting` | `-From item -Result item -Count 1 -Xp 0.7` | `GameRegistry.addSmelting` line in init |
| `tile` | `-Ticking` `-Inventory n` | `<Name>Block` + `<Name>Tile` (NBT save, client sync, optional ITickable and item capability, drops contents), registration |
| `test` | name like `ruby-ore-drops` | `src/gametest/.../<Name>Test.java` scaffold (status reminds you until the placeholder check is replaced) |

`-Like` sets every value from a vanilla material; any explicit option overrides it. "Iron stats with diamond durability": `new tools gron -Like iron -Durability diamond`.

| Tools (vanilla) | level | uses | speed | damage | enchant | axe damage / speed |
|---|---|---|---|---|---|---|
| wood | 0 | 59 | 2 | 0 | 15 | 6 / -3.2 |
| stone | 1 | 131 | 4 | 1 | 5 | 8 / -3.2 |
| iron | 2 | 250 | 6 | 2 | 14 | 8 / -3.1 |
| diamond | 3 | 1561 | 8 | 3 | 10 | 8 / -3.0 |
| gold | 0 | 32 | 12 | 0 | 22 | 6 / -3.0 |

| Armor (vanilla) | durability factor | protection helmet,chest,legs,boots | enchant | toughness |
|---|---|---|---|---|
| leather | 5 | 1,3,2,1 | 15 | 0 |
| chainmail | 15 | 2,5,4,1 | 12 | 0 |
| iron | 15 | 2,6,5,2 | 9 | 0 |
| gold | 7 | 2,5,3,1 | 25 | 0 |
| diamond | 33 | 3,8,6,3 | 10 | 2 |

Sword tooltip damage = 4 + `-Damage` (diamond: 7); axe tooltip = 1 + `-AxeDamage` (diamond: 9). Armor piece durability = factor x 11/16/15/13 (helmet/chest/legs/boots). Damage reduction counts at most 20 armor points in total (full diamond = 20), so higher protection only fills the bar; use `-Toughness` for stronger armor.

"N times diamond" is ambiguous (tooltip damage or bonus? protection past the cap?). Pick one reading, write the resulting numbers and the reason in TASK.md, then pass them explicitly: 3x diamond sword tooltip 21 = `-Damage 17`. Compare the `stats:` line with TASK.md before checking: generated checks assert what the generator wrote, so they cannot catch a wrong number.

**Generated checks.** Every generator (except tile/test) appends assertions to `src/gametest/.../GeneratedContentTest.java`: recipes craft their result, smelting smelts, ores drop the right items, tool/armor/food stats match. `./agent.ps1 check game` runs them; edit them like any test.

**Batch.** Put one command per line in a text file (`#` comments, quotes for values with spaces) and run `./agent.ps1 batch plan.txt`. It stops at the first failure and says which line.

```text
# plan.txt
init gems "Gem Mod"
new item ruby
new item raw_ruby
new ore ruby_ore -Drops raw_ruby -Count 1-2 -Level 2 -Height 5-32
new smelting ruby -From raw_ruby -Result ruby -Xp 1.0
new block ruby_block -Material IRON -Hardness 5 -Level 2
new recipe ruby_block -Shape "###,###,###" -Key "#=ruby" -Result ruby_block
new recipe ruby_from_block -Ingredients ruby_block -Result ruby -Count 9
new tools ruby -Like diamond -Repair ruby -Recipes
new armor ruby -Like iron -Durability diamond -Repair ruby -Recipes
new food ruby_apple -Hunger 6 -Saturation 1.2 -AlwaysEdible -Effect regeneration:100:1
new tile ruby_box -Ticking -Inventory 9
check game
```

Ore generation affects only newly generated chunks. Anything not covered: write it by hand following the matching guide (docs/INDEX.md), registering through ModItems/ModBlocks so models and registration stay automatic.
