# Minecraft 1.12.2 mod kit

You build Forge mods for Minecraft 1.12.2 (Forge 14.23.5.2859, MCP stable_39, Java 8) from the user's description. Everything goes through `agent.ps1` in this folder:

| Command | What it does |
|---|---|
| `./agent.ps1 status` | project, mod, Java, last check, next step |
| `./agent.ps1 init <modid> "<Name>"` | working mod skeleton (empty project only) |
| `./agent.ps1 new <kind> <name> [options]` | item, block, ore, food, tools, armor, recipe, smelting, tile, test: docs/GENERATORS.md |
| `./agent.ps1 check` | compile + jar checks (~10 s) |
| `./agent.ps1 check server` | + start a server, lint registered content against its assets (~25 s) |
| `./agent.ps1 check game [-Test id]` | + run game tests from src/gametest/java: docs/TESTING.md |
| `./agent.ps1 api <Class>[.member\|.new]` or `api -Find <text>` | real 1.12.2 signatures plus source (fields, constants, bodies) |
| `./agent.ps1 batch plan.txt` | run one command per line, stop at the first failure |
| `./agent.ps1 version <x.y.z>`, `./agent.ps1 export` | bump version; copy the checked jar to releases/ |

If scripts are blocked: `powershell -NoProfile -ExecutionPolicy Bypass -File ./agent.ps1 ...`

## Loop

1. `status`. Java missing: follow SETUP.md.
2. Put the request, your decisions and acceptance checks in TASK.md. Keep it short; replace, never append.
3. Use generators for standard content (several at once: `batch`). For anything else read the one matching guide from docs/INDEX.md.
4. `check` after each change and fix the first error; hints name the 1.12.2 replacement. Unsure of a name or signature: `api`, never guess.
5. Generators add their own checks; important custom behavior gets a game test (`new test <id>`, then `check game`).
6. Finish with `check server` (or `check game`) passing with no WARN lines, TASK.md updated, `export` if a jar is wanted. Tell the user what was tested automatically and what to look at in the game client (`gradlew.bat runClient`): textures, GUIs, rendering.

## Rules

- Java 8, Forge 1.12.2 only. No records/var/text blocks, DeferredRegister, mods.toml or data packs. Code from other versions has wrong names.
- Rendering, GUI screens, models and keybinds belong in client-only classes (`@Mod.EventBusSubscriber(value = Side.CLIENT)` or `@SideOnly(Side.CLIENT)`). `check server` catches mistakes.
- Change the world only on the server: `if (!world.isRemote)`.
- Write files with your edit/write tool or `[IO.File]::WriteAllText`, never `Set-Content`/`Out-File` (they add a byte-order mark).
- Test code goes in src/gametest, never src/main. Keep the pinned build files unless the user asks.
- Ask the user only when a choice changes gameplay. Otherwise decide and note it in TASK.md.
