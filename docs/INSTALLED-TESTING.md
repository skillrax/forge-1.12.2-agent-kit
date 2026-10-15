# Proposed installed-artifact testing

Status: server workflow implemented with an exact production starter export, actual Iron Chests release and separate remapped driver. Natural-tick transfer/accounting, result-integrity and extracted-candidate evidence: evidence/installed-tests/summary.json. Recipient instructions: [kit workflow](../kit/docs/INSTALLED-TESTING.md). Installed client/graphical milestones remain planned. Preserve exports and pinned build dependencies.

The reusable harness now auto-indexes @InstalledTest project classes from src/installedTest/java. TestContext handles sequencing, bounded normal-tick waits, invariants and assertions; no shared-driver scenario switch remains. The hopper case is an optional example outside core compilation. Generic lifecycle/discovery and candidate evidence: evidence/installed-harness/summary.json. Vanilla entity age ticking checks harness generality, not custom-entity gameplay. Start with the compact recipient guide; the sections below retain the runtime design/boundaries.

## Objective

Run an exact exported production mod JAR with optional exact third-party release JARs inside an isolated, normally installed Forge runtime. Automate a meaningful behavior scenario with bounded evidence. Gradle runClient/runServer and development class directories are not substitutes for installed-artifact evidence.

Prism/PolyMC remain optional manual tools. Dedicated-server testing is the first deliverable. A graphical client still needs launch preparation, game files, libraries, natives, assets and a supported session mechanism. Installed-client testing is a separate milestone; graphical previews build on it.

## Runtime contract

- Read game/Forge pins from kit/kit.json; select Java 8 explicitly through existing discovery. Do not change build pins to ease installation.
- Ship scripts, installation recipes, separate test-driver source/build support and guidance. Prepare game/Forge downloads locally. Exclude game files, downloaded mods, accounts, caches and worlds from the kit ZIP. Obtain required EULA acceptance explicitly, separate from test results. Distribution reference: https://www.minecraft.net/en-us/eula .
- Use the pinned official Forge installer and official game/library metadata where applicable. Lock file origins and verified upstream digests where available, plus SHA-256 identities. Reject changed files; never substitute latest. First installation requires network access; verified complete caches should support reuse. Prove the exact legacy installer procedure rather than assuming current installer commands apply. Forge distribution: https://files.minecraftforge.net/net/minecraftforge/forge/index_1.12.2.html .
- Consume the existing export manifest and verify its exact subject JAR hash. Allow explicitly supplied external artifacts with expected hashes, reporting their different provenance. Never select JARs by wildcard order or latest timestamp.
- Declare every third-party JAR, required companion and scenario configuration. Installed tests use normal release artifacts. Developer JARs needing development mappings belong to a separate workflow, never an automatic fallback.
- Keep test dependencies out of main configurations and production packaging. A production API compile dependency is a separate explicit decision.

## Isolation and driver

Keep verified runtime caches separate from per-run game directories. Each run gets its own mods/config/world/logs/results, containing only declared artifacts. Never borrow gameplay worlds/configs/mod collections or put development output on the game's classpath. Verify staged hashes before/after execution; report loaded mod IDs/versions and code origins. This is file/process isolation, not a security sandbox for untrusted JARs.

Use a separate Forge test-driver mod, built/remapped for installed execution. Its scenario code stays outside src/main and outside the subject JAR. Prefer registry IDs and public world/capability interfaces; compiling against a target API must not bundle target classes. Exercise normal ticks/events. Do not call implementation update methods directly and claim normal tick/player behavior.

Pass a fresh run ID to the driver. Require structured results tied to the run/scenario, every declared assertion, no assertion failure and clean shutdown. Readiness, zero exit code or a generic PASS log line alone cannot pass behavior. Retain full logs with bounded diagnostics. Crash, timeout and missing completion fail. Stop only processes owned by the run. Bind server tests to loopback with an available port.

## Interface and evidence

Shell command: tools/Test-InstalledMod.ps1 -Manifest <scenario.json> -AcceptEula. Prepare with tools/Prepare-InstalledServer.ps1 and build the separate driver with tools/Build-InstalledTestDriver.ps1. Shell results are independent of existing agent/MCP status; no wrapper added.

Inputs identify server/client mode, subject export/artifact, dependencies/hashes, driver/scenario, configuration, time limits and explicit restart phases if needed. Reports identify exact subject/dependency/driver/runtime bytes, Java, world/config identity, loaded code origins, named assertions, process outcome, timings and evidence paths. Distinguish infrastructure failures from behavior failures. Report installedServer and installedClient independently; capture success and appearance acceptance are separate claims. Relevant artifact/runtime/scenario/config changes invalidate prior evidence.

## Implementation sequence and stopping conditions

1. **Installed-server feasibility.** Prepare the pinned server, load one existing small production export and separate driver, assert one natural-tick world interaction, record staged JAR origins/hashes and clean shutdown. No development runtime on the game classpath.
2. **Third-party proof.** Select one concrete interaction and actual release dependency with required companions. Assert cross-mod behavior. A synthetic dependency may check plumbing but cannot establish third-party interoperability. Check missing/changed inputs fail before launch and driver failure/missing completion cannot yield PASS; these cover result integrity, not a general fault campaign.
3. **Recipient delivery.** Extract reusable runner/driver patterns and concise guidance. Update package allowlist and version only when preparing changed recipient content. Check actual ZIP extraction/inclusion and one focused installed test from it. Persistence scenarios alone need a second JVM/save-reload check. Preserve older candidates and exports.
4. **Installed-client feasibility.** Prove a production-only launch on a visible Windows desktop using client libraries, natives, assets, Forge launch metadata and a supported owned-account/session setup. Resolve session provisioning before promising unattended execution. Do not read/copy launcher credentials or log tokens. An explicitly selected local installation may supply verified files but must not become an undeclared requirement. Account setup and repeatable testing are separate steps; a general-purpose launcher/account manager is outside the server deliverable.
5. **Client behavior/previews.** Enter a disposable world with exact production artifacts and a client-capable driver, execute a bounded scene/input scenario, capture results and exit. Record normal input/packets versus test-only API actions. Client/server connection, intended appearance and graphics-state restoration require actual observations; server PASS does not establish them.

## Remaining choices

Server feasibility and one actual third-party interaction now pass. Iron Chests is a representative inventory dependency, not a claim about all recipient mods. Its release file advertises a different version than embedded Forge metadata; exact SHA-256 plus runtime version are both retained. Client file/session provisioning still needs a bounded technical investigation. Launcher-free graphical testing is a planned goal, not an established capability or a release gate for the first server workflow.
