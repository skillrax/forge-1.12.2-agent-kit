# Minecraft 1.12.2 mod kit

You build Forge mods for Minecraft 1.12.2 (Forge 14.23.5.2859, MCP stable_39, Java 8) from the user's description, through `agent.ps1` in this folder:

| Command | What it does |
|---|---|
| `./agent.ps1 status` | project, mod, Java, last check, next step |
| `./agent.ps1 init <modid> "<Name>"` | mod skeleton (empty project only) |
| `./agent.ps1 new <kind> <name> [options]` | item, block, ore, food, tools, armor, recipe, smelting, tile, mob, test: docs/GENERATORS.md |
| `./agent.ps1 check [server\|game [-Test id]]` | compile (~10 s); + server lint of assets (~25 s); + game tests (docs/TESTING.md) |
| `./agent.ps1 api <Class>[.member\|.new]`, `api -Find <text>` | signatures and source of any Minecraft/Forge 1.12.2 class |
| `./agent.ps1 batch plan.txt` | one command per line, stop at the first failure |
| `./agent.ps1 version <x.y.z>`, `export` | bump version; copy the checked jar to releases/ |

Scripts blocked: `powershell -NoProfile -ExecutionPolicy Bypass -File ./agent.ps1 ...`

## Loop

1. `status`. Java missing: SETUP.md.
2. Request, decisions and acceptance checks go in TASK.md: short, replaced, never appended.
3. Start from the closest generator, custom content too: `new tile <name> [-Inventory n] [-Ticking]` for a block that stores or does something, `new mob <name> -Like <vanilla mob>` for a mob, else `new item`/`new block`. It writes classes, registration, models, lang, texture; hand-write the behavior in those classes from the one matching guide in docs/INDEX.md. Make only what was asked (`-Only`).
4. Write the code, then `check` and fix the first error: the compiler is the cheapest signature check and its hints name the 1.12.2 replacement. Do not pre-read source to verify names. Do look up behavior before relying on it (where vanilla saves or copies data, NBT keys, event order): `api -Find <text>` or `api Class.member` beats memory.
5. Generated checks only repeat what the generator wrote: compare its `stats:` line with the numbers in TASK.md. Custom behavior gets a game test as soon as it compiles (`new test <id>`, `check game`).
6. Done = `check game` (or `check server`) PASS without WARN, TASK.md updated, `export` if a jar is wanted. Tell the user what was tested and what to look at in `gradlew.bat runClient` (textures, GUIs, rendering).
7. Stuck (a `STUCK` line, or 3 tries on one problem): stop and report instead. A partial report is a valid finish: what works and is tested, the exact failing check, what you ruled out, what the user should try in the client.

## Rules

- Java 8, Forge 1.12.2 only: no records/var, DeferredRegister, mods.toml or data packs; other versions' names are wrong.
- Rendering, screens, models, keybinds: client-only classes (`@Mod.EventBusSubscriber(value = Side.CLIENT)` or `@SideOnly(Side.CLIENT)`). Change the world only when `!world.isRemote`.
- Write files with your edit tool or `[IO.File]::WriteAllText`, never `Set-Content`/`Out-File` (byte-order mark). Other PowerShell with `$` variables: put it in a .ps1 file and run that; inline `-Command` text gets mangled by other shells.
- Tests in src/gametest, never src/main. Keep the pinned build files.
- Ask the user only when a choice changes gameplay; otherwise decide and note it in TASK.md.
