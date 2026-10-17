# Checks and game tests

| Check | Proves | Time |
|---|---|---|
| `check` (= build) | compiles; jar has an @Mod, Java 8 bytecode, valid JSON, versions in sync; unit tests pass if any | ~10 s |
| `check server` | build + dedicated server starts and stops cleanly + **lint**: every registered item/block/entity/sound has its translation, model, textures and blockstate variants, enchantments and effects their translation; tile entities registered | ~25 s |
| `check game [-Test a,b]` | server + your `@GameTest` scenarios | ~25 s + test time |
| `check unit` | JUnit 4 tests in src/test/java (plain Java logic only; no Minecraft registries) | ~10 s |
| `check environment` | toolchain and Minecraft API compile | ~10 s |

Output is a few lines: `PASS|FAIL <profile> (<s>): summary`, then problems. `errors` reprints the last result; `errors -Full` adds the log tail. Lint lines are `WARN`/`ERROR <subject>: <message>`; ERROR fails the check, WARN means something looks broken in the client (missing texture, raw translation key). Fix or deliberately justify every WARN.

Nothing here can see the screen. GUI layout, model appearance and animations need the user (`gradlew.bat runClient`); say so when reporting.

## Game tests

Generators already append checks for what they create to `GeneratedContentTest.java` (recipes, smelting, ore drops, tool/armor/food/mob stats, mob eggs, drops and spawns); `check game` runs them. Write your own tests for behavior: `./agent.ps1 new test <id>` writes a scaffold in src/gametest/java (its placeholder check fails until you replace it). A test is a class with `@GameTest("id")` implementing `Scenario`. `plan(t)` declares steps; they run on real server ticks in a fresh flat world, each test in its own cleared 16x16 area with a stone floor at y=3.

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
| `check(name, () -> cond, () -> detail)` | named assertion step (names: lowercase-with-dashes, unique) |
| `waitTicks(n)`, `waitUntil(name, maxTicks, cond, detail)` | let the world run |
| `invariant(name, cond, detail)` | must hold every tick from here on |
| `origin()`, `world()`, `server()`, `ticks()` | fixture area, WorldServer, server, elapsed ticks |
| `place(pos, "modid:block")`, `place(pos, state)`, `state(pos)`, `tile(pos, Class)`, `inventory(pos)` | blocks and tile entities |
| `item(id)`, `block(id)`, `stack(id, count)`, `spawn("modid:entity", pos)` | registry lookups (`id@meta`, `ore:name` work in `stack`), entities (the test server runs `spawn-animals=false`, so animals vanish on their next tick: use an armor stand or a mob) |
| `craft("a", "", "b", ...)`, `smelt(id)` | crafting result of a row-major 3x3 grid ("" = empty), furnace result |
| `drops(blockId, toolId)`, `count(stacks, id)` | place + mine a block with a tool in a free spot, return what dropped; count one item |
| `player(name)` | survival FakePlayer at origin, facing south; mobs cannot see or hurt it |
| `targetPlayer(name)` | a player mob AI targets and damages (in the world's player list; removed when the test ends). It never ticks, so after a hit `hurtResistantTime` stays up: set it to 0 to count several hits |
| `loot(table)`, `loot(table, entity)` | one roll of a loot table (`"modid:entities/name"`); loop and `count` for ranges like "0-2" |
| `useItem(p, hand)`, `finishUsingItem(p, hand)`, `useOnBlock(p, pos, face, hand)`, `breakBlock(p, pos)`, `attack(p, entity)` | real interaction code paths. `useOnBlock` with an empty hand returns PASS even when `onBlockActivated` ran (vanilla): assert on the effect, not the result |
| `messages(p)` | chat and action-bar messages sent to `p`, oldest first (`"actionbar: "` prefix; translated ones end in `[key]`) |

Default budget is 1200 ticks per test: `@GameTest(value = "id", maxTicks = 6000)`. Signatures and bodies: `agent.ps1 api TestContext[.member]` (no need to read the whole file).

Things that bite:
- Only the lambdas passed to `then`, `check`, `waitUntil` and `invariant` run on ticks. Other code in `plan()` runs once at tick 0, before any step: compute and assert inside the lambdas.
- One behavior per `@GameTest`: each test gets its own cleared area, so setups cannot disturb each other, and `check game -Test id` reruns one. Steps inside one test share the area: keep separate setups 3+ blocks apart, and remember redstone carries (a comparator powers the block it faces away from, and that block powers its neighbors, such as a hopper).
- The test area ticks like a loaded world: tiles, hoppers and redstone run. A hopper moves one item per 8 ticks; `enabled=false` in its state means it is receiving redstone power (the default is enabled) and it moves nothing.
- A failed test also prints `area` lines: every block the test placed and any other block in its area (x,y,z from `t.origin()`), with its state, redstone it receives and inventory contents, then entity counts. Read them before adding instrumentation; make your own detail print counts the area lines do not show.
- The same failure 3 checks in a row prints `STUCK`: isolate the behavior in its own test, then report (AGENTS.md step 7) instead of guessing on.
- FakePlayer never ticks: item use timers, attack cooldown (hits do ~20% damage) and potion countdowns on it do not advance. Call `finishUsingItem` for food. Held-item behavior (`Item.onUpdate`, for anything "while in the inventory") never runs by itself: call `stack.getItem().onUpdate(stack, t.world(), p, slot, selected)` each tick from a `waitUntil` or `invariant`.
- `t.player` is not in the server's player list: command selectors, name lookups and mob targeting (`getNearestAttackablePlayer` searches only that list) never find it, and `player.openGui` does nothing. Use `targetPlayer` for hostile mobs; test containers by constructing them directly.
- Random ticks run (crops grow, grass spreads, leaves decay), so "X did not happen" can fail by chance. Assert on state your code owns (a counter, an inventory) instead of on world noise.
- The test world is always day: zombies and skeletons burn. Use other mobs, or give them fire resistance, when measuring health.
- Other dimensions exist: `t.server().getWorld(-1)` (Nether) and `getWorld(1)` (End) load on demand, and `entity.changeDimension(dim)` works, so cross-dimension behavior can be tested.
- Each run uses a new world, so save/load across restarts is not tested. A tile's `writeToNBT`/`readFromNBT` round trip can be checked directly.
