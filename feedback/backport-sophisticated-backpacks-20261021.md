# Kit feedback: Sophisticated Backpacks 1.12.2 backport

This is feedback from the agent side on using the Minecraft 1.12.2 mod kit for a much larger job than its generators were built for. The job was to port Sophisticated Backpacks 1.20.1 and Sophisticated Core 1.20.1 into one Forge 1.12.2 mod. The sources were about 74k lines of Java, and the result is about 760 tracked source files.

It ran over two days (2026-10-20/21) as 13 milestones (M0–M12), plus fixes, a performance pass and a release. The models were Claude Sonnet 5.5 and Opus 5.5. From M7 on, Opus planned and fixed the hard bugs, and Sonnet subagents did the bulk porting.

**Result:** 0.1.0 has 61 items, 4 blocks and 63 recipes, with 57 game tests passing. Baubles, JEI and Inventory Tweaks compat is confirmed by the user in game.

## Summary

The kit's server-side loop is excellent and carried the project. In order of value:
- `check` (about 7 s);
- `api -Full` for vanilla bodies;
- game tests;
- `-Restart` persistence tests.

**What the kit doesn't cover is the client.** Almost every bug that reached the user was in client-side GUI, rendering or networking. No kit check could see them, and each one cost a user round trip. The most valuable additions would be a client-side container/GUI test path and a few targeted lints. Both are described below.

## What worked well

- **`./agent check` speed and output:**
  - The compile check takes about 7–10 s.
  - The one-line `PASS build`/`FAIL ...` results are easy to grep.
  - Compiler errors pointing at 1.12 names made the "write, then fix the first error" loop cheap.
  - Even with dozens of files changed per milestone, compile errors were never the bottleneck.
- **`./agent api <Class>.<member> -Full`:** this was the single most useful tool, and it beat model memory every time it was used. Examples:
  - `Container.slotClick`;
  - `ContainerRepair.updateRepairOutput`;
  - `EntityPlayer.setItemStackToSlot`, which revealed the equip-sound bug;
  - `RenderItem.renderItemModel`, which revealed the blend-state bug;
  - `GuiContainer.drawSlot`;
  - `WorldEntitySpawner` (whether `doSpecialSpawn` fires);
  - `Bootstrap.BehaviorDispenseOptional`.

  `api -Find` was good for locating classes.
- **Game tests:**
  - The `then` / `check` / `waitUntil` / `require` API is small and fits how agents think.
  - `t.player()` FakePlayers, `t.server().getCommandManager()` and `t.world()` were enough for almost all server logic: containers, upgrades, loot rolls, commands, dispensers, hoppers and recipes.
  - `-Test <id>` gave fast iteration.
  - The `new test` scaffold that fails on purpose (`KIT-SCAFFOLD`) is a good guard.
  - The `-Restart` tests caught real persistence issues.
- **`check client` screenshots:**
  - They caught missing textures and a broken model early.
  - They confirmed the M8 dynamic model across tiers and dyes.
  - They confirmed the perf-pass model change hadn't broken item rendering.
- **`export` guard:** it refuses an unchecked or changed jar at the same version, which is good release hygiene.
- **AGENTS.md rules:** "client-only code in @SideOnly classes, change the world only server side" plus `check server` prevented every dedicated-server crash after one early lesson. A Message class had referenced `EntityPlayerSP` in a signature; after that it never happened again.

## Bugs and rough edges in the kit

1. **Game-test areas are silently reused.** Test *n* (alphabetical) gets the 16×16 area of *n* mod 36, and that area is cleared when the test starts. Once the project passed 36 tests, a `-Restart` test lost its chest because a later test cleared the shared area.
   - It failed as a puzzling "contents gone after restart", not as a harness error.
   - Workaround: build restart tests at `origin + (17,0,17)`, in the gaps between areas.
   - Fix: give each test a unique area (grow the grid). At minimum, print a WARN when the test count is over 36.
2. **`export` can't replace an unpublished jar at the same version.** During polish I had to `rm releases/...-0.1.0.jar` by hand three times to re-export 0.1.0 after fixes. A `-Replace` flag would help, or treating a jar with no git tag or release as replaceable. Same for `version`: going from 1.0.0 down to 0.1.0 needed `-Force`. That's fine, but `init` could ask for the starting version.
3. **WARN noise from the kit itself.** The game log carries a missing translation for the kit's own harness item/block and FML zip warnings. "Done = PASS without WARN" is a good rule. Kit-originated WARNs make agents either chase them or learn to ignore WARNs, and the second is dangerous.
4. **CRLF warnings on every commit.** Every commit printed `LF will be replaced by CRLF` for each touched file. A `.gitattributes` in the kit template would remove the noise and keep diffs clean.
5. **Step label limit (1–96 characters)** is only enforced at plan time, as an exception that fails the test. It's cheap to hit with descriptive labels. Truncate with a WARN, or document the limit in the scaffold comment.
6. **The `check game` summary has no pass count.** I ran `check game` twice in many turns: once to grep FAIL/WARN, once to count `PASS test` lines. A final line like `PASS game 57/57 tests, 0 WARN (68 s)` would halve those runs. At about 65–80 s per full run this was a real time cost, and the waste was partly mine.
7. **Contradictory instructions about editing files.**
   - AGENTS.md says to use the edit/write tool and never shell heredocs.
   - The harness's auto mode says to prefer Bash, including heredocs.
   - Large heredocs then failed (`ENAMETOOLONG`, "unexpected EOF").

   The practical rule that emerged was: Write for big files, short perl or heredocs for small edits. Stating that rule in AGENTS.md would save rediscovery each session.
8. **Python is assumed in places, but it isn't required or installed.** The docs mention a Python sample generator, and Python isn't installed on this machine. Perl was the fallback for scripted edits. Saying "use perl or PowerShell scripts for bulk edits" would help.

## Gaps: what the kit could not catch

These are the bugs that reached the user. Each was confirmed in game and needed a user test cycle. None could be seen by `check`, `check game` or `check client`.

| Bug | Why the kit missed it | Kit addition that would catch it |
|---|---|---|
| Placing a tank or battery desynced the client slot layout (items shown in the wrong slots, illegal moves). It took 2 fix attempts and came back once. | Game tests have no client container. FakePlayers have no connection, so client-bound packets are never processed. | A **client container test**: open a container on the test client, click slots by id, then assert that client and server slot lists and stacks match. Even a "compare `openContainer` slot counts and stacks client vs server after N ticks" check would have caught it three times. |
| LAN join failed: "string length 29 > 20". | `SimpleNetworkWrapper` channel = mod id (29 characters). Singleplayer never sends the name. | A **lint**: channel names ≤ 20 characters. Ideally also a "dedicated server + client connects" smoke check. |
| Black slot icons after drawing a display item. | GL blend state leaked by `RenderItem.renderItem(stack, TransformType)` inside a TEISR. | Hard to lint in general. Document it in FORGE-1.12.md; a GUI screenshot of an inventory with the custom item would show it. |
| JEI covering the mod's tabs, and the missing dimmed background. | The screen never called `drawDefaultBackground()`, which JEI 4.x needs to update exclusion areas. | **Lint**: `GuiContainer` subclasses whose `drawScreen` doesn't call `drawDefaultBackground()`. That is cheap and high value for any GUI mod. |
| Empty contents tooltip, then black holes in it. | Tooltip lines after the first get a `§7` prefix, and depth from items under the tooltip. | Screenshot of a hovered tooltip. |
| XP fluid untextured (missing-texture pattern). | Forge 1.12 only stitches fluid sprites for fluids with a block. | `check client` could render a tank/fluid sprite, or lint that registered fluid sprites exist in the atlas. |
| Tool swapper playing the equip sound with no swap. | `EntityPlayer.setHeldItem` plays the equip sound (found via `api`). | No check can hear. A doc note is enough. |
| Inventory Tweaks sorting upgrade slots and erroring. | Soft-dependency mods can't run in dev (below). | Optional-mods runtime. |

**Soft dependencies can't run in the dev environment.** The JEI, Baubles and Inventory Tweaks jars are SRG-obfuscated, and FG7 offers no runtime deobfuscation here. So all compat was compile-only, and the user had to test with the exported jar in a real instance. A `runtimeMods` folder that `check client` and `runClient` deobfuscate (or a documented recipe for it) would have saved two user cycles in M11.

**`check client` screenshots are fixed scenes.** They show items and blocks only. Configurable scenes would have caught several bugs above without the user, such as:
- open screen X for container Y;
- an entity wearing item Z in its chest slot;
- hover item Z with Shift held.

## Costs and efficiency

- **Subagent token use (M7 onward, Sonnet):**

  | Run | Tokens |
  |---|---|
  | M7 | 539k |
  | M8 | 379k |
  | M9 | 243k |
  | M10 | 304k |
  | M11 | 236k |
  | M12 | 296k |
  | perf audit | 263k |
  | **Total** | **about 2.26M** |

  These are reported by the harness. Wall time per milestone was 17–33 minutes. The Opus supervising session stayed small: it reviewed reports, re-ran tests and fixed the hard bugs. M0–M6 ran in a single session without delegation and aren't broken out here.
- **Splitting the work paid off.** Sonnet with a self-contained brief (rules, files to copy patterns from, test list, stop rule, commit rule) finished each milestone with green tests. Opus then fixed:
  - the slot desync;
  - the LAN channel name;
  - the JEI background;
  - the Inventory Tweaks sections;
  - the tooltip;
  - the GL blend leak.

  Every one of these was a "needs reasoning about 1.12 internals" problem. A kit doc on writing milestone briefs for subagents would be a natural addition.
- **Wasted work:**
  - **Re-running full `check game` to count passes** (see item 6).
  - **The performance pass.** It looked safe by analysis and passed all 57 tests, but it re-broke the client slot layout and had to be reverted whole. That's the strongest argument for a client container test: it turned a "free" optimisation into a loss.
  - **One usage-limit interruption** mid-M7 (HTTP 429). Resuming the same subagent worked cleanly, and git commits after each cluster meant no work was lost. Worth recommending in the docs.
  - **Missing summary from `check`:** agents grep logs; a JSON summary file per check (`reports/last-check.json` with counts, failures and warns) would remove the double runs.
- **Speed:**
  - A full game run (about 57 tests) took 65–80 s, and `check client` about 30–40 s.
  - Most time went into Gradle and game startup, not the tests.
  - A "keep the test server warm between runs" mode, or running only tests whose classes changed, would cut iteration time a lot on a project this size.

## Docs worth adding (docs/FORGE-1.12.md)

These 1.12 gotchas each cost a bug or a debugging session. All were confirmed in this project.

**Networking and data**
- `NetworkRegistry.newSimpleChannel(name)`: the name must be **≤ 20 characters**. The mod id is often longer.
- `PacketBuffer.readCompoundTag` caps one NBT at **2 MB**. FML already splits server→client payloads, so the NBT cap is the real limit for big inventories.
- Client→server custom payloads are limited to 32767 bytes.
- `ItemStackHandler.insertItem` does **not** call `isItemValid` (1.20 does). Check it yourself, or automation bypasses slot rules.
- `EntityPlayer.addExperience` can't take XP away. Negative values corrupt level progress, so set level, progress and total directly.

**Rendering and GUI**
- Call `drawDefaultBackground()` at the start of `drawScreen`. `GuiContainer` doesn't, and JEI relies on the event it fires.
- `RenderItem.renderItem(stack, TransformType)` calls `renderItemModel`, which ends with `disableBlend()`. Inside a GUI or TEISR this turns later slot background icons black. Use `ForgeHooksClient.handleCameraTransforms` + `renderItem(stack, model)` instead.
- Tooltip lines after the first get a `§7` prefix from `GuiScreen.getItemToolTip`, so marker-based tooltip drawing must use `contains`, not `startsWith`.
- Draw tooltip extras with a +300 z translate, or the items under the tooltip punch holes in it.
- Forge stitches fluid sprites only for fluids with a block. A blockless fluid needs `TextureStitchEvent.Pre` registration.
- Add worn layers to every `RenderLivingBase`, not only `RenderBiped`. Endermen, witches and illagers aren't `RenderBiped`.

**Behaviour and events**
- `EntityPlayer.setHeldItem` / `setItemStackToSlot` play the equip sound. Set `inventory.mainInventory` directly for simulated holds on fake players.
- `LivingSpawnEvent.SpecialSpawn` fires for natural spawns, spawners **and spawn eggs**, before `onInitialSpawn`.
- Inventory Tweaks: `@ChestContainer` alone treats every non-player slot as chest. Provide a `@ContainerSectionCallback`. Return a raw `Map` and keep the `ContainerSection` references in a separate class, so the container loads without Inventory Tweaks.

## Smaller suggestions

- **Upstream the port tools:** `PORT/tools` (Java 17→8 mechanical passes, `c.sh` compact errors, tracker generator) were written ad hoc. A generic "port from a newer MC version" helper would be a big multiplier: records, pattern `instanceof`, switch arrows, `List.of`/`.toList()`, a starter name map such as `Component`, `CompoundTag`, `Level`.
- **Test helper for client-bound packets:** a way to capture packets sent to a FakePlayer. That would let tests assert "the server told the client X", for example `SyncSlotChangeErrorMessage` with the right columns.
- **Kit-provided timing harness:** the `perf-backpack-ticks` pattern (ms per simulated tick, p50/p99) worked well. A tiny kit API like `t.time(label, runs, runnable)` with a summary line would make performance checks standard.
- **Deletion and revert hygiene:** `git revert -q` isn't a valid flag. My follow-up `--amend` then mislabelled the commit instead of reverting it, which took two more commands to fix. That was agent error, not the kit's. But a kit helper like `./agent revert <commit>` that checks out, rebuilds and re-runs `check game` would make "this change broke the client, roll it back" a one-liner. That was the most stressful moment of the project.
