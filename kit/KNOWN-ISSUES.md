# Known issues and limits (0.5.1)

- **Looks are untested.** Checks run on a dedicated server, so textures, models, GUIs, particles and HUDs are only seen in `gradlew.bat runClient`. Look before you share a mod.
- **No save/restart test.** Every check starts a new world. Data that must survive leaving and rejoining (tile contents, player data) is checked by an NBT round trip at most; confirm it in the client by saving, quitting and reloading.
- **No multiplayer sync test.** Nothing runs a real client against a server, so client/server desync (ghost items, GUIs out of step) can slip through. Test on a LAN world with two clients if your mod has GUIs or synced data.
- **Exported jars are not tested in a modpack.** The jar is built and reobfuscated, but not loaded next to other mods.
- **Windows 10/11 x64 only.**
- **Agent quality varies.** Claude Code passed its checks on all 8 requests of the last test series on its own; a small local model passed 10 of 16 attempts, and long sessions can lose track (see "Local models" below).
- **First build downloads about 1 GB** (Gradle, Minecraft, Forge) and takes 10-30 minutes once.

## Local models

The kit works with local agents, but give them room: at least 64k context (131k is better), an output limit around 32k, and in OpenCode a compaction `buffer` of 20000-32000 so long sessions can summarize themselves. If the model server has no vision support, declare the model text-only (`"modalities": { "input": ["text"], "output": ["text"] }`), or reading a texture image ends the session.

## Reporting a problem

Zip and send: `TASK.md`, the `reports/` folder (check results, `agent-log.jsonl` with every command the agent ran) and what you asked for. Leave out `build/`, `run/` and `.gradle/`.
