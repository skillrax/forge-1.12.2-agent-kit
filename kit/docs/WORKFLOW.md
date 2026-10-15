# Validation and evidence

For an empty foundation, tools/Initialize-Mod.ps1 -ModId mymod -Name "My Mod" -Package com.example.mymod -Version 1.0.0 creates one Forge entry point and synchronizes build/resource metadata and artifact naming. It rejects existing src/main content. Record gameplay acceptance and implement the feature afterward; initialization is not a working gameplay proof. Existing mods should be adapted directly.

Close a delivery with the exported JAR path and mod version, the matching PASS scopes/run IDs, and explicit pending gameplay/client/independent-install checks. Update TASK.md with the delivered outcome and remaining acceptance. A launched setup process or a build alone is not completion of the user's full acceptance. After Y setup, restart applications and confirm fresh project-root evidence before resuming.

agent.ps1 -Action check -Profile all permits an empty foundation; mod requires a Forge entry point; unit requires executed JUnit tests; server checks readiness/shutdown. PASS applies only to that scope. Failed checks include bounded diagnostics; use -Action errors -RunId <new-id> when more context is needed. Status with -Full includes the entire latest report. Status nextAction uses current evidence and source presence; TASK.md/user acceptance remains authoritative.

Normal all/build/mod checks reuse unchanged Gradle outputs while inspecting the current SRG JAR. First/failed baselines, changed build configuration and removed source/resource paths trigger clean automatically. Use -Clean (MCP clean: true) for an explicit recovery build. Reports retain path inventory/clean reason; compact tool responses omit internal inventories. The artifact hash points to the exact JAR named by inspection, never the first matching directory entry.

src/test/java is for meaningful logic tests. Game registries/ticking need bootstrapped runtime assertions. Add validation-only code to a separate evaluation source set used by development execution, never the distribution JAR. Client rendering and independently installed artifacts need their own observations.

The server profile creates a new isolated world for each run. Restart acceptance must deliberately configure a separate persistent test world across JVMs and assert actual vanilla save/load; do not mistake a missing fresh-world save for a mod defect. Keep this acceptance setup out of production source, and distinguish disk persistence from live player logout/login.

Record-Evidence.ps1 records explicit behavior/client/installedArtifact observations and hashes their files/artifact; it cannot establish the truth of a manual statement. Export-Release.ps1 preserves the exact JAR after a fresh mod PASS. Reports are generated under reports/; keep evidence alongside the matching source/artifact for review. External cache changes may require rechecking despite matching fingerprints.

For visuals run gradlew.bat runClient. Inspect name, model/texture, creative tab, stack/recipe and interactions in a disposable world. For an independent installation use matching pinned Forge/game versions and exact SRG JAR hashes; retain logs/screenshots and stop processes after the trial. No graphical/independent-install claim follows from build/server PASS.

For an automated production-JAR server scenario, use the optional [installed-test workflow](INSTALLED-TESTING.md): a pinned installed runtime, exact subject/dependency hashes and a separate test-driver JAR. Add annotated project scenarios in src/installedTest/java; the shared harness supplies sequencing, normal-tick waits, assertions and failure reports without edits to its core. Named behavior/origin evidence remains independent from the ordinary development-server profile. The optional hopper example proves one interaction only; it does not infer arbitrary mod behavior or graphical acceptance.


Source inspection: powershell -NoProfile -File ./tools/Read-MappedSource.ps1 -Class net.minecraft.item.ItemFood -Member onItemUseFinish -MaxLines 30. Output includes declaration locations, bounded line numbers and whether more lines exist. Custom -Archive is explicit and caller-selected. The helper does not change source/build inputs or index unrelated archives.

Use a focused build and short behavior check for the requested mechanism. Fault injection, stress and broad lifecycle checks are optional for a concrete defect or claim. Document which scope ran. Read errors before full logs and stop unchanged failing retries.

In PowerShell batches set $ErrorActionPreference='Stop' and use paths anchored to the current checkout. Check $LASTEXITCODE for native processes: terminating PowerShell errors alone do not stop a failed native check. Verify the edit/copy completed before dependent validation.

Each newly delivered changed mod should have a new version: normally1.0.0 to1.0.1 for a fix,1.0.0 to1.1.0 for a compatible feature. Sync Gradle version, declared @Mod/mcmod.info versions, artifact filename and export manifest. The mod check validates declared metadata against Gradle; Export-Release requires versioned naming and rejects differing hashes already exported under that name/version. Earlier snapshots are preserved. This guard is local to reports/releases; it cannot know deliveries whose history was deleted or never exported. Renaming the artifact prefix must not be used to evade versioning. Intermediate edits needn't increment repeatedly; user-specified version policy takes precedence.

For absent members/fields, use Read-MappedSource.ps1 -Class <exact-class> -Declarations -MaxLines 20, then a bounded -Line read. Discovery lists conservative public/protected/private declarations, including same-line annotations and fields; it is not a Java parser and may miss multiline/package-private declarations. Bounds remain 80 lines and 240 characters per line. errors includes scope, fingerprints and report/raw-log paths; retain full failures.

Legacy Forge can report nonfatal ASM discovery errors for module-info.class in Java9 metadata of existing dev dependencies. Preserve those logs and distinguish the exact metadata failure from mod startup/assertion failures; never dismiss unrelated errors. Use affected checks for changed behavior/tooling and inspect packaging when delivering; no default whole-suite campaign is required.
