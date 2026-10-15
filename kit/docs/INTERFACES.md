# Frontend-independent interface

The required workflow uses local scripts and files; no OpenAI API, model key or cloud service is called by the kit. A frontend able to execute local commands can run the shell interface. Optional MCP adds stdio tools over the same runner: project_status, validate and diagnostics. Other compatible MCP clients can launch tools/Start-Mcp.ps1 from the extracted workspace. Actual editor/extension/local-model integrations have not been tested.

Shell contract (Windows PowerShell):

```powershell
powershell -NoProfile -File ./agent.ps1 -Action status
powershell -NoProfile -File ./agent.ps1 -Action check -Profile mod
powershell -NoProfile -File ./agent.ps1 -Action errors -RunId <new-run-id>
```

stdout is one UTF-8 JSON result with schemaVersion1. Check requires process exit0 and statusPASS; failed checks include bounded diagnostics. errors retrieves causes and does not prove passed validation. Confirm projectRoot and source/toolchain/artifact freshness before reuse. Status supplies an evidence-based nextAction. Failed setup/checks provide nonzero exits and actionable messages. Full logs stay local. Use shell -Clean or MCP clean: true for recovery; normal builds reuse unchanged work with automatic clean on removed paths or changed build configuration. MCP server identity comes from kit.json. The source helper returns a separate bounded JSON result; it is not an extra MCP tool.

Read START.md plus current TASK/status first, then only the relevant skill reference. Do not auto-load all docs or example fixtures. User instructions and model settings remain under user control. Local-model capability is a separate experiment: supporting the interface does not prove that a particular model can implement/debug the mod.

Optional installed-server behavior tests use separate shell tools: New-InstalledScenario.ps1, Prepare-InstalledServer.ps1, Build-InstalledTestDriver.ps1 and Test-InstalledMod.ps1. See [installed testing](INSTALLED-TESTING.md). Project scenarios are indexed automatically; compact results identify scenario/phase/ticks and bounded failures. They load exact production JARs in an isolated installed Forge server and do not alter agent status/MCP capabilities. Client/graphical testing is separate.
