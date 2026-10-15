# Item and recipe pattern for this environment

From the project root, the working files are in `.agents/skills/forge-1122-mod/assets/starter/src/main`.

For an empty foundation, `tools/Initialize-Mod.ps1 -ModId mymod -Name "My Mod" -Package com.example.mymod -Version 1.0.0` creates one entry point with matching build/resource metadata. Then adapt the registration/item/resource patterns below into that mod; do not copy a second entry point or replace its chosen metadata with skillprobe values. The command adds no items or recipes and refuses existing src/main content.

| File | Purpose |
| --- | --- |
| java/com/example/skillprobe/SkillProbeMod.java | @Mod entry point and identity constants |
| java/com/example/skillprobe/ModItems.java | Static RegistryEvent.Register<Item> handler; constructs and registers the item |
| java/com/example/skillprobe/ClientModels.java | Client-only ModelRegistryEvent subscriber |
| resources/mcmod.info | Legacy Forge mod metadata |
| resources/pack.mcmeta | pack_format 3 for Minecraft 1.12.2 |
| resources/assets/skillprobe/lang/en_us.lang | Legacy .lang translation entries |
| resources/assets/skillprobe/models/item/probe_token.json | Generated item model using a vanilla diamond texture |
| resources/assets/skillprobe/recipes/probe_token.json | Shapeless iron-ingot + diamond recipe |

Copy/adapt only these production files when creating a new mod. For an existing mod, merge registration and resources rather than overwriting its entry point. Choose lowercase registry paths and keep the chosen modid identical across annotations, registry names, resource directories and recipe output IDs.

The example registers `skillprobe:probe_token`, uses translation key `skillprobe.probe_token`, and resolves display text through `item.skillprobe.probe_token.name`. The Item instance is created during its registry event, then stored in ModItems.PROBE_TOKEN for later model registration. Do not register a different instance with the same ID or manually invoke event handlers.

ClientModels uses `@Mod.EventBusSubscriber(value = Side.CLIENT, modid = ...)` and static `@SubscribeEvent`. No common class imports it. The model binds metadata 0 and the inventory variant. A successful dedicated server is useful evidence of side separation; it cannot show whether the texture actually rendered.

The recipe goes under `assets/<modid>/recipes/`, not `data/`. Its file name becomes the recipe registry path. This example uses `minecraft:crafting_shapeless` and output `skillprobe:probe_token` with count 1. It has no custom Java recipe class and uses no external texture files.

This environment deliberately puts processed resources beside compiled classes because legacy Forge looks for assets relative to its discovered mod class directory. Preserve that build setting; separate outputs can compile/package successfully while leaving runtime recipes and translations missing.

For blocks, entities, tile entities, networking or GUIs, this item example is only a starting point. Consult the actual 1.12 API and build a feature-specific plan and assertions; do not treat the skill as comprehensive coverage of those systems.

Official version-specific references:

- https://docs.minecraftforge.net/en/1.12.x/concepts/registries/
- https://docs.minecraftforge.net/en/1.12.x/concepts/sides/
- https://docs.minecraftforge.net/en/1.12.x/utilities/recipes/
- https://docs.minecraftforge.net/en/1.12.x/models/using/

Their old Gradle setup instructions do not replace this project's pinned ForgeGradle 7 build.
