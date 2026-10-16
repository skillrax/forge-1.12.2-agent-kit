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

- AGENTS.md stays under ~2.5 KB; guides under ~6 KB each; one topic per guide; INDEX.md one line per guide.
- A ```java block in kit/docs is either in qa/fixtures/guide-samples (compiled; behavior covered by a game test where behavior is the point) or it is cut. `qa/unit/Test-GuideSnippets.ps1` enforces this (`MyMod`/`mymod` = `RubymodMod`/`rubymod`; other names must match the fixture). A fragment not worth a fixture becomes prose with inline names. Check method names with `agent.ps1 api` before writing them.
- State behavior and pitfalls, not proof history. Proof details stay in qa/fixtures and docs/CAPABILITIES.md.
- New common mistakes go into tooling first: `$KitRenames` in kit/testing/Diagnostics.ps1 (compiler hints), AssetLint (server check) or a generator.

## Tool contracts

- `agent.ps1` prints plain text; `-Json` prints the full result (schemaVersion 1) for programs. There is no MCP server (removed in 0.5.0: it duplicated a subset of shell commands and needed Node).
- Profiles: environment, build (`mod`/`all` are aliases), unit, server, game. server/game add the check harness (`kitHarness` source set) and src/gametest to the dev server classpath; neither enters the jar.
- The harness writes `run/testing-server/<run>/kit-check.json` and stops the server; the runner fails on lint ERROR, failed tests or no harness report.
- Generators insert above `// kit:<marker>` lines in the init skeleton and refuse to overwrite files.
- The installed-driver path (testing/installed-driver, tools/*Installed*) is optional and unchanged; it must stay behind the `installedTestDriver` Gradle property.

## Releases

Change kit/kit.json version for changed recipient content. Regenerate packaging/files.json from the kit tree (packaging/README.md), build with packaging/Build-KitZip.ps1 and verify the extracted ZIP builds. Preserve exports and pinned dependencies. Public publishing is a separate user decision.
