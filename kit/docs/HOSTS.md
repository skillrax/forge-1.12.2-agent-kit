# Agent apps

The kit is plain files plus PowerShell; it calls no AI service and stores no keys. Any agent that can read/write files and run PowerShell in this folder works.

| Host | Setup |
|---|---|
| Claude Code | open this folder; CLAUDE.md imports AGENTS.md automatically |
| Codex and other AGENTS.md-aware agents | open this folder; AGENTS.md is read automatically |
| Other agents / local models | make AGENTS.md the system prompt (or first message) and give the agent a PowerShell tool rooted here |

A shell tool is required: generators, checks and API lookups are all `agent.ps1` commands. Agents limited to file editing without command execution cannot use this kit on their own.

Small-context models (32k): AGENTS.md (~600 tokens) + one guide (~1-1.5k) + compact check output fits easily. Prefer generators over hand-written code, run `check` after every edit, and use `api` lookups instead of reading source files.

Programs driving the kit can add `-Json` to any `agent.ps1` command for the full result object (schemaVersion 1).
