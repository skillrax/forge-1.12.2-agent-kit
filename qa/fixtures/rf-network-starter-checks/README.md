# RF starter adaptation checks

QA-only source used by `qa/integration/Test-RfNetworkKitCandidate.ps1` on the actual extracted kit ZIP. No classes/resources from this directory enter the kit or exported production JAR.

`StarterChecks` exercises twelve renamed/tuned starter assertions: exact upgraded lava credit and simulation, saved reserve, all-face storage output, live face permissions, shared path accounting, zero cable buffer, pending-owner safe dirty marking, and held FE ports after queue/replacement. `QueuedChunkChecks` verifies a ten-chunk path across pending and actual intermediate unloading (27 assertions). `ClientChecks` verifies server-synchronized large fields, face/numeric actions, invalid action rejection and five framebuffer screenshots.

Evidence and inspected screenshots: `evidence/rf-network-kit/summary.json`. Accepted original gameplay remains `evidence/rf-network/1.0.1/user-acceptance.json`; these checks do not repeat that campaign. The reused runner's generic scope string describes the original proof; the candidate summary records the narrower actual fixture scope.
