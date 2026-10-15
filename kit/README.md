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

Versions: [kit.json](kit.json). Third-party notices: [THIRD-PARTY-NOTICES.md](THIRD-PARTY-NOTICES.md).
