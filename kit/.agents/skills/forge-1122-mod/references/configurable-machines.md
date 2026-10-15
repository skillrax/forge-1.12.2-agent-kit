# Configurable machines and upgrades

For a machine with configurable item faces, active pull/push, GUI buttons, redstone and upgrade-gated processing, adapt the optional [configurable starter](../assets/configurable-machine-starter/src/main/java/com/example/configmachine/MachineMod.java). It contains four processing slots plus one GUI-only upgrade slot, FE storage, saved controls and a client-only menu. The accepted smelter supplied this pattern. The simpler inventory/menu starter remains useful when these features are unnecessary.

Copy the configurable starter's `src/main` contents into an empty checkout's `src/main/java` and `src/main/resources`, creating those directories explicitly. Merge relevant components into an existing mod instead of adding another entry point. Rename package, mod ID, proxy strings, registry IDs, resource paths and metadata consistently. Build with the existing pinned configuration and synchronized mod version. Optional assets do not install themselves. The recipient starter omits the proof's demo generator/pipe and all QA; use an appropriate Forge Energy source.

## Adaptation points

| Need | File |
|---|---|
| Capacity, receive rate, duration, RF/t, transfer amount/cadence, default faces | [MachineSettings.java](../assets/configurable-machine-starter/src/main/java/com/example/configmachine/MachineSettings.java) |
| Example recipe entries, upgrade requirement, furnace fallback and input acceptance | [MachineRecipes.java](../assets/configurable-machine-starter/src/main/java/com/example/configmachine/MachineRecipes.java) |
| Inventory, operation identity, accounting, face views, transfer and NBT | [MachineTile.java](../assets/configurable-machine-starter/src/main/java/com/example/configmachine/MachineTile.java) |
| Slots, shift-click, server actions and field synchronization | [MachineContainer.java](../assets/configurable-machine-starter/src/main/java/com/example/configmachine/MachineContainer.java) |
| Colored face selector, controls, status/hover text | [MachineScreen.java](../assets/configurable-machine-starter/src/main/java/com/example/configmachine/MachineScreen.java) |

Keep gameplay-specific IDs, materials, counts and tuning in these adaptation points. Two example Java entries use iron/gold ore plus sand to produce two ingots and a brick with the recipe upgrade. Entries hold both input counts, both outputs and an upgrade requirement. Custom entries are evaluated in order, then the current `FurnaceRecipes` registry supplies ordinary smelting. Missing upgrade/secondary ingredients permit ordinary fallback; do not accidentally change this policy while adding a recipe. Configure entries before ticking; this is not a JSON loader or live recipe-reload framework. Custom matching uses exact item/metadata, ignores ingredient NBT and requires specified counts; ordinary furnace matching remains the registry's behavior. Outputs are copied. Add different matching only for a requested use case.

The generic tick loop consumes configured counts only at completion; it contains no iron/gold/sand branch. The operation key includes selected recipe/output/counts and main-input identity/NBT so incompatible changes reset partial progress. Output blockages and redstone pauses retain progress without spending RF. Partial RF is not refunded after a recipe change. If changing operation identity or persistence, verify that specific interruption once. For a fuel machine, catalyst, fluid ingredient or different upgrade semantics, define those behaviors explicitly rather than treating this scaffold as their proof.

## Control, synchronization and save rules

Face roles are `0=disabled`, `1=main input`, `2=secondary input`, `3=main output`, `4=secondary output`; role minus one is the physical inventory slot. The upgrade is slot 4 and excluded from automation. Face indices follow `EnumFacing`: DOWN, UP, NORTH, SOUTH, WEST, EAST. They are world directions; rotation-relative faces would require an explicit coordinate mapping. Multiple faces may address one slot. Held capability views consult the current role on each call, avoiding stale permissions after a GUI change.

The menu uses vanilla window actions (`sendEnchantPacket` / `Container.enchantItem`) for the small integer control set. Only the server mutates controls; check the current container, tile/distance access and action range. Use this path for these existing buttons; richer payloads need a suitable validated packet. New controls must update their action IDs, field count, listener array, client field application and NBT together. For unsaved display data such as last transfer outcome, NBT is unnecessary.

Window properties use signed shorts. This example's 16000 RF capacity, duration, role/status values fit; larger values need split fields or another synchronization method. Keep inventory stack limits, recipe counts, positive duration/transfer cadence and nonnegative RF consistent when tuning. The screen's dimensions/slot coordinates are shared with the container and need a client observation after layout changes. Keep menu/model imports behind the client proxy/subscriber; the common/server tile has no renderer imports.

## Transfers and diagnostic outcomes

Item face permissions, auto-transfer switches and RF ports are separate. An input role permits insertion only, an output role extraction only. The automatic switches control active neighbor pull/push; external hoppers still follow face permissions. All faces accept FE independently of their item role. Default redstone requires no signal; flipping polarity requires a signal. It pauses processing and active transfers, while RF reception remains available.

Reuse [item transfer accounting](item-transfer-and-routing.md#transfer-accounting) and the [chunk-safety rules](machines-and-energy.md#chunk-safety-and-dirty-marking). Reacquire neighbor capabilities on the opposite face for each attempt; never cache a neighbor across replacement/unload. Retain output until the receiver actually accepts it. Pull only a simulated accepted count; unexpected committed remainder is preserved as an item entity rather than silently deleted. This assumes handlers honor their contract and does not establish arbitrary modpack compatibility. The plain chest guard is intentionally conservative and saves without comparator probes because this machine has no comparator output.

Hover diagnostics distinguish disabled/auto-off/redstone, no items, unloaded positions/chest-area guard, absent inventory/capability, rejected/full destination and movement. Capture the current role plus reason before cycling a stalled face. A user-reported transient stall in the proof could not be reproduced and was closed by the user; the diagnostics do not constitute a proven fix. Preserve that distinction when reporting reliability.

## Efficient checks

Use the accepted patterns to avoid another campaign for recipe/rate/cosmetic variations. Match checks to the change:

- Recipe selection/counts: one operation with input/output/RF accounting, plus the relevant upgrade/fallback condition.
- Transfer changes: one adjacent-inventory scenario and a blocked-destination resume; include simulation permissions if changed.
- GUI/control changes: actual client control/field observation; persistence changes additionally need one save/reopen check.
- Side-sensitive imports/registration: focused build and server loading.
- A changed recipient candidate: inspect actual ZIP contents/hashes/links and build the relevant starter from its extraction.

Keep assertion mods and evidence outside production resources/JARs. The original proof has server accounting and user gameplay/save-reopen acceptance, including hoppers/double-chest changes; that supports representative reuse, not universal handlers, stress performance, experience payout, furnace-specific hooks or legacy CoFH-only RF adapters. This kit does not claim measured token savings.
