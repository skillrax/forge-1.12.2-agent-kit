# Start here

Extract the ZIP. Create a Codex project using the extracted folder containing Setup.cmd, agent.ps1 and kit.json as its primary folder. A ZIP attached to a chat is not a working project.

Ask: "Read AGENTS.md and START.md, run Setup.cmd as administrator and verify setup. On failure give me the next action."

Choose Y/N in the setup window before downloads. Y sets Windows JAVA_HOME to verified JDK25 and system PATH to JDK25/bin then JDK8/bin first; it aligns an existing current-user JAVA_HOME override and backs up prior values under reports/setup. Changes wait until both JDKs are verified. N leaves persistent settings unchanged; the kit works locally. Installer/UAC prompts may still need responses. After Y restart Codex/terminals and reopen this project.

The second Y/N choice offers recommended Node.js/MCP support. Y reuses compatible Node.js22+/npm or installs Node.js LTS through WinGet after Java verification, installs locked MCP dependencies and checks module loading. MCP gives Codex direct status, validation and diagnostics tools. N skips Node/MCP setup; shell commands still work. Both choices happen before downloads. Restart Codex/terminals after a Node installation; module loading does not prove the app connected.

Alternative: right-click Setup.cmd, Run as administrator, then open this folder in Codex. Windows64-bit/Minecraft1.12.2/Forge14.23.5.2859; setup installs missing JDK25/8, requested Node/MCP support and runs the pinned environment check. First downloads can take minutes; progress/log paths are shown. No global Gradle is required; a Java8 JRE is insufficient.

Agent setup route (visible window needed for prompts):
```powershell
$setupLauncher = (Resolve-Path -LiteralPath './Setup.cmd').Path
Start-Process -FilePath $setupLauncher -ArgumentList '--no-pause' -Verb RunAs -WorkingDirectory (Split-Path -Parent $setupLauncher) -WindowStyle Normal -PassThru
```
A process ID means launched, not completed. --no-pause skips only the final keypress; Y/N and installer prompts remain visible. Confirm launcher exit0 and matching environment PASS before claiming success; inspect this attempt's reports/setup/setup-latest.json and reports/testing-tools on failure. Manual launches keep the final pause. Run agent status in this root; reuse fresh matching checks.

Recovery: ZIP-only/wrong folder → extract/open the folder above and confirm status.projectRoot. Cancelled elevation → rerun the same administrator launcher when ready. Stale JAVA_HOME → setup discovers JDKs independently; restart after Y. Installer/check failure → keep message/reports and repair the stated cause before retrying. Check installer state after timeout/cancellation. Missing WinGet → repair Microsoft App Installer or install x64 JDK25/8 manually. Details: docs/DEPENDENCIES.md.

Process-local terminal route:
```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File ./tools/Setup.ps1 -InstallMissing
powershell -NoProfile -ExecutionPolicy Bypass -File ./agent.ps1 -Action status
powershell -NoProfile -ExecutionPolicy Bypass -File ./agent.ps1 -Action check -Profile environment
```
Bypass applies to that process only. Node/MCP are optional; for noninteractive setup use tools/Setup.ps1 -WithMcp -InstallMissing (Node.js22+/npm, or installs LTS). Give the agent mod behavior and acceptance examples. Validation/export: docs/WORKFLOW.md. Other frontends: docs/INTERFACES.md. Build/server PASS alone is not gameplay proof.
