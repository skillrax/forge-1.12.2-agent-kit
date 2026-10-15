# Rotor + Crystal proof

Minecraft 1.12.2 / Forge 14.23.5.2859 / Java 8. Install `releases/animation-effects/1.0.1/animation-effects-proof-1.0.1-srg.jar` in client and server mods folders. No extra mod or downloaded art required. Find **Rotor + Crystal (Right-click On/Off)** in Decorations, or use `/give @p animationproof:rotor_crystal`. The inventory icon is a stone pedestal. Right-click the pedestal, rather than the decorative parts above it. It starts ON.

## Short manual checklist

1. Place one fixture. Watch the gold rotor rotate and the cyan crystal hover/turn with orbiting sparkles. Use All or Decreased particles.
2. Right-click the pedestal. Confirm OFF appears, motion freezes and sparkles disappear within about 1.2 seconds. Right-click again: ON resumes motion and sparkles.
3. Switch it OFF, save/close and reopen the world. Confirm it stays OFF; toggle ON again. If using a server, another player should see the same on/off state.

The user reported steps 1–3 all passed with expected results on 2026-10-14. This includes observed motion/effects, on/off controls and normal save/reopen. Installed hashes and the conditional second-player observation were not separately detailed. Acceptance: `evidence/animation-effects/1.0.1/user-acceptance.json`.

## Evidence and mechanism

`evidence/animation-effects/1.0.1/summary.json` records the focused build, dedicated-side checks and actual graphical captures. The client harness used two normal `PlayerControllerMP.processRightClickBlock` calls and their server packets, verified synchronized OFF/ON state and a frozen clock, then exited cleanly. Captures at ticks 80/87 show different rotor orientations and crystal heights with sparkles; ticks 150/180 show unchanged paused geometry with no sparkles; tick 220 shows resumed effects. These are captured frames, not a frame-rate measurement. The user accepted all three manual steps; the separate acceptance record preserves that report.

The server stores activity and clock anchor/elapsed time in the tile NBT, marks changes dirty and sends normal tile updates. Clients render `elapsed + worldTime - start + partialTicks` while active, and the frozen elapsed value while paused. Original procedural cuboids and an octahedron demonstrate independent part transforms. LWJGL 2 requires a 16-float buffer for `glGetFloat`, even when reading four color components. Texture, lighting, culling, current color and matrices are restored in `finally` scopes; depth/blend state is not changed. Expanded tile render bounds include the crystal.

Sparkles emit only on client ticks, one every four ticks per active fixture within 24 blocks, and live at most 24 ticks (at most six live per fixture at normal ticking). Particle updates use bounded orbit math and vanilla interpolation; OFF stops emission and lets residual particles expire. Minimal particles disables emission. Client imports/registration stay in the Side.CLIENT subscriber and its renderer/particle classes. No QA classes are included in the production JAR.

Decorations have pedestal-only collision/selection; this is not a tall collision or animated inventory model proof. No RF, recipes, resource caches, shaders, skeletal animation, third-party rendering compatibility, arbitrary large-count performance or multiplayer gameplay claim. No cosmetic refinement campaign is required.

## Reproduce

Create a fresh checkout with `kit/tools/New-Checkout.ps1`, run its setup, copy this fixture's `src/*` into its `src/`, and copy `build-overlay.gradle` beside its build file. Append `apply from: 'build-overlay.gradle'` to the checkout build file. Run `agent.ps1 -Action check -Profile mod`, then `qa/integration/Invoke-AnimationServer.ps1 -Workspace <checkout>` and `Invoke-AnimationClient.ps1` with the same argument. The overlay only adds isolated evaluation output to run tasks. Export with the checkout's `tools/Export-Release.ps1`. Never build into that checkout while its client is running.
