# Repair using actual evidence

Call diagnostics for the failed run. Read its message and bounded lines first. Open the saved fullLog only if the relevant error is missing. Fix one cause, then rerun the relevant profile. Keep build pins intact.

| Failure | First thing to inspect |
| --- | --- |
| Cannot find symbol | Import and exact method name in the stable-39 mapped source; Item.setTranslationKey is the naming API here |
| No @Mod entry point | Entry-point annotation/import and actual src/main/java placement |
| Duplicate registration | Same namespace:path registered twice, or an event handler manually called |
| Null item during model binding | Registration timing and whether ModItems stores the registered instance |
| Dedicated server cannot load net.minecraft.client.* | Client reference in common class; separate client-only subscriber |
| Missing texture/model | assets/<modid>/models/item/<path>.json, model location and texture reference |
| Recipe missing | assets/<modid>/recipes/<path>.json, recipe JSON/output ID and full game log |
| unit profile says no tests | Add tests for actual isolated logic when it exists; do not add a dummy passing test |
| Runner failure or no JSON | Saved runner log; this is a tooling failure, not proof the mod failed compilation |
| Held item repeatedly lowers/raises as NBT changes | Item.shouldCauseReequipAnimation; see persistent-items.md for selective bookkeeping suppression |
| Restart assertion cannot find prior player data | Ordinary server smoke runs use fresh worlds; acceptance needs an isolated persistent world across processes |

Use tools/Read-MappedSource.ps1 with an exact -Class/-Member and bounded -MaxLines (default30, maximum80). It discovers the pinned stable39 mapped source in the Gradle cache; explicit -Archive selects a caller-supplied archive. Overloads use -MatchIndex. Follow up with a small -Line range only when needed; avoid whole-class output.

Legacy side annotations matter even in test code: a CLIENT-only method may compile but disappear on a dedicated server. Known examples include FoodStats.setFoodSaturationLevel and SPacketSetSlot.getStack. Common food code can use FoodStats.addStats; potion amplifiers are zero-based. Inspect the actual member's annotations instead of inferring side availability from its name.

For PowerShell edits/copies followed by validation, use $ErrorActionPreference='Stop', root-anchored paths and explicit native exit-code checks. A failed copy must stop dependent checks; otherwise they rerun unchanged code and consume effort without testing the repair.

Evidence meanings:

- mod PASS: compilation, existing unit tests, reobfuscation and basic static JAR checks passed; at least one @Mod exists.
- unit PASS: at least one unskipped JUnit test executed; inspect what it asserted.
- server PASS: development-server readiness and shutdown passed.
- Rendering, crafting behavior and production-JAR loading need additional checks. A startup PASS alone does not demonstrate them.

The bundled example validator runs a separate assertion mod inside a real server. It checks item registration, stack limit, recipe output, valid inputs in either order, and rejection of wrong inputs. It tests the bundled example, not arbitrary code in the main project. The validation-only mod is excluded from production starter assets.
