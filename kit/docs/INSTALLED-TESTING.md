# Optional installed tests for a mod project

Use these tools when a feature needs a real Minecraft/Forge world or exact third-party production JARs. Prefer ordinary unit tests for isolated logic. Choose one focused scenario from the requested behavior; variants do not need a new campaign. This workflow tests installed server behavior; client/input/rendering are separate.

## Short agent loop

1. Define an observable outcome from the requested feature, including a relevant failure condition. Do not invent a passing assertion to satisfy the runner.
2. Create a project scenario, implement it, then build only the separate test driver:

```powershell
powershell -NoProfile -File ./tools/New-InstalledScenario.ps1 -Name my-feature
# Edit the returned src/installedTest/java/... file to implement the actual checks.
powershell -NoProfile -File ./tools/Build-InstalledTestDriver.ps1
```

The scaffold deliberately fails until real setup/checks are supplied. Annotated project scenarios are discovered automatically. No edit to the shared driver or manual registration list is needed. The build returns the driver path/hash, scenario count and first 20 IDs. Compile failures return bounded diagnostic lines and the full log path. Missing/duplicate IDs fail the build; unknown requested IDs fail before Minecraft starts.

3. Check/export the subject mod normally. Prepare the pinned runtime once, then reuse it:

```powershell
powershell -NoProfile -File ./tools/Prepare-InstalledServer.ps1
```

4. Select the exported subject, exact optional dependencies, new driver hash and scenario in a JSON manifest. After accepting the [Minecraft EULA](https://www.minecraft.net/en-us/eula) for this local test server:

```powershell
powershell -NoProfile -File ./tools/Test-InstalledMod.ps1 -Manifest ./your-scenario.json -AcceptEula
```

Require exit zero and PASS. Read the compact response first: scenario, phase, tick count, outcome and evidence paths. Inspect its report/log only when needed. Change/rebuild the driver when test code changes; do not rebuild the production mod merely because a test changed. Reuse runtime caches and unchanged build outputs. Token/cost savings have not been measured.

## Scenario API

A public class implements InstalledScenario and declares @InstalledTest("my-feature"). Its plan(TestContext t) queues actions; callbacks execute on the server thread at normal END ticks. Test helpers and normal Minecraft/Forge APIs are both available. This is a small Java API, not a new language or an exhaustive mod-feature catalog.

| API | Purpose |
|---|---|
| then(label, action) | Arrange or interact at this sequence position; exceptions fail with the active label |
| check(name, condition, detail) | Assert immediately when this step executes |
| waitTicks(count) | Allow this many normal server ticks to pass |
| waitUntil(name, timeoutTicks, condition, detail) | Poll over normal ticks; record success once or fail with current state on timeout |
| invariant(name, condition, detail) | From this point, check every observed tick and record once on completion; first violation fails |
| world(), server(), origin(), ticks() | Access the real runtime and an overworld position within spawn residency |
| block(id), item(id), place(pos, id/state), tile(pos, type), inventory(pos) | Small registry/world helpers with explicit missing/type errors |
| parameters(), string(key, fallback), integer(key, fallback) | Read scenario-specific JSON inputs |

For example, in plan(), with arrange/hasExpectedOutput/describeOutput implemented by the project:

```java
t.then("arrange", this::arrange);
t.waitUntil("processed", 100, this::hasExpectedOutput, this::describeOutput);
```

Detail suppliers should describe observed state (counts, energy, entity status), not just repeat the expectation. Assertion names become scenario.NAME in reports and expectedAssertions. Names must be unique. Every completed scenario must have at least one behavior assertion; successful loading or an empty plan cannot pass. A condition timeout reports its assertion name, elapsed ticks and current detail. Invariants report their number of observed ticks.

The manifest's maxTicks bounds the whole scenario; timeoutSeconds also bounds the Java process. Keep both short for the mechanism. Plans allow up to 256 steps and 32 active invariants to bound accidental runaway scheduling. Arbitrary callback code must remain short/nonblocking; the process timeout is the outer limit. Random mob spawning is disabled by a world rule, while explicitly spawned animals/monsters are allowed. A scenario can change world rules through the normal API when that behavior is under test.

The hopper example lives under testing/installed-examples, outside the compiled core. Copy it into src/installedTest/java/example/installedtests only when useful. It illustrates one inventory interaction; its PASS says nothing about other mod features. Project source/resources are included only in the separate test-driver JAR, never the production source set. Default scenario compilation has Forge APIs but no subject class output. For a direct third-party API, add an explicit mapped test-only compile dependency; never bundle it or add it to main as a shortcut.

## Manifest

Paths resolve relative to the scenario manifest; an exported artifact path resolves relative to its export manifest. Replace the placeholders below with actual returned paths/hashes. Dependencies may be an empty array.

```json
{
  "schemaVersion": 1,
  "mode": "server",
  "runtimeLock": "reports/installed-runtime/1.12.2-14.23.5.2859/runtime-lock.json",
  "scenario": "my-feature",
  "maxTicks": 400,
  "timeoutSeconds": 90,
  "subject": { "modId": "yourmod", "exportManifest": "C:/project/reports/releases/EXPORT/manifest.json" },
  "driver": { "modId": "installedtestdriver", "path": "C:/project/reports/installed-driver/BUILD/installed-test-driver-1.0.0-srg.jar", "sha256": "DRIVER_SHA256" },
  "dependencies": [],
  "expectedAssertions": ["scenario.processed"],
  "parameters": {}
}
```

Each optional dependency has modId, path and SHA-256. Obtain release JARs/required companions separately from their publishers. An external subject can use path/sha256 instead of exportManifest. Developer JARs needing development mappings are not interchangeable with production release JARs. Currently declare one mod ID per JAR. Arbitrary config-file staging and cross-JVM persistence require explicit extensions.

## Runtime and evidence

No Prism/PolyMC is required. Runtime preparation downloads the pinned normal Forge installation, verifies its installer and locks its files. Complete matching caches are reused offline; an incomplete/changed cache is rejected. Preserve its log and choose a new -CacheDirectory for repair. -Java8Home selects an explicit game JDK. Game/third-party files, accounts, caches and worlds are excluded from the kit ZIP.

Each run gets fresh mods/config/world files under reports/installed-tests/RUN/game and binds to loopback. Exact input/staged hashes, loaded mod/container/class origins, named assertions, driver phase, Java and runtime/configuration identities are retained. The subject and selected project scenario classes must load from their staged JARs. Tests use normal game ticks; direct fixture placement/API actions remain test actions, not simulated player input.

Missing/changed inputs, unsupported driver catalogs, incomplete results, failed/missing assertions, crashes and timeouts fail. Full logs/results/worlds remain for diagnosis; failure diagnostics are bounded. Only the runner's own process tree is stopped on timeout. This is file/process isolation, not a security sandbox for untrusted JARs. Results do not automatically change agent/MCP status or manual installedArtifact evidence.

Legacy details handled by the supplied code: Forge may report jar: CodeSource URLs; Windows PowerShell needs plain diagnostic strings before JSON serialization, explicit UTF-8 and its own Modules directory before foreign inherited module paths.
