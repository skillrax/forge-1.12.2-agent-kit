# Held-out trial prompts

Paste only the **Prompt** text, verbatim. The **Grade** lines are for you; never give them to the agent. None of these mods has a guide or generator option of its own: each tests whether the agent can combine general kit knowledge. Do not add a guide or generator for one of these mods after a trial; fix the general cause (AGENTS.md in this repo).

Run P0 first. If P0 fails on tooling (shell, permissions, context), fix the setup before spending time on the rest.

## P0 smoke (generators only)

**Prompt:** Make me a mod called Copper Age. I want a copper ingot, copper ore that spawns underground like iron, and a copper pickaxe and sword that are a bit better than stone but worse than iron. Smelt the ore into ingots.

**Grade:** only pickaxe + sword (`-Only pickaxe,sword`); ore smelts to ingot; tool numbers between stone and iron and written in TASK.md; `check game` PASS with no WARN.

## P1 storage block (tile entity, inventory, comparator)

**Prompt:** I want a "Coin Jar" block. Right-click it with any item to put one of that item in (up to 64 total, any mix). Sneak + right-click with an empty hand takes the last item back out. A comparator next to it should show how full it is. Hoppers should be able to pull items out of the bottom. It must keep its contents when the world reloads.

**Grade:** server-side insert/extract; NBT save; item capability exposed (at least down); comparator via `hasComparatorInputOverride`/`getComparatorInputOverride` and `markDirty` (comparator update); game test inserts, extracts and checks the comparator value.

## P2 held item with state (item ticking, NBT, sides)

**Prompt:** Make a Magnet Ring. While it's anywhere in my inventory and switched on, dropped items within 6 blocks fly towards me. Right-click with it to switch it on or off, with a message saying which. It should remember if it's on, and it shouldn't pull items that another player just dropped for 2 seconds.

**Grade:** logic in `onUpdate` on the server only; on/off in stack NBT; pickup delay respected (`EntityItem` pickup delay / thrower check); no per-tick NBT churn bobbing the item, or re-equip suppressed; game test with dropped items moving toward a FakePlayer.

## P3 world-acting machine (ticking tile, capabilities, loaded chunks)

**Prompt:** Add a Growth Totem block. Every 5 seconds it bonemeals one random crop within 4 blocks of it, using up one bone meal from its own inventory each time. Hoppers can feed it bone meal from any side, and it shouldn't accept anything else. If it has no bone meal it does nothing.

**Grade:** `ITickable`, server only, counter saved or derived from world time; insert-only handler filtering bone meal (`EnumDyeColor.WHITE` metadata 15); `ItemDye.applyBonemeal` or `IGrowable` with the real crop; area checked with `isBlockLoaded`; game test: crop grows and bone meal count drops.

## P4 custom mob (entity, AI, spawning, loot)

**Prompt:** Add an Ember Wisp mob for the Nether. It floats around slowly, is hostile to players, and sets the ground under it on fire every few seconds if it can. It has 10 hearts and drops 0-2 "ember" items when killed. I want a spawn egg for it.

**Grade:** registered `EntityEntry` with egg, Nether biome spawn; flying movement (not a reskinned zombie walking); fire placement server side and only on valid blocks; loot table JSON with count 0-2; renderer in client-only code; game test spawns it and checks health/attributes.

## P5 linked pads (saved data, dimensions, cooldowns)

**Prompt:** I want teleport pads. You link two pads with a Pad Linker: sneak-right-click the first pad, then right-click the second. Walking onto a linked pad sends you to the other one, even if it's in another dimension. Each player gets a 3 second cooldown so they don't bounce back and forth. Breaking a pad unlinks its partner.

**Grade:** link stored in tile NBT or `WorldSavedData` (both ends); cross-dimension via `changeDimension`/teleporter without nether portal side effects; per-player cooldown not in a static map that leaks or is lost wrongly; partner unlinked on break even if its chunk is unloaded (or documented limitation); game test for linking and same-dimension teleport.

## P6 player stat with HUD (persisted data, networking, client)

**Prompt:** Add a stamina system. Sprinting drains stamina, and when it hits zero I can't sprint until it recovers a bit. It refills while I'm not sprinting. Show it as a small bar above the hunger bar. It should survive dying and logging out.

**Grade:** server-authoritative value in persisted NBT or capability with clone handling; packet to the client on change (not every tick); HUD in `RenderGameOverlayEvent` client-only; sprint blocked server side (and client prediction); game test for drain/regen on a FakePlayer; tells the user the HUD needs `runClient`.

## P7 enchantment (events, recursion, tool damage)

**Prompt:** Make a "Vein Miner" enchantment for pickaxes. When you mine an ore with it, all touching ores of the same kind also break (up to 32 blocks), and the pickaxe takes damage for each one. It shouldn't work in creative-mode-only ways, like breaking ores the pickaxe couldn't normally mine.

**Grade:** `BlockEvent.BreakEvent` server side; same block state/ore check; harvest level respected (`canHarvestBlock`/`ForgeHooks`); breadth-first cap 32 with no recursion into itself (re-entrancy guard); drops and XP via `harvestBlock` or `destroyBlock` with player; tool damage per block and stops when the tool breaks; game test with a vein of ores.
