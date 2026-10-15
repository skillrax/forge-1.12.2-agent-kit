# Clockwork Bottle1.2.0

Feature extension of accepted1.1.0, built with private toolkit0.3.0-alpha.3. Minecraft1.12.2, Forge14.23.5.2859 build baseline, Java8 bytecode. No added dependency. Replace your previous Clockwork Bottle JAR; do not install both. Existing bottle registry ID/charge, recipe, Night Vision and furnace behavior are retained.

Shift-right-click a vanilla furnace with the main-hand bottle:2x for15 normal game seconds costs30 seconds of charge. Repeat for4x/60,8x/120, etc.; each success refreshes15 seconds, and expiry resets the next use to2x. White centered text on every face shows the multiplier above remaining whole seconds, rounded up. Labels are visible within32 blocks, depth-tested and have no backing panel. They use the default font and require no texture or resource pack. Successful furnace HUD text is removed; failure messages remain.

The server sends a complete dimension snapshot every20 game ticks and immediately on activation/removal. Other players in that dimension receive the same state, including late observers within one game second. Countdown changes follow server game time; a displayed integer can be up to one game second behind the latest tick. Cache clears when the client world unloads; unloaded/non-furnace blocks are skipped. Boost state is not persisted. Furnace fuel and cooking accelerate together; duration is normal game time. Maximum individual rate8192x; global extra-update budget8192. Many-target/network performance is not stress-tested.

Recipe rows: diamond/clock/diamond, diamond/gold ingot/diamond, diamond/glass bottle/diamond. The temporary experience-bottle texture remains.

For testing with cheats:

```text
/give @p clockworkbottle:clockwork_bottle 1 0 {ClockworkBottle:{ChargeTicks:72000}}
```

Check2x/4x/8x labels and refresh, expiry/removal, failure feedback, and the top/bottom/front/back/left/right faces; solid walls should hide text. Check retained held-item animation and ordinary Night Vision. A second real client can check observer visibility and logout cleanup. Graphical and real-networked-client acceptance remain pending.

Production source is src/main; nine unit tests are in src/test. Dedicated-server acceptance additionally checks340 actual ticks, authoritative label snapshots, existing item behavior, saved charge and restart/old-format compatibility. Assertion mods, saved fixtures, QA logs and build overlay are excluded from the production JAR/source ZIP.
