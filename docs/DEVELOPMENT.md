# Small development loop

The goal is a usable kit backed by representative examples. Thermal qualification and measured token-savings gates are retired. Existing QA is available for diagnosis and focused regression; it is not mandatory on every task.

1. State one missing mechanism or observed defect and a short stopping condition in TASK.md.
2. Read the relevant reference/example. Reuse demonstrated patterns; avoid cataloguing future variants.
3. Implement the smallest useful example/fix. Keep production, client-only code and QA separate.
4. Run the affected build/check and one short gameplay/logging scenario. Include a nearby failure case when relevant, such as full outputs for a machine. Server startup proves startup only.
5. Record outcome, example/artifact path and material limits. Extract a short reusable lesson if useful. Stop when the stated mechanism works.

Persistence changes may need one save/reload check; client changes need client observation; transfers need simple accounting. Whole lifecycle matrices, fault injection, large loads and multi-run comparisons are optional investigations for a specific issue/claim. Do not repeat unchanged passing checks without an affected input or concrete reason.

## Private beta preparation

Stop capability expansion for this candidate. Focus on recipient workflow, token efficiency and reliability with the existing demonstrated mechanisms.

Implemented workflow improvements: failed checks return bounded causes; status recommends a focused next action; validation records the exact inspected JAR; checkout preflights required entries and rejects source-descendant/junction destinations; MCP identity reads kit.json; fresh-PC instructions follow the actual launcher.

Optional installed-artifact server testing now has a separate runner/driver and evidence contract: [recipient guide](../kit/docs/INSTALLED-TESTING.md). Use it for an affected production-JAR or third-party interaction question, not as a new default release gate. Existing development-server PASS remains readiness evidence only.

Project scenarios live in src/installedTest and are auto-indexed; use the shared tick/assertion lifecycle rather than editing the core driver per mod. Define real outcomes from feature acceptance, inspect compact failures first, and keep unit tests for isolated logic. Build/runtime reuse and bounded diagnostics reduce repeated work; no quantitative token-cost improvement is claimed.

Normal builds reuse unchanged Gradle work, with automatic clean for missing/failed baselines, changed build configuration or removed source/resource paths. Explicit -Clean remains available. Real checks confirm unchanged compilation reuse, removed/renamed class/resource exclusion and exact-artifact export despite an extra JAR. MCP setup reuses a matching dependency/Node identity only after module readiness, supports forced repair and bounds/logs npm execution. Initialize-Mod provides an empty, consistent foundation and refuses existing mod content.

Evidence: evidence/beta-workflow/summary.json; Node installation/prompt basis: evidence/node-setup/summary.json. These are focused workflow/build observations, not measured token savings, cold-PC acceptance or gameplay proof. Preserve pinned dependencies, exports, QA separation and bounded lookup. The optional mapped-source MCP wrapper remains a later convenience, outside this beta preparation pass.

## Beta candidate stopping rule

- Working setup/build/export and correctly allowlisted package.
- Existing capabilities indexed and accepted examples retained.
- A short separate-PC trial of setup, one small requested mod, export and actual installed gameplay; report cold-cache status and interventions honestly.
- Concise relevant guidance and known limitations.

Machine/pipe variants and new capabilities do not add gates. Other missing mechanisms are future tasks. A private beta candidate can be handed off for the separate-PC trial with that trial explicitly pending. This candidate does not claim full Thermal equivalence, arbitrary compatibility, large-network performance, universal autonomy or measured token savings.

Change kit version only when preparing changed recipient content; source of truth is kit/kit.json. Preserve archives/exports. Check affected tooling after tooling changes; check actual ZIP contents/extraction for a new candidate. Public publication remains a separate user decision with applicable license/notice checks.

## Context and handoffs

AGENTS.md holds stable instructions; TASK.md is the single current-state handoff. Keep both short and replace stale state instead of appending history. README.md maps the workspace. These three docs are on-demand references, not startup prerequisites. History belongs in Git, immutable exports or archive/.

Before interruption, leave the next executable action and evidence path in TASK.md. A new chat should resume without reconstructing the timeline. Do not add a new plan/report for every small step.

Optional installed driver registration must remain behind the installedTestDriver Gradle property. The remapper attaches registered JAR tasks to assemble; unconditional registration makes an empty recipient build require test scenarios. Build-InstalledTestDriver.ps1 opts in explicitly. Keep ordinary foundation builds and the separate driver build as distinct checks when changing this boundary.
