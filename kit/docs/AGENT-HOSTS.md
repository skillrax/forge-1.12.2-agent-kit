# Choose an agent host

The kit runs local Windows scripts. It does not call an AI provider, select a model, store model credentials or supply an inference server. Use Claude Code, Codex, a local-model agent or another host that can read/write files and execute PowerShell in the intended workspace. A chat model alone needs a tool-capable host or a person to run commands. Capability varies by model; this interface does not establish equal results across models.

## Shared instructions

Open the extracted kit root containing agent.ps1 and Setup.cmd. Ask the host to read AGENTS.md, START.md and TASK.md explicitly. The Forge guide is ordinary Markdown at .agents/skills/forge-1122-mod/SKILL.md; read it for mod work even if the host has no skill discovery. Load only the relevant linked reference. .agents is shared content, not a provider runtime requirement.

CLAUDE.md is a short pointer to the same instructions for hosts that use that filename. Other hosts can use the explicit opening prompt above. Keep one source of workflow rules in AGENTS.md; do not duplicate them into separate provider guides. At the maintainer root, use the root AGENTS.md/TASK.md instead of recipient setup.

## Shell route (works without MCP)

Run these in the kit root after setup:

```powershell
powershell -NoProfile -File ./agent.ps1 -Action status
powershell -NoProfile -File ./agent.ps1 -Action check -Profile mod
```

Inspect JSON projectRoot before using the result. Exit 0 and status PASS prove the reported scope only. Select the model in your host. Smaller models can work on bounded edits with one reference and compiler diagnostics; use a more capable model or human review when the task exceeds their reliable behavior. No model-tier routing is built into the kit.

## Optional MCP route

Run tools/Setup.ps1 -WithMcp first. mcp/client.example.json describes the stdio launch command, not a universal client configuration file. Translate it into your host's supported MCP configuration and set the process working directory to the absolute kit root. If relative arguments are unsuitable, replace ./tools/Start-Mcp.ps1 with that script's absolute path; the working directory must still match the kit root. Do not copy model settings or credentials into the kit.

The optional integrations/codex/config.toml sample can be copied into .codex/config.toml for a host that accepts that format. Core setup never reads it. Configure other clients through their own supported settings; no client CLI syntax is prescribed here.

Check the connected server lists project_status, validate and diagnostics, then call project_status and confirm the exact projectRoot. A successful dependency/module check alone does not establish connection. Without MCP, use the shell commands above.

## Evidence limits

The shell and MCP contracts can be tested independently of a provider. Actual Claude Code, Codex application, local-model host and specific light/heavy model acceptance must be recorded separately. Cold-PC provisioning and graphical gameplay checks are separate too.