# Minecraft 1.12.2 mod kit

You build Forge 1.12.2 mods (Forge 14.23.5.2859, MCP stable_39, Java 8) from the user's description with `./agent.ps1` (if blocked: `powershell -NoProfile -ExecutionPolicy Bypass -File ./agent.ps1 ...`):

| Command | Does |
|---|---|
| `status` | project, Java, last check, next step |
| `init <modid> "<Name>"` | mod skeleton |
| `new <kind> <name> [options]` | item block ore food tools armor recipe smelting tile mob test: docs/GENERATORS.md |
| `check [server\|game [-Test id]]` | compile ~10 s; + asset lint; + game tests (docs/TESTING.md) |
| `api <Class>[.member\|.new]`, `api -Find <text>` | signatures and source of any Minecraft/Forge class |
| `batch <file>`, `version <x.y.z>`, `export` | run lines in order; bump version; copy the checked jar to releases/ |

## Loop

1. `status`. Java missing: SETUP.md.
2. Request, decisions and acceptance checks go in TASK.md (short; replace, never append).
3. Start from the closest generator, custom content too: `new tile <name> [-Inventory n] [-Ticking]` for a block that stores or does something, `new mob <name> -Like <vanilla mob>`, else `new item`/`new block`. Write the behavior in the generated classes from the matching guide (docs/INDEX.md), which also covers what no generator makes (enchantments, effects, commands). Make only what was asked (`-Only`).
4. Write, then `check`, fix the first error: the compiler checks names cheapest; its hints name the 1.12.2 replacement or the closest members. Do not pre-read source for names. Do look up behavior before relying on it (where vanilla saves or copies data, NBT keys, event order): `api` beats memory.
5. Generated checks only repeat what the generator wrote: compare its `stats:` line with TASK.md. Custom behavior gets a game test as soon as it compiles (`new test <id>`, `check game`).
6. Done = `check game` PASS without WARN, TASK.md updated, `export` if wanted; tell the user what was tested and what to look at in `gradlew.bat runClient`.
7. Stuck (`STUCK`, or 3 tries on one problem): stop. A partial report is a valid finish: what works, the failing check, what you ruled out, what to try in the client.

## Rules

- Java 8 and 1.12.2 names only (no records/var, DeferredRegister, mods.toml, data packs). Vanilla constants: `net.minecraft.init` (Blocks, Items, Enchantments, MobEffects, SoundEvents, Biomes).
- Rendering, screens, models, keybinds: client-only classes (`@SideOnly(Side.CLIENT)`, client event subscribers). Change the world only when `!world.isRemote`.
- Write files with your edit tool or `[IO.File]::WriteAllText`, never `Set-Content`/`Out-File` (byte-order mark). PowerShell with `$` variables goes in a .ps1 file; inline `-Command` text gets mangled.
- Tests in src/gametest, never src/main. Keep the pinned build files.
- Ask the user only when a choice changes gameplay; otherwise decide and note it in TASK.md.
