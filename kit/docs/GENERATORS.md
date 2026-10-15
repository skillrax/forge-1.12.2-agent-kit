# Generators

`./agent.ps1 init <modid> "<Name>" [-Package com.x.y] [-Version 1.0.0]` creates the skeleton in an empty src/main:

| File | Role |
|---|---|
| `<Name>Mod.java` | `@Mod` class: MODID, NAME, VERSION, LOGGER, INSTANCE, preInit/init with `// kit:preinit`, `// kit:init` |
| `ModItems.java` | `item("name", new Item())` registers and sets translation key `item.<modid>.<name>`; `// kit:items` |
| `ModBlocks.java` | `block("name", ...)` registers block + ItemBlock (skip with `implements ModBlocks.NoItem`); `// kit:blocks`, `// kit:tiles` |
| `BasicBlock.java` | full block with material, sound, hardness, resistance; `.harvest("pickaxe", 2)`, `.light(1.0F)` |
| `ClientEvents.java` | client only; gives every item of the mod the model `models/item/<name>.json` (opt out: `implements ClientEvents.CustomModel`); `// kit:models` |

Generators insert code above those `// kit:` markers. Keep the markers. Hand-written code next to generated code is fine.

`./agent.ps1 new <kind> <name> [options]`. Names are lowercase registry paths (`ruby_ore`). Item references are `name` (this mod), `modid:name` or `minecraft:name`. Every kind writes lang entries (`-Display "Text"` overrides the title-cased name) and 16x16 placeholder textures (`-Color ff0000` sets the color).

| Kind | Options (defaults) | Creates |
|---|---|---|
| `item` | `-Tab MATERIALS` `-StackSize n` | item, model, texture |
| `block` | `-Material ROCK` (IRON WOOD GROUND SAND GLASS CLOTH PLANTS ...) `-Hardness 3` `-Resistance` `-Tool pickaxe` `-Level 0` `-Light 0..1` | block + item, blockstate, models, texture |
| `ore` | `-Drops item` (else drops itself) `-Count 1-2` `-Xp 3-7` `-Level 1` `-Hardness 3` `-Veins 8` `-VeinSize 8` `-Height 5-48` `-Dimension 0` (-1 Nether, 1 End) | OreBlock (Fortune, Silk Touch, XP), ModWorldGen entry |
| `food` | `-Hunger 4` `-Saturation 0.3` `-Wolf` `-AlwaysEdible` `-Effect name:ticks[:amplifier[:chance]]` | ItemFood; amplifier 0 = level I |
| `tools` | `-Level 2` `-Durability 500` `-Speed 6` `-Damage 2` `-Enchant 14` `-Repair item` | material + sword, pickaxe, axe, shovel, hoe (`<name>_sword` ...) |
| `armor` | `-Durability 15` `-Protection 3,8,6,3` (helmet,chest,legs,boots) `-Toughness 0` `-Enchant 14` `-Repair item` | material + 4 pieces, item and worn textures |
| `recipe` | shaped: `-Shape "###,#S#,###" -Key "#=ruby,S=minecraft:stick"`; shapeless: `-Ingredients "a,b"`; both: `-Result item -Count n` | `recipes/<name>.json`. Ingredients may be `minecraft:dye@4` (metadata) or `ore:ingotIron` (ore dictionary) |
| `smelting` | `-From item -Result item -Count 1 -Xp 0.7` | `GameRegistry.addSmelting` line in init |
| `tile` | `-Ticking` `-Inventory n` | `<Name>Block` + `<Name>Tile` (NBT save, client sync, optional ITickable and item capability, drops contents), registration |
| `test` | name like `ruby-ore-drops` | `src/gametest/.../<Name>Test.java` scaffold |

Vanilla diamond values for reference: tools level 3, 1561 uses, speed 8, damage 3, enchant 10; armor durability 33, protection 3,8,6,3, toughness 2, enchant 10. Iron: level 2, 250 uses, speed 6, damage 2; armor 15, 2,6,5,2.

Nothing generates crafting recipes for tools/armor; add them with `new recipe`. Ore generation affects only newly generated chunks.

Examples:

```powershell
./agent.ps1 init gems "Gem Mod"
./agent.ps1 new item ruby
./agent.ps1 new ore ruby_ore -Drops ruby -Count 1-2 -Level 2 -Height 5-32
./agent.ps1 new block ruby_block -Material IRON -Hardness 5 -Level 2
./agent.ps1 new recipe ruby_block -Shape "###,###,###" -Key "#=ruby" -Result ruby_block
./agent.ps1 new recipe ruby_from_block -Ingredients ruby_block -Result ruby -Count 9
./agent.ps1 new smelting ruby -From ruby_ore -Result ruby -Xp 1.0
./agent.ps1 new tools ruby -Level 3 -Durability 1200 -Speed 8 -Damage 3 -Repair ruby
./agent.ps1 new armor ruby -Durability 30 -Protection 3,8,6,3 -Toughness 2 -Repair ruby
./agent.ps1 new food ruby_apple -Hunger 6 -Saturation 1.2 -AlwaysEdible -Effect regeneration:100:1
./agent.ps1 new tile ruby_box -Ticking -Inventory 9
```

Anything not covered: write it by hand following the matching guide (docs/INDEX.md), registering through ModItems/ModBlocks so models and registration stay automatic.
