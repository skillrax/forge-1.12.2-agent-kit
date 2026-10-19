# Setup (once per PC)

Needs Windows 10/11 x64. The kit uses JDK 25 to run Gradle and JDK 8 to compile mods. No global Gradle needed; the first build downloads and decompiles Minecraft/Forge once (10-30 minutes; progress lines show the current step).

## Easiest: Setup.cmd

Right-click `Setup.cmd` > Run as administrator. It asks one Y/N question before downloading anything. **Java settings**: Y sets Windows JAVA_HOME to JDK 25 and puts JDK 25 then JDK 8 first on PATH (old values are backed up under reports/setup). N changes nothing permanent; the kit still finds the JDKs itself.

It installs missing JDKs with WinGet and runs the environment check. After answering Y, restart your agent app and terminals.

An agent can start it in a visible window (the user still answers the prompts):

```powershell
$launcher = (Resolve-Path ./Setup.cmd).Path
Start-Process -FilePath $launcher -ArgumentList '--no-pause' -Verb RunAs -WorkingDirectory (Split-Path $launcher) -WindowStyle Normal -Wait
```

A started process is not a finished setup. Afterwards `./agent.ps1 status` must show `java: ok` and `./agent.ps1 check environment` must PASS.

## Without admin rights or prompts

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File ./tools/Setup.ps1 -InstallMissing
powershell -NoProfile -ExecutionPolicy Bypass -File ./agent.ps1 check environment
```

Custom JDK locations: `tools/Setup.ps1 -GradleJavaHome <jdk17+> -Java8Home <jdk8>`.

## Problems

| Symptom | Fix |
|---|---|
| Agent sees a ZIP, not files | Extract it; open the folder containing Setup.cmd and agent.ps1 as the workspace |
| UAC cancelled | Run Setup.cmd as administrator again |
| WinGet missing | Install "App Installer" from the Microsoft Store, or install Temurin JDK 25 and JDK 8 (x64) by hand |
| `Network download failed` (first build) | Gradle, Minecraft and Forge files (~1 GB) come from services.gradle.org, Mojang and maven.minecraftforge.net; the check already retried once. Rerun `./agent.ps1 check environment` (finished downloads are kept). Still failing: open https://maven.minecraftforge.net/ in a browser; try without VPN/proxy or later |
| Installer failed or timed out | Read reports/setup/setup-latest.json and the log it names; fix that cause, then rerun |
| Wrong JAVA_HOME | The kit finds JDKs on its own; after choosing Y, restart the terminal/agent app |
| `Unable to access jarfile`, odd file-not-found errors | The folder path is too long (Windows 260-character limit). Move the kit to a short path such as `C:\mods\mykit` |
| Scripts blocked | Use `powershell -NoProfile -ExecutionPolicy Bypass -File ...` (affects that process only) |

More detail (JDK discovery rules, logs): docs/DEPENDENCIES.md. Agent apps: docs/HOSTS.md.

## Removing the kit

1. Delete the kit folder (your mods live in it: copy `src/` and `releases/` out first).
2. Gradle's cache, about 1-2 GB including Minecraft and Forge: `%USERPROFILE%\.gradle` (or `GRADLE_USER_HOME` if you set it). Other Gradle projects use it too.
3. If you answered Y in Setup.cmd: your previous system PATH and JAVA_HOME are saved in `reports/setup/environment-before-*.json`; restore them under Windows Settings > System > About > Advanced system settings > Environment Variables.
4. The JDKs Setup installed: Windows Settings > Apps, "Eclipse Temurin JDK 25" and "Eclipse Temurin JDK 8" (or `winget uninstall --id EclipseAdoptium.Temurin.25.JDK` and `.8.JDK`).
