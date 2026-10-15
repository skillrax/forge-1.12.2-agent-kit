# Minecraft1.12.2 kit

Read START.md, run agent status and resume TASK.md. Keep objective, acceptance, next step and results under2000 characters. Load only the relevant Forge skill reference.

Setup requests: use the known Setup.cmd administrator route in START.md; it asks separate Y/N choices for Java settings and recommended Node/MCP before downloads. A ZIP attachment needs extraction and a project rooted in that folder first. Confirm status.projectRoot; a launched process is not setup PASS. Stop on cancellation/failure with the next action and log pointer. Reuse fresh matching environment PASS; Node/MCP are optional. MCP module-load PASS is not app connectivity evidence.

Preserve the pinned game/Forge/mappings and Java8 baseline. Production is src/main; keep client rendering out of common/server classes and assertions out of the JAR.

Use -Action check -Profile mod (mod is not an Action); unit requires executed tests; server proves startup only. Require exit0/PASS and stated scope. Failures include bounded diagnostics; use -Action errors for that run when needed, fix its cause and rerun. Reuse fresh evidence unless external conditions changed; do not duplicate successful baselines. Checks reuse unchanged Gradle work; -Clean requests recovery. Removed source/resource paths or changed build configuration trigger automatic clean.

For APIs use tools/Read-MappedSource.ps1 with an exact Class/Member and bounded MaxLines. Avoid whole classes/overlapping snippets. Text is UTF-8. See on-demand skill/workflow guidance when needed.

Use a focused build and short check of new behavior. Extra fault/load/lifecycle checks need a concrete issue or claim; do not repeat unchanged passes. Report unchecked behavior. User instructions/model settings take precedence; do not change them.

Delivery: export the checked versioned JAR with tools/Export-Release.ps1. Give its path/version, checked scope and pending gameplay/client/install checks. Changed deliveries need a new mod version with synchronized metadata and preserved prior exports; follow user version policy. Update TASK.md with outcome and remaining checks before closing.
