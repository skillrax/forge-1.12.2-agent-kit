# Checks and game tests

| Check | Proves | Time |
|---|---|---|
| `check` (= build) | compiles; jar has an @Mod, Java 8 bytecode, valid JSON, versions in sync; unit tests pass if any; calls to client-only members (ERROR in game tests, WARN in common code); `spawnEntity` in a both-sides hook without an `isRemote` test (WARN) | ~10 s |
| `check server` | build + dedicated server starts and stops cleanly + **lint**: every registered item/block/entity/sound has its translation, model, textures and blockstate variants, enchantments and effects their translation; tile entities registered; recipes whose ingredients match nothing | ~25 s |
| `check game [-Test a,b]` | server + your `@GameTest` scenarios | ~25 s + test time |
| `check unit` | JUnit 4 tests in src/test/java (plain Java logic only; no Minecraft registries) | ~10 s |
| `check environment` | toolchain and Minecraft API compile | ~10 s |

Output is a few lines: `PASS|FAIL <profile> (<s>): summary`, then problems. `errors` reprints the last result; `errors -Full` adds the log tail. Lint lines are `WARN`/`ERROR <subject>: <message>`; ERROR fails the check, WARN means something looks broken in the client (missing texture, raw translation key). Fix or deliberately justify every WARN.

Nothing here can see the screen. GUI layout, model appearance and animations need the user (`gradlew.bat runClient`); say so when reporting.

## Game tests

Generators already append checks for what they create to `GeneratedContentTest.java` (recipes, smelting, ore drops, tool/armor/food/mob stats, mob eggs, drops and spawns); `check game` runs them. Write your own tests for behavior: `./agent.ps1 new test <id>` writes a scaffold in src/gametest/java (its placeholder check fails until you replace it). A test is a class with `@GameTest("id")` implementing `Scenario`. `plan(t)` declares steps; they run on real server ticks in a fresh flat world, each test in its own cleared 16x16 area with a stone floor one block below `t.origin()`.

```java
@GameTest("ore-drops-ruby")
public class OreDropsTest implements Scenario {
    @Override public void plan(TestContext t) {
        BlockPos pos = t.origin().add(2, 0, 2);
        t.then("mine it", () -> {
            t.place(pos, "mymod:ruby_ore");
            FakePlayer p = t.player("miner");
            p.setHeldItem(EnumHand.MAIN_HAND, new ItemStack(Items.IRON_PICKAXE));
            t.require(t.breakBlock(p, pos), "not broken");
        });
        t.check("ruby-dropped", () -> t.world().getEntitiesWithinAABB(EntityItem.class, new AxisAlignedBB(pos).grow(2))
                .stream().anyMatch(e -> e.getItem().getItem() == ModItems.RUBY), () -> "no ruby item entity");
    }
}
```

| TestContext | Use |
|---|---|
| `then(label, () -> {...})` | run code as the next step; use `require(cond, msg)` inside |
| `check(name, () -> cond, () -> detail)` | named assertion step (names of `check`, `waitUntil`, `invariant`: lowercase-with-dashes, unique; `then` labels are free text) |
| `waitTicks(n)`, `waitUntil(name, maxTicks, cond, detail)` | let the world run |
| `invariant(name, cond, detail)` | must hold every tick from here on |
| `origin()`, `world()`, `server()`, `ticks()` | fixture area (16x16 stone floor at y -1; origin is the air block above its corner), WorldServer, server, elapsed ticks |
| `place(pos, "modid:block")`, `place(pos, state)`, `state(pos)`, `tile(pos, Class)`, `inventory(pos)` | blocks and tile entities |
| `item(id)`, `block(id)`, `stack(id, count)`, `spawn("modid:entity", pos)` | registry lookups (`id@meta`, `ore:name` work in `stack`), entities (the test server runs `spawn-animals=false`, so animals vanish on their next tick: use an armor stand or a mob) |
| `craft("a", "", "b", ...)`, `smelt(id)` | crafting result of a row-major 3x3 grid: all 9 cells ("" = empty), or only the ingredients of a shapeless recipe; furnace result |
| `drops(blockId, toolId)`, `count(stacks, id)` | place + mine a block with a tool in a free spot, return what dropped; count one item |
| `player(name)` | survival FakePlayer at origin, facing south (same name = same UUID); mobs cannot see or hurt it |
| `targetPlayer(name)` | a player that mob AI targets and that takes damage from mobs, players and their projectiles (in the world's player list; removed when the test ends). It never ticks, so after a hit `hurtResistantTime` stays up: set it to 0 to count several hits |
| `loot(table)`, `loot(table, entity)` | one roll of a loot table (`"modid:entities/name"`); loop and `count` for ranges like "0-2" |
| `useItem(p, hand)`, `finishUsingItem(p, hand)`, `useOnBlock(p, pos, face, hand)`, `breakBlock(p, pos)`, `attack(p, entity)` | real interaction code paths. `useOnBlock` is a real right-click: it returns PASS whenever the hand is empty afterwards (empty hand, or your `onBlockActivated` used up the last item), and when `onBlockActivated` returns false the held item's own use runs, so a held block item is placed against the target. Assert on the effect, and hold a non-placeable item (a stick) to test "nothing happens" |
| `messages(p)` | chat and action-bar messages sent to `p`, oldest first (`"actionbar: "` prefix; translated ones end in `[key]`) |

Default budget is 1200 ticks per test: `@GameTest(value = "id", maxTicks = 6000)`. Signatures and bodies: `agent.ps1 api TestContext[.member]` (no need to read the whole file).

Things that bite:
- Only the lambdas passed to `then`, `check`, `waitUntil` and `invariant` run on ticks. Other code in `plan()` runs once at tick 0, before any step: compute and assert inside the lambdas. Steps run back to back in the same tick until a `waitTicks`/`waitUntil`: something spawned in one `then` has not moved yet in the next `check`.
- The test server never translates: assert on translation keys (`messages` ends translated text in `[key]`), not on English text.
- A bow-style release: `stack.getItem().onPlayerStoppedUsing(stack, t.world(), p, stack.getMaxItemUseDuration() - chargeTicks)`.
- One behavior per `@GameTest`: each test gets its own cleared area, so setups cannot disturb each other, and `check game -Test id` reruns one. Steps inside one test share the area: keep separate setups 3+ blocks apart, and remember redstone carries (a comparator powers the block it faces away from, and that block powers its neighbors, such as a hopper).
- The test area ticks like a loaded world: tiles, hoppers and redstone run. A hopper moves one item per 8 ticks; `enabled=false` in its state means it is receiving redstone power (the default is enabled) and it moves nothing.
- A failed test also prints `area` lines: every block the test placed and any other block in its area (x,y,z from `t.origin()`), with its state, redstone it receives and inventory contents, then entity counts. Mobs also show health, burning and hurt cooldown. Read them before adding instrumentation; make your own detail print counts the area lines do not show.
- A test that fails at a step, passes, then fails there again prints `FLAKY` (world noise, not your last change). The same failure 3 checks in a row, or one test failing 5 checks in a row, prints `STUCK`: isolate the behavior in its own test, then report (AGENTS.md step 7) instead of guessing on.
- FakePlayer never ticks: item use timers, attack cooldown (hits do ~20% damage) and potion countdowns on it do not advance. Call `finishUsingItem` for food. Held-item behavior (`Item.onUpdate`, for anything "while in the inventory") never runs by itself: call `stack.getItem().onUpdate(stack, t.world(), p, slot, selected)` each tick from a `waitUntil` or `invariant`.
- `t.player` is not in the server's player list: command selectors, name lookups and mob targeting (`getNearestAttackablePlayer` searches only that list) never find it, and `player.openGui` does nothing. Use `targetPlayer` for hostile mobs; test containers by constructing them directly.
- Random ticks run (crops grow, grass spreads, leaves decay), so "X did not happen" can fail by chance. Assert on state your code owns (a counter, an inventory) instead of on world noise.
- The test world is always day: zombies and skeletons burn. Use other mobs, or give them fire resistance, when measuring health. After any damage a mob ignores hits for its hurt cooldown (~10 ticks), and an arrow-type projectile bounces off instead of hitting: hit fresh mobs, one hit per mob or wait it out. `spawn` skips `onInitialSpawn`, so mobs have no equipment.
- Other dimensions exist: `t.server().getWorld(-1)` (Nether) and `getWorld(1)` (End) load on demand, and `entity.changeDimension(dim)` works, so cross-dimension behavior can be tested.
- Each run uses a new world, so save/load across restarts is not tested. A tile's `writeToNBT`/`readFromNBT` round trip can be checked directly.
