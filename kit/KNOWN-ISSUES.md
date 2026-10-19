# Known issues and limits (0.5.4)

- **Looks are checked, not judged.** `check client` proves every block and item has a model and its textures, and saves screenshots; whether they look right, GUIs, particles and HUDs need your eyes (the screenshots or `gradlew.bat runClient`). It needs a desktop session: it opens a game window for about 40 seconds.
- **Player data across a restart is untested.** Test players do not survive a restart, so player-held data is not covered by `afterRestart` tests; world data (tile contents, block states, saved data) is.
- **No multiplayer sync test.** Nothing runs a real client against a server, so client/server desync (ghost items, GUIs out of step) can slip through. Test on a LAN world with two clients if your mod has GUIs or synced data.
- **Exported jars are not tested in a modpack.** The jar is built and reobfuscated, but not loaded next to other mods.
- **Windows 10/11 x64 only.**
- **Agent quality varies.** Claude Code passed its checks on all 8 requests of the last test series on its own; a small local model passed 10 of 16 attempts, and long sessions can lose track (see "Local models" below).
- **First build downloads about 1 GB** (Gradle, Minecraft, Forge) and takes 10-30 minutes once.

## Local models

The kit works with local agents, but give them room: at least 64k context (131k is better), an output limit around 32k, and in OpenCode a compaction `buffer` of 20000-32000 so long sessions can summarize themselves. If the model server has no vision support, declare the model text-only (`"modalities": { "input": ["text"], "output": ["text"] }`), or reading a texture image ends the session.

## Reporting a problem

Zip and send: `TASK.md`, the `reports/` folder (check results, `agent-log.jsonl` with every command the agent ran) and what you asked for. Leave out `build/`, `run/` and `.gradle/`.
