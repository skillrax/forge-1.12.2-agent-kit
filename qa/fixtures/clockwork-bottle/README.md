# Clockwork Bottle trial

Current mod version: 1.0.1. Internal charge/sequence updates suppress Forge's re-equip animation, preserving normal slot/item/visual-data changes. Version 1.0.0 remains archived with the user's observed hand bobbing. Confirm the visual correction in a graphical client.

Generated from the human prompt in PROMPT.md and one clarification. Minecraft 1.12.2 / Forge 14.23.5.2859 build baseline; Java 8 mod bytecode. No external mod dependencies. Mod and item are both named Clockwork Bottle; registry ID is clockworkbottle:clockwork_bottle.

Shapeless recipe: one glass bottle + one clock + one gold ingot. Placeholder texture is vanilla's experience bottle. A bottle does not stack. Main inventory, hotbar and offhand charge; armor, chests and dropped items do not. Each 20 carried server ticks earns one second; cap is 60 hours. Server lag slows game-time charging. Offline time adds no charge.

Main-hand right-click sets Night Vision I to 15 seconds, then 30, 60, 120, etc., spending the full selected amount. Failure leaves the existing effect and bottle state untouched and displays a message. Successful use replaces existing Night Vision. The sequence belongs to this bottle and its last user while their Night Vision is active; removal/expiry resets it. Another user starts at 15 seconds. Vanilla remaining potion duration pauses offline. Costs above the storage cap fail safely rather than wrapping.

Production: src/main. Arithmetic tests: src/test. Acceptance-only source: src/evaluation plus build-overlay.gradle; these stay out of the JAR and production source ZIP. Reproduction runner seeds the preserved solution into a fresh alpha2 extraction; it is deterministic QA, not independent model generation. Source ZIP contains the kit plus production source and unit tests, without this acceptance overlay.

## In-game acceptance still required

Install the exported SRG JAR in an isolated Minecraft 1.12.2 Forge instance. Record its SHA256 and Forge version. Open the mod list, craft the recipe, inspect the bottle texture/name and hover it while it charges. Check main inventory/hotbar/offhand and chest/drop pause. Tooltip format is HH:MM:SS / 60:00:00.

For quicker click checks, cheats can seed an hour of charge:

```text
/give @p clockworkbottle:clockwork_bottle 1 0 {ClockworkBottle:{ChargeTicks:72000}}
```

Click separately and check approximately 15/30/60/120-second SET durations and equivalent charge deductions. Clear Night Vision or let it expire, then check the next use costs 15 seconds. Try an empty/insufficient bottle: it must show a message, preserve any existing effect, and spend nothing. Carry two bottles and check independence. Move/drop/pick up and trade a bottle; verify retained charge and another holder's first use. Close/reopen the instance and check charge and continued unexpired escalation. Test a dedicated-server login/logout as a separate networked observation.

Automated assertions cover real inventory hooks and vanilla save/load across processes with test-controlled players and queued packets. They do not render a tooltip or establish live multiplayer behavior. All observations should retain this distinction.
