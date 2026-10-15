# Agent apps and MCP

The kit is plain files plus PowerShell; it calls no AI service and stores no keys. Any agent that can read/write files and run PowerShell in this folder works.

| Host | Setup |
|---|---|
| Claude Code | open this folder; CLAUDE.md imports AGENTS.md automatically |
| Codex and other AGENTS.md-aware agents | open this folder; AGENTS.md is read automatically. `integrations/codex/config.toml` is an optional MCP sample (copy to `.codex/config.toml`) |
| Other agents / local models | make AGENTS.md the system prompt (or first message) and give the agent a PowerShell tool rooted here |

Small-context models (32k): AGENTS.md (~600 tokens) + one guide (~1-1.5k) + compact check output fits easily. Prefer generators over hand-written code, run `check` after every edit, and use `api` lookups instead of reading source files.

## Optional MCP server

`tools/Setup.ps1 -WithMcp` installs it (Node 22+). Launch command for your client (stdio), with the working directory set to this folder's absolute path:

```
powershell -NoProfile -ExecutionPolicy Bypass -File ./tools/Start-Mcp.ps1
```

`mcp/client.example.json` shows the shape. Tools: `project_status`, `validate` (profile build/server/game/unit/environment, optional `test` filter), `diagnostics` (re-read a run). Text results match the shell output. After connecting, call `project_status` and confirm `projectRoot`. Generators and `api` are shell-only.

Shell results: plain text by default; `-Json` on any `agent.ps1` command gives the full result object (schemaVersion 1) for programs.
