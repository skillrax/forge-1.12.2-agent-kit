# Focused promoted-starter checks

Maintainer-only validation of the recipient configurable-machine starter. Run `powershell -NoProfile -File qa/integration/Test-ConfigurableMachineKitCandidate.ps1 -Manifest releases/<kit-version>/manifest.json`; use the version from `kit/kit.json`.

The runner extracts the actual ZIP, checks exact allowlist/hashes and relevant relative links/anchors, creates explicit Java/resource destinations, copies the packaged starter and builds its production JAR. Only afterward it copies these isolated evaluation classes and the accepted proof's dev overlay into the extraction. Assertion classes and demonstration power blocks must not be present in the production JAR.

Eleven server-world assertions focus on the changed recipe/configuration boundary: original upgrade/furnace fallback, a QA-only 3-cobblestone/2-sand entry with 4-stone/1-flint outputs, exact counts/RF, saved identity/progress, blocked-output resume, a dynamically registered furnace result, live face permissions/simulation, automatic output, controls and diagnostics. No accepted gameplay suite is replayed. Client QA observes the actual renamed menu/control synchronization and hover diagnostic; screenshots require inspection. These are server-world update checks and client captures, not an independent user gameplay/restart campaign.

Latest evidence: `evidence/configurable-machine-kit/summary.json`. Accepted behavioral basis remains `evidence/configurable-smelter/1.0.1/user-acceptance.json`. The original intermittent stall was closed unreproduced at the user's request; promotion does not imply a new root cause or fix. No broad regression, stress, arbitrary-handler compatibility or quantitative efficiency benchmark is claimed.
