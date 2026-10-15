@echo off
setlocal
echo Run this file using right-click, Run as administrator.
echo Choose Y/N for Windows Java settings, then for recommended Node.js/MCP support.
echo Setup installs missing JDKs first, then Node.js and MCP dependencies if selected.
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0tools\Setup.ps1" -InstallMissing -PromptMachineEnvironment -PromptNode
if errorlevel 1 goto failed
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0agent.ps1" -Action check -Profile environment
if errorlevel 1 goto failed
echo Setup and pinned environment check passed. Use this folder as your Codex project.
echo Restart Codex and terminals after changing Java settings or installing Node.js.
echo Setup result: reports\setup\setup-latest.json. Check logs: reports\testing-tools.
if /I not "%~1"=="--no-pause" pause
exit /b 0
:failed
echo Setup did not complete. Keep the message above and the reports folder for diagnosis.
echo Resolve the stated cause before retrying. Check installer state after a timeout or cancellation.
echo Setup result: reports\setup\setup-latest.json. Check logs: reports\testing-tools.
if /I not "%~1"=="--no-pause" pause
exit /b 1
