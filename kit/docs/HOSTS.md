# Agent apps

The kit is plain files plus PowerShell; it calls no AI service and stores no keys. Any agent that can read/write files and run PowerShell in this folder works.

| Host | Setup |
|---|---|
| Claude Code | open this folder; CLAUDE.md imports AGENTS.md automatically |
| Codex and other AGENTS.md-aware agents | open this folder; AGENTS.md is read automatically |
| OpenCode with a local model (tested) | open this folder; AGENTS.md is read automatically. Point a custom OpenAI-compatible provider at the server; set the model's output limit to at least 16k, since thinking models can spend a smaller budget entirely on thinking and loop |
| Other agents / local models | make AGENTS.md the system prompt (or first message) and give the agent a PowerShell tool rooted here |

A shell tool is required: generators, checks and API lookups are all `agent.ps1` commands. Agents limited to file editing without command execution cannot use this kit on their own.

Small-context models (32k-64k): AGENTS.md (~750 tokens) + one guide (~1-1.5k) + compact check output fits easily. Quantized 27B-class local models built the kit's held-out test mods in 10-65 minutes. Every `agent.ps1` call is logged to `reports/agent-log.jsonl`, so long unattended runs can be reviewed afterwards.

Programs driving the kit can add `-Json` to any `agent.ps1` command for the full result object (schemaVersion 1).
