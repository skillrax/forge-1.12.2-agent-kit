# Focused maintainer QA and examples

Run checks relevant to the changed mechanism/tool. A small proof normally needs a mod build and short gameplay/logging check, with a nearby failure case if useful. Deep campaigns are optional and do not govern the new release goal.

| Change or lookup | Available asset |
|---|---|
| Validation/diagnostics | `unit/Test-TestingTools.ps1`, `unit/Test-AgentInterface.ps1` |
| Setup/Java/launcher | Matching `unit/Test-*Setup.ps1`, `Test-*JavaEnvironment.ps1`, `Test-WingetLauncher.ps1`, `Test-SetupLauncher.ps1` |
| Mapped source | `unit/Test-MappedSource.ps1` |
| Packaging | `unit/Test-Packaging.ps1` |
| New candidate | `integration/Test-KitZip.ps1 -Manifest <new-manifest-path>` |
| Version/export behavior | `integration/Test-VersionDelivery.ps1` |
| Installed production JARs / optional third-party integration | `integration/Test-InstalledWorkflow.ps1` (inspect its explicit artifact parameters); recipient workflow: `kit/docs/INSTALLED-TESTING.md` |
| Generic installed scenario lifecycle / discovery | `integration/Test-InstalledHarness.ps1`, `integration/Test-InstalledDiscovery.ps1`; setup: `fixtures/installed-harness/README.md` |
| Optional MCP | `npm --prefix qa ci`, then `npm --prefix qa test` |
| Playable patterns | `fixtures/workshop`, `fixtures/clockwork-labels`, `fixtures/aegis-apple` |
| Relevant example reproduction | Matching `integration/Test-Workshop.ps1`, `Test-Clockwork*.ps1`, `Test-AegisApple.ps1`; inspect parameters first |
| Small energy proof | `integration/Test-Energy.ps1`; gameplay checklist in `fixtures/energy/README.md` |

Example from root: `powershell -NoProfile -File qa/unit/Test-Packaging.ps1`.

Fixtures are example/QA source outside the recipient package. Some historical runners execute extensive assertions even in Reproduce mode; use them only when useful. They seed existing solutions and are not fresh AI trials. Do not rewrite unused runners solely to reduce a campaign.

Thermal and benchmarks are frozen under `archive/2026-10-12-pre-pivot/qa/`; restoration limits are in archive/README.md. Maintainer QA/research stays outside the ZIP. Optional reusable installed-test support is explicitly packaged under kit/testing; its driver builds to a separate JAR and never enters the production mod.
