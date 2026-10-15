# Separate-PC beta trial

Use the supplied ZIP and checksum on another Windows PC. Extract into a new folder containing spaces and open that extracted root as the Codex project. Do not copy developer reports, run folders or caches. Record whether caches were empty; a warm-cache trial remains useful but is not a cold-machine claim.

1. Verify the ZIP SHA256. Run START.md's administrator Setup.cmd route. Choose Y/N separately for Windows Java settings and recommended Node/MCP support; setup installs missing prerequisites. Restart Codex/terminals after persistent Java changes or Node installation. Keep setup-latest.json and failure logs. A launched process is not completed setup.
2. Confirm agent status.projectRoot and matching environment PASS. If MCP was selected, check that Codex can list/call project_status for this root; module-load PASS alone does not prove app connection. Shell-only use remains supported.
3. Give Codex one small behavioral prompt and acceptance examples. Build with the mod profile and check the requested behavior. Record any repairs or user interventions; no default fault, stress or autonomy campaign is required.
4. Export the validated versioned SRG JAR with tools/Export-Release.ps1. Install that exact hash into a matching Minecraft1.12.2/Forge14.23.5.2859 client and observe the requested gameplay. Add save/reload or server checks only where the behavior needs them. Build/server readiness is not gameplay proof.

Return the setup/build/export identities, short acceptance outcome, relevant logs/screenshots and unresolved problems. Stop unchanged failing retries and repair the stated cause. Public publication is a separate decision.
