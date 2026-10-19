# Agentic Minecraft 1.12.2 mod kit

A workspace where an AI coding agent (Claude Code, Codex, a local-model agent, ...) builds Forge 1.12.2 mods from your description.

1. Extract this folder and run setup once: [SETUP.md](SETUP.md).
2. Open the folder in your agent app and describe the mod you want. The agent reads [AGENTS.md](AGENTS.md) (Claude Code loads it through CLAUDE.md).
3. The agent creates the mod with generators, checks it on a real dedicated server (registrations, models, textures, translations, behavior tests) and exports `releases/<modid>-<version>.jar`.
4. Look at it in the game: `gradlew.bat runClient`, or install the jar with Forge 1.12.2-14.23.5.2859.

| Folder | Contents |
|---|---|
| `src/main` | your mod (starts empty) |
| `src/gametest` | behavior tests run inside a server, never shipped |
| `docs/` | generator reference, testing, Forge 1.12 guides ([index](docs/INDEX.md)) |
| `examples/` | larger working mods to copy from: machines, GUIs, energy networks, static models |
| `tools/`, `testing/` | scripts behind agent.ps1 |

Versions: [kit.json](kit.json). License: MIT ([LICENSE](LICENSE)); third-party notices: [THIRD-PARTY-NOTICES.md](THIRD-PARTY-NOTICES.md).

What changed: [CHANGELOG.md](CHANGELOG.md). Limits and how to report a problem: [KNOWN-ISSUES.md](KNOWN-ISSUES.md).

## Your first mod (about 15 minutes after setup)

1. Open this folder in your agent app and type something like: *"Make a mod called Ruby Tools: a ruby ore that spawns underground, rubies that smelt from it, and ruby tools a bit better than iron."*
2. The agent writes the request into TASK.md, runs `./agent.ps1 init` and the generators, then `check`, `check server` and `check game` until they pass. You will see short PASS/FAIL lines; a FAIL is normal mid-way, the agent fixes it. It may ask you a question when a choice changes gameplay.
3. When it says it is done, read its summary: what was tested, and what to look at in the game.
4. Look at it: the agent's last check (`check client`) opens a game window briefly and saves screenshots of every block and item; it prints their paths. To play with it, run `gradlew.bat runClient` in this folder (first start takes a minute), create a creative world and find the items in the creative menu. Anything that looks wrong: tell the agent what you saw.
5. The finished mod is `releases/<modid>-<version>.jar`. Put it in the `mods` folder of a Forge 1.12.2-14.23.5.2859 installation to play it normally.

Next requests go into the same folder ("add a ruby block", "make the pickaxe mine 3x3"). For a second, separate mod, extract a fresh copy of the kit.
