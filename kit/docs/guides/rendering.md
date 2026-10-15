# Rendering: animated parts, particles, complex static models

Ordinary blocks and items use JSON models. Code rendering is client-only: everything below lives in client classes registered from ClientEvents.

## Animated tile parts (TESR)

- State (on/off, clock) lives in the tile on the server: change it in `onBlockActivated` (server), `markDirty()`, `world.notifyBlockUpdate(pos, state, state, 3)`; include it in `getUpdateTag`/`getUpdatePacket`. Send state changes, never per-frame values.
- Bind the renderer in `ClientEvents.models`: `ClientRegistry.bindTileEntitySpecialRenderer(MyTile.class, new MyRenderer())` (`MyRenderer extends TileEntitySpecialRenderer<MyTile>`).
- Clock that pauses and resumes smoothly: store `elapsedTicks` and `startTick` (`world.getTotalWorldTime()`), then

```java
double time = elapsedTicks + (active ? Math.max(0, world.getTotalWorldTime() - startTick) + partialTicks : 0);
```

  On toggle (server) add the active ticks to `elapsedTicks`, reset `startTick`, flip, sync. Rotation `(time * degreesPerTick) % 360`; hover `baseY + Math.sin(time * frequency) * amplitude`. Never increment angles inside `render` (frame-rate dependent).
- Per part: `GlStateManager.pushMatrix()`, translate to pivot, rotate, draw, `popMatrix()` in `finally`. Expand `getRenderBoundingBox()` to cover all moving extents.
- GL state: change and restore through `GlStateManager` (texture, lighting, cull, blend, color) in `finally`. Drawing color arrays invalidates the cached color: save it first. With the pinned LWJGL 2, `GL11.glGetFloat(GL11.GL_CURRENT_COLOR, buf)` needs a **16-float** direct buffer (`BufferUtils.createFloatBuffer(16)`); a 4-float buffer crashes.

## Particles

Spawn from `TickEvent.ClientTickEvent` (phase END), never from `render`; skip when paused, respect particle settings, only near a loaded active tile. Bound the rate (example: one every 4 ticks within 24 blocks, 24-tick life). A custom `Particle` updates `prevPosX/Y/Z` before moving and dies at `particleMaxAge`.

## Complex static models (OBJ/FBX/glTF art)

`examples/static-model` renders triangle meshes (ST3D format) with a client-only renderer, pedestal block, facing and shared display lists (rebuilt on resource reload).

- Inspect the asset first (format, textures, axes, parts, licensing). Keep originals untouched; export into a new folder.
- Blender route (`examples/static-model/scripts/export_blender.py`, Blender 4.2): prepare an assembled `.blend` (UVs, pose, front facing -Y, Z up), configure `scene.example.json` (objects, material slot -> texture/cutout), then
  `blender -b prepared.blend --python-exit-code 1 --python examples/static-model/scripts/export_blender.py -- export scene.json out-dir`
  (`-- inspect` first). Output: `sample.mesh` + `report.json`. Convert diffuse textures to PNG yourself; normal/roughness maps are not diffuse.
- Put `sample.mesh` under `assets/<modid>/models/statue/` and PNGs under `textures/`; adjust the renderer's model path and `getRenderBoundingBox` from the report bounds. Opaque and cutout only; no translucency or PBR.
- `scripts/make_sample.py <new-dir>` regenerates the sample pyramid (Python stdlib only).

## Test

Server tests cover the saved state and sync fields. Motion, particles and models must be looked at in the client (`gradlew.bat runClient`); say exactly what to look for.
