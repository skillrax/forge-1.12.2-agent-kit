# Maintainer entry point

This repository maintains the kit in `kit/` (what recipients get). Read TASK.md for the current state. Do not run recipient setup as a maintainer step; recipient instructions start at kit/AGENTS.md.

Goal: an AI agent, including small local models, turns a user's description into a correct Minecraft 1.12.2 Forge mod with few tokens and few mistakes. Prefer a generator over a check, a check over a hint, a hint over prose. Workflow, doc rules and tool contracts: docs/DEVELOPMENT.md. Lookup routes: docs/REFERENCES.md.

Verify what you change (qa/README.md): fast unit tests for tools, `qa/integration/Test-KitSamples.ps1` for harness/generators/guides. Every guide code sample must exist, compiled and tested, in qa/fixtures/guide-samples.

Keep context small: search a named directory, read bounded sections. Do not bulk-read examples, fixtures or evidence. History belongs in Git; archives, old evidence and exports stay in the original workspace (docs/FORK.md).

Before handoff, replace TASK.md (objective, result/evidence, next action, blocker; under 2000 characters). Version: kit/kit.json. No public publishing without the user's decision.
