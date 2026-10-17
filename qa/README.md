# Maintainer QA

Run what the change affects (docs/DEVELOPMENT.md). From the repository root:

| Check | Command | Covers |
|---|---|---|
| Agent interface | `powershell -NoProfile -File qa/unit/Test-AgentInterface.ps1` | status, init, all generators, version, compact/JSON output, compiler hints, errors, export gate (fake Gradle) |
| Runner contract | `powershell -NoProfile -File qa/unit/Test-TestingTools.ps1` | exit codes, server/harness handshake, lint ERROR and failed game tests, timeout (fake Gradle) |
| Guide snippets | `powershell -NoProfile -File qa/unit/Test-GuideSnippets.ps1 [-Detail]` | every ```java block in kit/docs occurs in fixtures/guide-samples (~1 s; also run first by Test-KitSamples) |
| Generators + guide samples | `powershell -NoProfile -File qa/integration/Test-KitSamples.ps1` | real Minecraft: generated mod and every guide sample compile, lint clean, pass game tests |
| Setup/Java/launcher | `qa/unit/Test-*Setup.ps1`, `Test-*JavaEnvironment.ps1`, `Test-WingetLauncher.ps1`, `Test-SetupLauncher.ps1` | setup scripts |
| Mapped source | `qa/unit/Test-MappedSource.ps1` | source reader |
| Packaging | `qa/unit/Test-Packaging.ps1`; candidate: `qa/integration/Test-KitZip.ps1 -Manifest <path>` | allowlist and ZIP |
| Local-model trials | `qa/local-model/README.md`: held-out PROMPTS.md, Read-Trial.ps1 (unattended run summary), results/ | unseen mods built from the packaged kit by a local model (OpenCode), with a Claude Code control run (manual) |
| Installed-artifact harness | `qa/integration/Test-InstalledHarness.ps1`, `Test-InstalledDiscovery.ps1`, `Test-InstalledWorkflow.ps1` | optional installed-server path |

Fixtures:
- `fixtures/guide-samples`: complete mod containing every java block in kit/docs, enforced by Test-GuideSnippets (paragraphs match in order, whitespace and comments ignored; `MyMod`/`mymod` = `RubymodMod`/`rubymod`).
- `fixtures/generators/GeneratorTests.java`: behavior tests for generator output.
- Other `fixtures/*`: accepted proof projects (sources, checklists, evaluation mods). They document how mechanisms were proven and remain useful worked examples.

Historical runners: most `integration/Invoke-*`, `Test-<Proof>.ps1`, `Test-*KitCandidate.ps1`, `Test-ForgeSkill.ps1`, `Test-BetaWorkflow.ps1` and `Test-VersionDelivery.ps1` were written for the pre-refactor runner (JSON by default, `-Action check -Profile mod`, starters under kit/.agents/skills/.../assets, Record-Evidence). They need `-Json` and the new kit/examples paths before reuse; adapt one only when its mechanism changes.

QA code never enters recipient ZIPs or mod jars.
