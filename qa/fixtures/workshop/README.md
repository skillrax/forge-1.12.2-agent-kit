# Kit Capability Workshop0.7.0

Stage6 adds ore generation, a small marker/chest, a crop and a harvest advancement. The earlier launcher and targeted reset remain included. The persistent Test Trader retains the vanilla villager model/texture and trading menu. Replace your previous Workshop version with0.7.0; keep Clockwork installed. Box, processor and pouch remain included with their existing IDs and data formats.

```text
/summon kitworkshop:test_trader ~ ~ ~
/give @p minecraft:apple 64
```

Spawn it on open, solid ground. It wanders slowly, stops walking during a trade, and exchanges3 apples for1 emerald. One customer per NPC; a second player sees "Trader is busy." Close the menu to hand it over, or spawn another NPC for simultaneous independent trades. The offer has1000 uses per NPC, no restocking, extra rewards or natural spawning. NPC name/location/trade uses save with its chunk. Stage4 adds the per-player quest below.




## Stage7: Test Dimension and portal

Replace the previous Workshop JAR on the server and every client; keep Clockwork installed. Earlier trial features and saved IDs remain included. This stage adds a flat grass world (grass at y63, dirt/stone below, bedrock at y0) with ordinary Plains/vanilla sky, and a full-cube Test Portal with vanilla obsidian texture. It is a simple right-click travel block; no portal frame or automatic walking/collision activation is required.

```text
/give @p kitworkshop:test_portal 2
```

Place a Test Portal in the Overworld on open ground. Right-click with your main hand (empty hand is easiest). Everyone can use the block. You arrive near2 64 0 in the Test Dimension; the generated return portal is at0 64 0. Right-click that portal to return near your own last entry position. Multiple players share this dimension but keep separate saved return points. Each successful trip has a40-tick cooldown (2 seconds at20 TPS); mounts/passengers and open menus prevent travel. Portals in the Nether/End or other dimensions do not activate. Travel preserves inventory/health and does not consume the portal.

Landing searches within8 horizontal blocks of the preferred point, first around its height and then at the column surface. It requires a full supported floor, clear feet/head space and a point inside the world border; wood/leaf floors and magma/cactus are deliberately excluded. If no candidate exists, travel is refused without clearing blocks or building a platform. A blocked saved return point can use a nearby safe position. Players who arrived through outside commands without saved return data use an Overworld spawn-area search. This is bounded, conservative placement; hazardous neighboring blocks, other entities and every modded hazard are not comprehensively modeled.

The origin return portal generates once with its chunk. Breaking it or building at the landing area stays saved; entering again does not rebuild your edits. You can place another Test Portal anywhere in this dimension to return. Keep a spare for repeatable tests. Beds are not enabled as respawn points here; vanilla bed use can explode when a provider cannot respawn. Death uses the Overworld respawn policy; inventory loss follows ordinary game rules. No custom biome, terrain noise, sky shader, automatic portal linking, entity/item transport or recipe is included.

### Dimension checklist

1. Enter and return three times, waiting2 seconds between trips. Check the flat terrain, normal rendering/chunk loading and generated origin portal. Return should be near your entry point, without suffocation, falling or an immediate bounce back.
2. Carry several named/countable items. Check inventory/health after both directions. Try clicking immediately again; cooldown should keep you in the current world. Dismount before travel.
3. Two players enter from portals far apart in the Overworld. Meet in the same Test Dimension, then return independently to each player's own entry location. Repeat with both travelling around the same time.
4. In the Test Dimension, place some blocks and a chest with known contents away from the origin portal. Move away/reload chunks; log out and back in while still there; restart the world/server while still there. Check the dimension, edits, chest contents and inventory remain, then return to the correct Overworld location.
5. While one player is away, have another obstruct that player's original return cell. Return should choose a supported nearby clear cell and preserve the obstruction. If a whole search region has no safe ground/space, the chat should refuse travel. Do not destroy every return option unless you have a spare portal/operator access for the test.
6. Break/remove the origin portal, move away and revisit/restart: it must stay removed. Use a spare portal to return; the dimension must not regenerate the old portal or erase your edits.
7. Optional death/respawn check: use an expendable inventory or enable keepInventory for this trial, then die inside the Test Dimension. Expect Overworld respawn, with normal bed/spawn and inventory rules; return portal use should still work on a new entry. Avoid using a bed inside this dimension.
8. Briefly recheck an earlier menu/NPC/quest/projectile feature, including items carried through the portal. Report any client crash/disconnect, wrong dimension/return, missing items or reverted world edits.

### Dimension ID

Default dimension and provider type ID is72. `config/kitworkshop.cfg` contains `dimension.dimensionId` (range2..10000). The server and every client must use the same free ID. A startup collision produces an explicit error rather than replacing another mod's dimension; choose a free ID before creating this trial world. **Never change the ID for an existing saved world**, since it identifies the dimension's saved data. Keep the same configuration when moving this world to another PC. Standard vanilla dimension storage and Forge registration are used; no automatic config/network synchronization is added.

Native assertions exercise the actual registered provider/generator, ordinary server-player changeDimension/ITeleporter transfers, independent saved returns, cooldown/menu/mount guards, conservative blocked landing and adjacent return fallback, plus vanilla world/player disk persistence across JVMs. Connections are stubs: the installed JAR's client respawn/chunk packets, rendering, actual logout/login, simultaneous connected-player travel and actual death/respawn require your trial. These assertions do not certify arbitrary custom terrain or portal types.
## Patch0.6.2: previous marker placement fix (accepted)

Replace the Workshop JAR on the server and every client; keep Clockwork installed. The previous crop/trampling repair was user-confirmed. For this placement repair use unexplored Plains terrain or a fresh default world, then `/workshopsite`.

1. Find a newly generated marker. It should stand on a level3x3 grass/dirt/stone surface with no tree canopy/trunk or unsupported corner beneath it. Confirm chest contents and crop still work.
2. Repeat at a few sites, including Plains near trees and uneven terrain. Unsuitable locations are skipped; the locator may report no site within its budget. Move farther into unexplored Plains and try again.
3. Check matching placement in multiplayer and that edits/chest contents remain after restart.

This patch validates all9 support blocks and the3-block headroom before making marker edits. It intentionally skips trees/obstacles and slopes; it does not relocate structures, remove trees, flatten hills or rebuild already populated terrain. Existing tree-top markers remain unchanged. Ore generation is independent and remains enabled in eligible chunks even if marker placement is rejected.

## Patch0.6.1: previous crop fix (accepted)

Reuse your existing world; replace the Workshop JAR on the server and every client. Keep Clockwork installed. No new terrain is needed.

1. Plant an immature Test Crop on farmland, then jump until the farmland becomes dirt. The crop must disappear and return exactly1 Test Seed, with no Test Harvest.
2. Bonemeal the resulting dirt/empty space: the crop must not regrow. Dirt must stay dirt. Till it again before replanting.
3. Repeat with a fully grown crop: expect1 Test Harvest and0..3 Test Seeds. Check the soil stays dirt after breaking/removing the plant and after reconnect/restart.
4. Repeat in multiplayer and check both players see the same plant removal and soil state.

The patch explicitly classifies Test Crop as Forge plant type Crop. The inherited Forge1.12.2 method recognizes vanilla crops by identity and otherwise classifies this custom block as Plains, allowing dirt support. Test Ore intentionally retains its fixed1-emerald drop without XP or Fortune scaling; this establishes generation/basic block drops, not full vanilla ore behavior.

## Stage6: world content

Use a fresh **default Overworld** for the quickest generation trial, with cheats enabled. Existing worlds can use unexplored terrain; already populated chunks are not retroactively changed. All earlier Workshop features remain included. Keep Clockwork installed.

### Locate a generated site

```text
/gamemode 1
/workshopsite
```

The operator-only locator prints marker coordinates, a teleport command, and one remaining Test Ore coordinate in that site's chunk. It explicitly requests up to4 candidate groups of4 chunks; normal Minecraft population may also load neighbors. It does not rebuild old sites or teleport you automatically. If no site is found, try new Plains terrain or a fresh default world. Use the printed teleport command in Creative to inspect the site, and the ore coordinate to inspect the underground vein.

Generation is limited to Overworld chunks whose center biome is ordinary Plains. Each new eligible chunk attempts3 small stone-replacing ore veins, with centers at y8..31. Every8th chunk on both axes can have a small3x3 stone-brick marker, a gold cap, a chest, a watered farmland/crop patch. Trees, uneven ground and blocked footprints prevent a marker. These are intentionally simple test structures, not Minecraft /locate-registered structures or a custom biome.

### World-content checklist

1. Locate a site in new terrain. Check the stone-brick/gold marker, chest and crop. Check Test Ore at the printed coordinate looks like vanilla emerald ore but has its own block name. Mine it in Survival with an ordinary iron pickaxe: expect1 emerald; use no Silk Touch/Fortune for this test.
2. Open the new chest: expect exactly2 emeralds and3 apples, potentially split across slots. Remove some or all loot. Mine an ore block. Log out/in, leave/reload the chunks, and restart the world/server: the edits must remain, without regenerated ore or refilled loot.
3. Harvest the site's crop after it matures, or plant your own using the commands below. Test Seeds plant only on farmland with an empty space above. Test Crop has8 wheat-looking growth stages, grows like vanilla wheat and accepts bonemeal. Natural growth takes time; bonemeal is the quick test. Mature harvest should drop1 Test Harvest and0..3 Test Seeds; immature crops should return seeds without Test Harvest. Test Harvest is a plain resource, without food/recipe behavior.
4. Save/reload a planted crop and verify its growth stage remains. Plant and harvest again to check the cycle can repeat.
5. Obtain a Test Harvest item in your inventory. Check the Test Harvest advancement/toast (also visible in Advancements). Two players should unlock it independently; another player obtaining the item should not complete yours. Check completion survives logout/restart.
6. Remove Test Harvest items from your inventory, then use `/workshopreset` or `/workshopreset OnlinePlayerName`. Verify the trial advancement is revoked and obtaining the item again unlocks it. Reset preserves inventory and other advancements/player data. Because this is a vanilla inventory predicate, retaining Test Harvest can unlock it again on the next inventory change; remove it first for repeatable tests.
7. In multiplayer, check both players see the same generated blocks, chest contents and crop stage. Chest loot is shared, not generated anew for each player. Briefly recheck earlier menus, trading, quest, projectile and reset command.
8. Optional: inspect newly generated non-Plains/Nether terrain; this mod should not generate its ore/markers there. Old edited sites should stay edited on later visits.

```text
/give @p kitworkshop:test_seeds 16
/give @p minecraft:dye 16 15
/give @p minecraft:iron_pickaxe 1
```

Till ordinary dirt/grass with a hoe, keep water nearby, and use Test Seeds. Dye metadata15 is bonemeal in1.12.2. To isolate advancement testing, `/give @p kitworkshop:test_harvest 1` can also supply the item.

Native QA exercises the registered Forge population callback in fresh flat-world chunks prepared with stone and controlled biomes, not naturally generated default terrain. It verifies ore/marker/loot, crop API growth hooks/bonemeal/harvest, advancement/reset and vanilla disk persistence/no regeneration. Crop natural random selection, default-world visuals/placement and connected multiplayer remain your client trial.

## Stage5: projectile and repeatable player tests

Replace the previous Workshop JAR with0.7.0; keep Clockwork installed. All previous blocks, pouch, NPC and quest remain included.

```text
/give @p kitworkshop:test_launcher 1
```

The Test Launcher has a vanilla snowball icon. Hold it in your main hand and right-click into the air to fire a custom projectile rendered as a vanilla snowball. It is reusable, uses no ammunition and has a10-tick (half-second at20 TPS) cooldown per player. The projectile travels with vanilla throwable gravity/drag, deals4 damage points (2 hearts before armor/effects) and stops on an entity or block hit. It does not explode, change blocks or drop an item. It expires after60 active server ticks (3 seconds at20 TPS). Unloaded chunks do not continue ticking; its saved flight age is retained when loaded. Vanilla damage rules, armor, invulnerability and server PvP settings apply. No homing, combat AI, custom particles or sound are included.

### Projectile test checklist

1. Give yourself the launcher. Check its name/icon, aim at open ground and right-click. Observe one snowball-like projectile traveling forward and falling. It should disappear on the ground; neither launcher nor ammunition is consumed.
2. Fire at a wall, then at an unarmored Survival mob (the Test Trader works). Stand several blocks away so right-click does not open the trader menu. Check wall blocks stay intact and the projectile disappears. A successful hit should deal2 hearts before mitigation; five well-spaced hits should kill a fresh unarmored Test Trader; damage flashes/knockback may vary with vanilla rules. Left-click melee damage is outside this launcher test.
3. Shoot at a mob behind a solid wall: the wall should stop the shot without damaging the mob. Aim high into open sky: a missed shot should disappear after roughly3 seconds of active ticking.
4. Repeatedly click: the cooldown should limit firing, and one click should not create duplicate shots. Try offhand-only use; this trial supports the main hand.
5. Two players shoot independently and observe each other's shots/impacts. Reconnect and retry. If testing player damage, use Survival and check your server's PvP setting; confirm PvP-off blocks player damage. Creative players are normally immune.
6. Briefly recheck normal trader interaction, quest, pouch and block menus for regressions. Report invisible projectiles, ghost impacts, duplicate damage or crashes with steps and logs.

### Reset quest progress on the same accounts

With cheats enabled or operator permission:

```text
/workshopreset
/workshopreset OnlinePlayerName
```

The first resets your own quest; the second resets that one online player's quest. In0.6.1 it clears Workshop quest progress and the Test Harvest advancement, and sends feedback. It does not change inventory, revoke old diamonds or refund delivered cobblestone; completing the quest again gives another test reward. Existing full-inventory behavior is unchanged: final delivery requires reward space before consuming cobblestone, even when payment would free a slot.

1. Complete or partially submit a quest, reset it, then sneak-right-click a trader: see a fresh acceptance and repeat the same scenario using the same account.
2. Reset one of two players. Check the other's progress and both inventories remain unchanged. Reset progress should remain reset after logout/login and save/restart; it should also survive death/respawn.
3. In multiplayer, verify a non-operator cannot use the reset command. An invalid/offline target should report failure and leave the sender/others unchanged. A dedicated-server console must specify an online player.

Server QA covers naturally ticked projectile movement/collisions/damage/owner attribution/expiry, flight-age NBT and command registration/permission/targeting/reset/replay/player persistence. Real projectile rendering, observer synchronization, PvP rules and installed command interaction require this client/multiplayer trial.

## Stage4: saved per-player quest

Sneak-right-click any Test Trader with an empty main hand to accept the Test Quest. Chat explains it: deliver8 cobblestone for1 diamond. Sneak-right-click again to submit cobblestone from your main inventory/hotbar. Partial deliveries are accepted; the first acceptance click consumes nothing. Normal right-click still opens trading.

Progress belongs to the player across all traders, independently of other players. It survives logout/login, world/server restart and death/respawn. After completion, further clicks only report that the reward was claimed. This quest is once per player in this world; replacing/killing the NPC does not reset it. Stage5 adds the operator-only reset command below; no custom UI or other quests are included.

Final delivery requires an empty main-inventory slot or room in an ordinary diamond stack before consuming payment. With no reward space, progress/payment remain unchanged; make room and retry. Close any open inventory/trading menu first. Use Survival for the main test; Creative still consumes delivered cobblestone.

### Quest test checklist

1. Summon a trader with `/summon kitworkshop:test_trader ~ ~ ~`. Empty your main hand and sneak-right-click: see acceptance in chat. Repeat with no cobblestone or only dirt: no progress/reward. Normal right-click should still trade apples.
2. Put3 cobblestone in your inventory, empty your hand and sneak-right-click: chat says3/8 and consumes exactly3. Log out/in or save/restart, then click with no cobblestone: progress should still be3/8.
3. Die and respawn while partially complete, then check the same progress with no cobblestone. Summon another trader and verify it recognizes that progress too.
4. Bring7 cobblestone. Delivery should consume only5, leave2 and give exactly1 diamond. Further clicks, another trader, death/respawn and reconnect/restart must not grant another reward or consume more cobblestone.
5. On a second player's incomplete quest, fill all36 main-inventory slots with full unrelated stacks plus enough cobblestone to finish. Final delivery should leave payment/progress untouched. Free a slot or make room in a diamond stack and retry.
6. Two players accept/deliver at the same time, using the same or different NPCs. Each should have independent progress and receive at most1 diamond. Briefly recheck normal merchant trading and previous box/processor/pouch features.

Server QA exercises actual player replacement and separate-JVM vanilla player save/load, payment/reward accounting and repeated completion. These quest checks were user-accepted with4 players in0.4.0; rerun relevant cases using the reset command if needed.

## NPC test checklist

1. Summon a trader. Check its model/name and wandering; right-click normally to open the familiar trading menu.
2. Try2 apples and an unrelated item: no emerald should be obtainable. Trade3 apples normally, then repeat/shift-click with more apples; verify exact costs/results. Vanilla shift-click from your inventory moves between hotbar/main inventory; place the payment into a trade slot yourself.
3. Fill your inventory and shift-click the result. Payment should stay unspent if the emerald cannot fit. Remove unused input before closing, or collect the items vanilla drops when a trade menu closes.
4. Two players try the same trader: only one menu should be active. Close it and let the other trade. Try separate traders at the same time.
5. Walk/teleport away, kill the trader or log out with its menu open. The session should close/release; unpaid input should not vanish. Another player should be able to use a surviving trader after logout.
6. Save/restart the world/server and check the same named trader remains, with a working offer. Briefly recheck the box, processor and pouch.

Server assertions cover actual NPC ticks/movement, registered spawning/tracking foundations, merchant clicks/accounting, customer/range/death/logout cleanup and actual chunk persistence. Real model rendering, trade-list packet reception and two connected clients require this installed trial.

The included three-slot Test Pouch uses a vanilla leather icon. Its previous client/multiplayer trial passed; the checklist below is available for regression checks. Existing box/processor/pouch IDs and saved data remain unchanged.

```text
/give @p kitworkshop:pouch 1
```

Hold the pouch in the main hand and right-click in the air. Pouches do not stack. Each has independent saved contents; the active hotbar slot is locked while its menu is open. Other pouches and shulker boxes cannot go inside. Offhand opening is outside this simple trial.

## Pouch test checklist

1. Get two pouches. Put different items in each, close/reopen and verify independent counts.
2. Test ordinary clicks, shift-click both directions, dragging stacks across the three slots and number-key swaps with other hotbar slots. Try extracting into a full inventory; items should stay in the pouch.
3. Try moving, shift-clicking, dropping or number-key swapping the active pouch while its menu is open; its slot should stay locked. Pouches/shulker boxes should be rejected as contents.
4. Close the menu, move the pouch to another slot, reopen it, then drop/pick it up and give it to another player. Contents should travel with that pouch.
5. Save/quit and reopen with populated pouches. In multiplayer, also log out/back in. Verify both contents and independent players' pouches.
6. Try changing the selected hotbar slot while the menu is open if your controls allow it. The server should close an invalid session and prevent further pouch edits. Do a brief box/processor check to catch regressions.

The server assertions cover click accounting, nesting/active-slot restrictions, stale-session closure, entity NBT round-trip and two distinct pouches saved/restored through vanilla player data. These pouch cases were also user-accepted in the preceding installed multiplayer trial.

Private barebones Minecraft1.12.2 capability trial, built with toolkit0.3.0-alpha.3 / Forge14.23.5.2859 / stable39 / Java8. No new dependency. It can coexist with Clockwork Bottle: separate mod ID kitworkshop. Do not install multiple Workshop versions together.

Two blocks in the Decorations creative tab:

- Test Storage Box: oak-plank cube; nine inventory slots accepting ordinary items.
- Test Processor: iron cube; left slot accepts cobblestone, right slot is output only. Converts one cobblestone to one stone every100 server ticks (five game seconds at20 TPS). No fuel or experience. Full/incompatible output pauses progress; empty input resets progress. Contents and partial progress save with the chunk. This is a test mechanism, not balanced survival content.

The menus use plain rectangles and vanilla item icons. There are no recipes, custom textures, sounds or client-to-server custom packets. Use Creative or cheats:

```text
/give @p kitworkshop:storage_box
/give @p kitworkshop:processor
/give @p minecraft:cobblestone 64
```

## Short installed-game checklist

1. Place both blocks, right-click each and check names, slots and your inventory appear without a crash.
2. Put several different stacks in the box. Move them with ordinary clicks and shift-click in both directions; close/reopen and check the counts.
3. Put cobblestone in the processor's left slot. Check the progress bar, one stone per five game seconds, and exact input/output counts. Try inserting dirt into input or placing an item in output; both should be rejected.
4. Shift-click cobblestone in and stone out. Try with your inventory full; items should stay safely in the output.
5. Fill output to64 stone. Processing should pause without using input. Remove some output; it should resume.
6. Leave items in both blocks, with the processor partway through work. Save/quit and reopen the world. Box contents, processor contents and partial progress should persist. To hold partial progress steady for this check, fill its output before quitting.
7. In Survival, break each block while it has items. Check its contents drop exactly once. Replacing it should create an empty inventory.
8. Optional: use a hopper above the processor for cobblestone and a hopper below for stone. Two players can also open the same box to check consistency.

Report crashes, incorrect counts, menu/texture problems or failed persistence with steps to reproduce. Exact Forge version and a crash log help identify compatibility failures. NPC visuals/trading and multiplayer were accepted in0.3.0. The quest was accepted with4 players in0.4.0. Projectile/reset tests were accepted in0.5.0; World-content generation, loot, persistence, growth and advancement passed the user multiplayer trial; the0.6.1 trampling repair was user-confirmed; the0.6.2 marker placement repair awaits new-terrain testing.

Production source is src/main. QA acceptance code, its persistent test world, reports, deliberate faults and build overlay are excluded from this JAR/source distribution. The source kit builds with agent.ps1 -Action check -Profile mod; it contains no ordinary unit tests. Maintainer server assertions provide the runtime evidence separately.
