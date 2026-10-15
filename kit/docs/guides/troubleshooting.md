# Troubleshooting

Fix the first problem a check prints, rerun the same check, and stop after two identical failures to rethink. `./agent.ps1 errors -Full` shows the log tail.

| Symptom | Cause / fix |
|---|---|
| `cannot find symbol` with a hint | apply the hint; it comes from the real 1.12.2 API |
| `cannot find symbol`, no hint | `./agent.ps1 api <Class>` to list members, `api -Find <word>` to search everything |
| `method does not override` | signature differs in 1.12.2; the hint shows the real one |
| `No Forge @Mod entry point` | the class with `@Mod` must be under src/main/java |
| Version mismatch | `./agent.ps1 version x.y.z` syncs build.gradle, @Mod and mcmod.info |
| `invalid side SERVER` / `NoClassDefFoundError: net/minecraft/client/...` at server start | a common class uses a client class; move that code into a client-only class (docs/FORGE-1.12.md, Sides) |
| `WARN ... missing translation` | add the exact key printed to `lang/en_us.lang` |
| `WARN ... model ... does not exist` / `texture not found` | create the file, or fix the path; a path without `modid:` means `minecraft:` |
| `WARN ... lacks variant(s)` | add those keys to the blockstate (properties alphabetical, `"normal"` without properties) |
| `ERROR ... tile entity ... not registered` | `GameRegistry.registerTileEntity(...)` at `// kit:tiles` |
| `Parsing error loading recipe` | the following lines name the file; check item ids, `"type"`, pattern width |
| `Potentially Dangerous alternative prefix` | registry name uses another namespace; use `setRegistryName(MODID, name)` |
| `Can not register to a locked registry` | registering outside `RegistryEvent.Register` |
| `NullPointerException` during registry/init | a static field used before its class created it (circular static init between ModItems and ModBlocks), or `Item.getByNameOrId` for an id that doesn't exist |
| Game test `exceeded N ticks` | the waited condition never happened; print state in the detail supplier |
| Behaves in tests, not in game | the test called code directly; use `useItem`/`useOnBlock`/`breakBlock` so real code paths run |
| Item bobs in hand while ticking | `shouldCauseReequipAnimation` (guides/items.md) |
| Works in single player, not on a server | client-only call in common code, or logic run only on the client |
| Gradle/toolchain failure, no Java error | `./agent.ps1 check environment`; if Java is missing, SETUP.md |

Side traps: some methods are client-only although they look common (`Item.addInformation`, `Potion.hasStatusIcon`, `Block.getRenderLayer`, `SoundEvent.getSoundName`, `FoodStats.setFoodSaturationLevel`). `./agent.ps1 api Class.method` shows `[client-only]`. Override them with `@SideOnly(Side.CLIENT)`; never call them from server code.

Harmless log noise: `Unable to read a class file correctly ... module-info` / `META-INF/versions` (legacy Forge scanning Java 9+ jars) and `Cache miss! Stacktrace for Information Only`. The checks already hide these.
