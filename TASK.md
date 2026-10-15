# Current maintainer task

Objective: Make installed production-JAR tests generically reusable and economical for mod authors, without shared-driver edits per project or QA in production.

Result/evidence: PASS evidence/installed-harness/summary.json. Project-owned @InstalledTest classes under src/installedTest/java are indexed at build time. Shared TestContext supplies sequenced actions, checks, bounded normal-tick waits, per-tick invariants and world/registry helpers. Empty plans fail; unknown IDs fail before game launch. New-InstalledScenario scaffolds a deliberately unfinished project test; build results list discovered IDs and bounded compiler diagnostics. Core has no mod-specific scenarios; hopper moved to optional examples. Guide: kit/docs/INSTALLED-TESTING.md.

Delivery: releases/0.4.0-beta.4/candidate ZIP/manifest, 175 exact allowlisted files. Extracted space/Unicode path verified; normal production build excludes all test classes/catalog. Five lifecycle cases, three duplicate/removal/restoration discovery cases, actual Iron Chests transfer and no-project build rejection PASS. Discovery and candidate driver bytes match; project changes preserve core hashes. Previous candidates/exports/pins preserved; no remaining test server.

Next action: Use project scenarios against concrete mod acceptance, preferring unit tests for isolated logic and installed tests for world/artifact questions. Next separate milestone is installed graphical-client file/native/asset and supported session provisioning, then client checks/previews. Avoid adding domain-specific tests to core or default broad campaigns.

Blocker: None for delivered generic server workflow. Feature-specific assertions still require project reasoning. No measured token-cost improvement, client, restart, cold-PC or broad modpack claim. No public publishing.
