# Clockwork Bottle 1.1.0

Feature extension of the accepted1.0.1 mod, built on toolkit0.3.0-alpha.3. Minecraft1.12.2, Forge14.23.5.2859 build baseline, Java8 bytecode. No extra library/mod dependency. Replace1.0.1 with the1.1.0 SRG JAR; keep the existing registry ID clockworkbottle:clockwork_bottle and saved bottle charge. Keep a copy of your test world.

## New use

Hold the bottle in the main hand and shift-right-click a vanilla furnace. First use sets2x speed for15 normal game seconds, costing30 seconds of stored charge. Repeat while active:4x costs60,8x costs120,16x costs240, etc. Every successful use sets the new multiplier and refreshes the same15-second duration. The sequence belongs to the furnace; another player's subsequent activation pays the next cost too. Failure preserves balance, speed and remaining duration. After expiry, the next use starts again at2x/30 seconds.

Fuel burning and cooking both run faster through the vanilla furnace update. Normal game ticks define duration; lag slows game seconds. No fuel-efficiency bonus or extra output per input. Boosts cancel on target replacement, chunk unload, last activator disconnect or server stop/restart, without refund. No chunk is loaded to keep a boost running. Ordinary Night Vision still works with an unsneaking main-hand right-click; sneaking air-use does not give Night Vision or spend charge.

Only vanilla furnace/lit-furnace blocks with the exact vanilla TileEntityFurnace are supported. Costs are bounded by the existing60-hour bottle cap; the maximum individually affordable request is8192x. Across active worlds, at most8192 extra furnace updates per server tick are reserved. If increasing a boost would exceed that budget, the game reports a limit and spends nothing. This caps scheduled work; high-tier/many-player performance has not been stress-tested.

## Recipe

```text
Diamond   Clock       Diamond
Diamond   Gold ingot  Diamond
Diamond   Glass bottle Diamond
```

The old three-ingredient recipe is removed. The item remains nonstackable with its experience-bottle placeholder texture and selective re-equip suppression. Main inventory/hotbar/offhand charge normally; chest/drop/offline locations do not.60-hour cap and Night Vision's15/30/60/120-second progression remain.

## Suggested installed-game checks

For quick testing with cheats:

```text
/give @p clockworkbottle:clockwork_bottle 1 0 {ClockworkBottle:{ChargeTicks:72000}}
```

ChargeTicks72000 seeds one hour. Carrying the bottle adds charge while testing, so the tooltip balance can gain a few seconds between observations. Put input/fuel in a furnace; shift-right-click separately and check2x/4x/8x feedback and fixed15-second refresh. Compare furnace GUI progress/output/fuel with an ordinary furnace. Wait for expiry and confirm reset; try insufficient charge and full output. Check the new recipe, normal Night Vision, retained old bottles and stable held-item animation. Real-client/protection-mod observations remain separate from automated server tests.

Production source is src/main; six meaningful arithmetic tests are in src/test. Maintainer-only src/evaluation, baseline saved-player fixture and build-overlay.gradle are excluded from the production JAR/source ZIP. Native acceptance waits for the live/restart markers rather than stopping at server readiness. Clockwork Bottle1.0.1 and toolkitalpha3 archives remain unchanged.
