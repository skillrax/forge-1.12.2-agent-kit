# Current maintainer task

Objective: Independent agent-neutral development fork, preserving the original Codex environment.

Result/evidence: COMPLETE. docs/FORK.md records provenance, boundaries and rollback. Independent Git baseline snapshots current working source, including uncommitted changes. Core setup/checkout no longer require .codex; shared shell/MCP workflow, explicit skill loading, CLAUDE.md pointers and optional integrations/codex sample are documented in kit/docs/AGENT-HOSTS.md. Kit identity and package naming are neutral. Pins retained.

Verification: evidence/agent-neutral/summary.json. Node/Java setup contracts, real stdio MCP, source checkout without .codex, exact allowlisted ZIP extraction, shell-only setup without Node, extracted foundation build and Unicode JUnit PASS. Optional driver explicitly builds one scenario; an empty driver still rejects. Fixed inherited assemble dependency on optional driver via explicit Gradle opt-in. No production QA classes. Original active-file hashes verified; only its TASK.md handoff changed.

Next action: Open this maintainer folder in another agent and read AGENTS.md/TASK.md. For a small mod trial open kit/ or create a fresh tools/New-Checkout.ps1 destination; follow START.md and docs/AGENT-HOSTS.md. Record the chosen host/model, intervention and behavior evidence. Historical evidence, exports and archives remain in ../Agentic-Codex-projects.

Blocker: None for this fork. Specific Claude/local/model integration, cold-PC provisioning and graphical gameplay remain unverified. No public publishing.