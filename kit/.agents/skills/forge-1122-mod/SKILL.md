---
name: forge-1122-mod
description: Create or repair Minecraft 1.12.2 Forge mods using this kit's pinned Java 8 build and validation tools. Includes item/recipe patterns and guidance for persistent ticking items. Use for mod features, not MCP server maintenance or newer Minecraft versions.
---

# Forge 1.12.2 mod development

Use this checkout's existing build configuration. Minecraft 1.12.2, Forge 14.23.5.2859, MCP stable 39-1.12 and Java 8 are fixed. Java 8 excludes records, var, text blocks and newer library APIs.

Keep the user's behavioral prompt. Ask when a missing choice materially changes gameplay, such as adding versus replacing a buff duration. Infer ordinary implementation safeguards and the configured environment; users need not specify overflow, duplication or side handling. Record defaults briefly in TASK.md.

Choose the cheapest reliable source for the complete task: existing code, bounded mapped sources or focused version-matched official web documentation. Research and justified libraries are allowed; do not guess to reduce lookup count. Record any new dependency and its compatibility. Codex is the primary target; speculative offline/frontend work is outside a normal mod task.

## Development loop

1. Run `agent.ps1 -Action status` or call the connected `project_status` tool. Inspect existing mod code before creating another entry point.
2. Record requested behavior, identifiers, acceptance checks and the next step in TASK.md (under 2000 characters). Proceed within the user request without requiring a separate Plan/Act approval.
3. Read only the relevant reference below. Implement one small step and reuse existing project patterns.
4. Call `validate` with `profile: "mod"`. Failed checks include bounded diagnostics; use `diagnostics` with that run ID when more context is needed, fix the concrete error and rerun. Do not replace this outcome with an older PASS. Normal checks reuse unchanged Gradle work; use `clean: true` (shell `-Clean`) for an explicit recovery build.
5. Use `profile: "unit"` for real JUnit 4 logic tests and `profile: "server"` for dedicated-server startup. Server acceptance is automatic. Run checks sequentially.
6. Record the new results and remaining unchecked behavior in TASK.md. Distinguish compiled, packaged, server-loaded, behavior-asserted and manually rendered evidence.

When delivering changed mod bytes after an earlier export, bump the mod version by default (PATCH for a compatible fix, MINOR for a compatible feature), synchronize Gradle/@Mod/mcmod metadata and rebuild/export. Preserve prior artifacts; don't bump every intermediate edit or change the kit version. Export rejects changed bytes under an already exported filename/version. See docs/WORKFLOW.md for delivery scope.

If MCP is disconnected, use the equivalent agent.ps1 commands in AGENTS.md. Do not invent additional MCP tools.

## Read when needed

- Entry points, item registration, model/language resources or recipes: [references/items.md](references/items.md). The complete working example is in `assets/starter/src/main`; inspect only the files needed for the requested feature.
- Block inventories/menus, recipe machines or Forge Energy: [references/machines-and-energy.md](references/machines-and-energy.md). Optional inventory/menu scaffold: `assets/machine-starter/src/main`; copy/adapt only when relevant.
- Saved-pattern automated crafting, registered recipe lookup, returned containers or per-pattern NBT/metadata ignore: [references/automated-crafting.md](references/automated-crafting.md). Reuse existing inventory/menu/energy plumbing; plan all outputs and returns before spending RF or consuming inputs.
- Bufferless six-way RF networks, solid/lava generation, upgrade fuel economy or shared tick budgets: [references/rf-networks-and-fuel.md](references/rf-networks-and-fuel.md). Optional working `assets/rf-network-starter/src/main`; no third-party adapter or large-network claim.
- Configurable six-face item slots, automatic pull/push, redstone controls or upgrade-gated recipes: [references/configurable-machines.md](references/configurable-machines.md). Optional working scaffold: `assets/configurable-machine-starter/src/main`; recipes/tuning are dedicated example configuration.
- Automated item extraction, sided item handlers or filter splitting: [references/item-transfer-and-routing.md](references/item-transfer-and-routing.md). For cross-chunk transfers, also read the shared chunk-safety section in the machine reference.
- Multi-hop item ducts, branching routes, configured source/destination filters or shared item budgets: [references/item-networks.md](references/item-networks.md). Reuse existing inventory/menu and loaded-view plumbing; preserve owned remainders through route edits.
- Fluid tanks/transfer, custom fluid flow/contact behavior or wrench pickup retaining machine state: [references/fluids-and-pickup.md](references/fluids-and-pickup.md).
- World block breaking/placement, actual tool enchantments, player versus direct callbacks, source collection/pouring and Nether policy: [references/world-block-actions.md](references/world-block-actions.md). Accepted bounded proof; modded tools/hand-drop/protection compatibility requires the requested integration, not a blanket vanilla restriction.
- Complex textured static models, source-format intake and normalized rendering: [references/static-models.md](references/static-models.md). Optional original sample/runtime scaffold: `assets/static-model-starter/src/main`; source imports and model-specific assembly remain separate adapters/configuration.
- Moving model parts, hover/rotation, saved on/off clocks or bounded client particles: [references/animation-and-particles.md](references/animation-and-particles.md). Use procedural geometry or adapt existing parts; no animation framework required.
- Stored charge, inventory ticking, save/load or changing held-item NBT: [references/persistent-items.md](references/persistent-items.md).
- Configurable keybindings, standalone personal menus/buttons/tooltips or server-owned saved player settings: [references/personal-menus.md](references/personal-menus.md).
- Entity ticks, vanilla trades, player lifecycle/reset or command permissions: [references/entities-and-players.md](references/entities-and-players.md).
- Crops, complete structure placement or dimension/return/respawn policy: [references/world-content-and-travel.md](references/world-content-and-travel.md).
- Wrong API names, missing assets, client/server crashes or questionable test results: [references/repair.md](references/repair.md).
- First feature trial and expected evidence: [references/first-trial.md](references/first-trial.md).
- Exact production-JAR/world/third-party tests: [installed-test workflow](../../../docs/INSTALLED-TESTING.md). Put feature-specific scenarios in src/installedTest; shared harness edits are unnecessary.

The example uses modid `skillprobe`; adapt the package, IDs and display names consistently. It is not automatically installed in the main source set. Maintainer validation assets live outside the recipient ZIP; never package assertion mods.

## Version boundaries

This is the legacy Forge event/lifecycle API: `@Mod`, `@Mod.EventHandler`, `@SubscribeEvent`, `RegistryEvent.Register` and `@Mod.EventBusSubscriber`. Modern DeferredRegister, RegistryObject, mod event buses, mods.toml and data-pack paths do not apply.

Registry identity, translation keys and display text are different values. In these actual stable-39 mappings, Item uses `setTranslationKey`, even though many older 1.12 tutorials say `setUnlocalizedName`. Verify unfamiliar methods against the mapped source or compiler rather than guessing another version's API.

For item creative tabs, import `net.minecraft.creativetab.CreativeTabs` and use `CreativeTabs.MATERIALS`; there is no `Item.CreativeModTab` in this API. A generated item model uses parent `item/generated` and the texture under `textures.layer0`, such as `minecraft:items/emerald`; a texture location is not a model parent.

Common/server classes must not reference client rendering classes. Keep model registration in a client-only event subscriber. Run server validation after changing side-sensitive code. Rendering remains unverified until the graphical client is checked.

Use meaningful tests for behavior that can be isolated. Minecraft/Forge registry assertions need a bootstrapped game; ordinary JUnit tests do not supply that. Zero unit tests is an honest outcome for an item-only scaffold, not evidence of gameplay correctness.


For unfamiliar APIs, inspect a bounded member with tools/Read-MappedSource.ps1; see references/repair.md for lookup and side pitfalls. Verify server availability as well as compilation.
