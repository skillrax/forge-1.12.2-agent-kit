# Maintaining the kit

The kit's job: let an AI agent, including small local models, turn a user's description into a correct 1.12.2 mod with few tokens and few mistakes. Prefer, in order: a generator (deterministic, tested) > a check that catches the mistake > a hint in tool output > a sentence in a guide.

## Loop

1. One defect or missing mechanism, with a stopping condition, in TASK.md.
2. Change the tool/guide/generator. Keep production code, client-only code and test code separate.
3. Verify:
   - tool changes: `qa/unit/Test-AgentInterface.ps1` and `qa/unit/Test-TestingTools.ps1` (fake Gradle, ~20 s);
   - guide edits: `qa/unit/Test-GuideSnippets.ps1` (~1 s); harness, generators, guide code or build changes: `qa/integration/Test-KitSamples.ps1` (real Minecraft, ~2 min);
   - new mechanism: one game test in the relevant fixture plus a nearby failure case. Client-visible results need a human look.
4. Replace TASK.md state; put lessons in the relevant guide, not in startup files.

## Rules for recipient docs

- AGENTS.md stays under ~3 KB (every line earned by a trial; move detail to the docs it belongs to before adding); guides under ~6 KB each; one topic per guide; INDEX.md one line per guide.
- A ```java block in kit/docs is either in qa/fixtures/guide-samples (compiled; behavior covered by a game test where behavior is the point) or it is cut. `qa/unit/Test-GuideSnippets.ps1` enforces this (`MyMod`/`mymod` = `RubymodMod`/`rubymod`; other names must match the fixture). A fragment not worth a fixture becomes prose with inline names. Check method names with `agent.ps1 api` before writing them.
- State behavior and pitfalls, not proof history. Proof details stay in qa/fixtures and docs/CAPABILITIES.md.
- New common mistakes go into tooling first: `$KitRenames` in kit/testing/Diagnostics.ps1 (compiler hints), AssetLint (server check) or a generator.

## Tool contracts

- `agent.ps1` prints plain text; `-Json` prints the full result (schemaVersion 1) for programs. There is no MCP server (removed in 0.5.0: it duplicated a subset of shell commands and needed Node).
- Profiles: environment, build (`mod`/`all` are aliases), unit, server, game. server/game add the check harness (`kitHarness` source set) and src/gametest to the dev server classpath; neither enters the jar.
- The harness writes `run/testing-server/<run>/kit-check.json` and stops the server; the runner fails on lint ERROR, failed tests or no harness report.
- A failed game test carries `area`: a bounded snapshot (12 blocks, entity counts) of what the test placed and what else is in its area, with state, redstone received and inventory. agent.ps1 keeps the last failure key in `reports/testing-tools/streak.json` and prints `STUCK` at 3 identical consecutive failures or 5 consecutive failures of one test; a PASS clears it. Both exist because unknown dead ends cannot be documented in advance: make them visible and bound their cost. Every agent.ps1 call appends one line to reports/agent-log.jsonl (action, arguments, seconds, exit, check run id); qa/local-model/Read-Trial.ps1 summarizes a trial folder from it.
- After a compiling build, agent.ps1 scans src/main and src/gametest for calls to members that exist only on the client (api index; `Get-KitClientOnlyCalls`): ERROR in game tests (fails the check), WARN in common code; @SideOnly(CLIENT) members, client classes and `onDataPacket`/`handleUpdateTag` are skipped. The same scan WARNs on `spawnEntity` without an `isRemote` test inside hooks that run on both sides (client ghosts); measured on all trial mods with no false positives (2026-10-18).
- `check client` (kit/testing/client, ClientCheckMod) writes `kit-client.json` in the harness format: lint for models/textures, `tests` for GUI container sync (`gui block|item <id>`). Sync subjects are project blocks/items overriding the right-click hook; client and server views are compared by slot index (player-inventory flag + inventory index, stack, cursor), a difference is retried once 10 ticks later before it fails. Fixture: Test-KitSamples -Client patches RubyBoxContainer into a client-only layout and expects FAIL.
- `reports/testing-tools/test-history.json` keeps each test's last 6 outcomes across PASSes; fail-pass-fail at one step prints `FLAKY`.
- Generators insert above `// kit:<marker>` lines in the init skeleton and refuse to overwrite files.
- The installed-driver path (testing/installed-driver, tools/*Installed*) is optional and unchanged; it must stay behind the `installedTestDriver` Gradle property.

## Releases

Change kit/kit.json version for changed recipient content, after `qa/Run-All.ps1` passes (all suites; `-Quick` for unit suites only while iterating), and add a kit/CHANGELOG.md entry; keep kit/KNOWN-ISSUES.md current. Regenerate packaging/files.json from the kit tree (packaging/README.md), build with packaging/Build-KitZip.ps1 and verify the extracted ZIP builds. Preserve exports and pinned dependencies. Public publishing is a separate user decision.
