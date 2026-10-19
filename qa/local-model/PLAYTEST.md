# Playtest rubric (5 minutes per trial)

What the game tests cannot see: models, textures, HUDs, chat as the player reads it, and a real save and reload. Run it on trials whose `check game` passes, after the acceptance tests (summary.md "Acceptance" column), in the trial folder:

```text
gradlew.bat runClient
```

Creative singleplayer world, cheats on, superflat. Score each line 0 (broken or missing), 1 (works with a visible flaw: missing texture, raw translation key, wrong count, needs a retry) or 2 (as asked). Write the scores and one line per flaw into the result file (`results/<id>-<agent>.md`). "Relog" = Save and Quit to Title, then reopen the world. Lines marked (op) need the cheats you turned on; the rest are survival behavior, so switch with `/gamemode s` where it matters.

## S0 - /whereami

| Check | 0-2 |
|---|---|
| `/whereami` in the Overworld: block coordinates match F3, says Overworld, biome name readable (not a key) | |
| Brightness changes between standing by a torch and in a dark hole | |
| `/whereami share`: the line names you (open to LAN and join from a second client if you can) | |
| `/whereami foo` shows usage; a command block running `/whereami` prints an error, no crash | |
| Look: chat text is readable, not raw keys or `BlockPos{...}` | |

## S1 - Glowberries

| Check | 0-2 |
|---|---|
| Place: seeds plant on farmland, not on dirt or grass | |
| Look: each of the 4 stages has its own model, no purple-black block at any stage (bonemeal through them) | |
| Look: a grown bush lights the ground around it at night (F3 block light) | |
| Break: grown bush gives berries and seeds, young one gives one seed; jumping on the farmland breaks it | |
| Eat a berry (survival): small hunger refill, night vision for a few seconds; item icons and names readable | |

## S2 - Pulse counter

| Check | 0-2 |
|---|---|
| Place: the block faces away from you like a repeater; the model shows which side is the front | |
| A lever on the back, flicked 3 times: dust in front shows strength 3; dust beside it stays off | |
| Right-click shows the count; sneak-right-click resets; a comparator beside it shows the count | |
| Relog: the count and the dust strength are the same after reopening the world | |
| Craft it in survival from a repeater, a comparator and stone (recipe book or JEI-free crafting) | |

## S3 - Gathering and Soulbound

| Check | 0-2 |
|---|---|
| Look: both appear in the creative Enchanted Books list with readable names | |
| Gathering pickaxe: mined blocks go straight into the inventory; a full inventory drops them | |
| Soulbound sword and armor: die in lava, respawn, they are back (armor worn or in inventory), nothing else kept | |
| Mods > the mod > Config: both toggles and the XP option exist, change takes effect without restart | |
| Book + tool on an anvil works; Gathering shows up at an enchanting table (level 30, a few rerolls) | |

## S4 - Maple syrup and tank

| Check | 0-2 |
|---|---|
| Look: the syrup bucket and the poured syrup have textures (not missing or invisible) | |
| Pour it: flows slower and not as far as water; walking through it is slow | |
| Tank: water bucket in, empty bucket out, empty hand says what and how much | |
| Break the tank with 3 buckets in, place it again: still 3 buckets | |
| Relog: tank contents kept; a comparator beside a half-full tank shows about 7-8 | |

## S5 - Fetching wolves

| Check | 0-2 |
|---|---|
| Stick on your tamed wolf: chat says the mode, the wolf does not sit or stand up | |
| Drop 3 items with Q around you: the wolf brings them to your feet one by one | |
| A sitting wolf does not fetch; an untamed wolf and someone else's wolf ignore the stick | |
| Relog while it carries something: it still has it (and finishes or drops it) | |
| Look: the carried item is visible in its mouth, if the mod shows it (note "not shown" without scoring 0) | |

## S6 - Tool belt

| Check | 0-2 |
|---|---|
| Controls menu: the key has its own section with readable names; rebinding works | |
| Look: three belt icons near the hotbar, updated on each press | |
| Press 4 times with a sword in hand: hand goes sword, slot 3, slot 2, slot 1, sword again | |
| Relog, and Nether portal there and back: belt still there | |
| Die with keepInventory on (kept) and off (belt items drop, belt empty after respawn, no duplicates) | |

## S7 - Blast kiln

| Check | 0-2 |
|---|---|
| Build it wrong (missing brick, block in the middle): the right-click message names the problem | |
| Build it right with the controller in two different walls: "formed" both times | |
| Hoppers: ore on top, coal into the middle ring, a hopper under a bottom brick into a chest: 2 ingots per ore | |
| Break a brick mid-smelt: stops, hoppers stall; rebuild: continues with what was inside. Break the controller: drops everything | |
| Look: textures on bricks and controller; formed/smelting visuals if any (note "none" without scoring 0); Mods > Config shows multiplier and time | |

Total per prompt: 0-10. A trial that passes its acceptance tests but scores under 7 here points at what the server-side tests cannot reach (client code, assets, persistence on disk): note which line.
