# Moving model parts and bounded particles

Use a tile renderer for independently rotating, translating or hovering block-model parts. Ordinary block/item JSON remains useful for the base and inventory icon. The accepted Rotor + Crystal proof demonstrates procedural cuboids, an octahedron, orbit sparkles and a saved server-owned on/off control. It does not establish skeletal animation, imported animation clips, shaders or large-instance performance. No extra art or animation framework is required for these mechanisms.

## Server-owned state, client-only presentation

Keep the block, tile, saved activity and animation clock free of client imports. Handle `onBlockActivated` on the server; mutate once, call `markDirty()` and `world.notifyBlockUpdate(pos, state, state, 3)`. Return handled on both sides. An action-bar message and an explicit creative name make the control easy to find.

Write clock/activity fields in `writeToNBT`, restore them in `readFromNBT`, and include them in `getUpdateTag` for initial chunk delivery and `getUpdatePacket`/`onDataPacket` for later changes. New tiles need an explicit initial clock anchor. Send state changes, not per-frame rotations or particles. Give absent fields sensible defaults and bound malformed values. Clients observe the state; they never decide the saved switch.

Put model registration, `ClientRegistry.bindTileEntitySpecialRenderer`, `TileEntitySpecialRenderer` and `Particle` code behind a `@Mod.EventBusSubscriber(modid=..., value=Side.CLIENT)` entry point. Common code must not instantiate/reference those classes. Compilation against client APIs is not a dedicated-server side-loading check.

## Smooth clock and part transforms

For a clock that freezes OFF and resumes ON, store `elapsedTicks` and `startTick` against `world.getTotalWorldTime()`. Active presentation time is:

```java
double time = elapsedTicks + (active
    ? Math.max(0, world.getTotalWorldTime() - startTick) + partialTicks
    : 0);
```

On the server toggle, accumulate the active whole ticks into `elapsedTicks`, set `startTick` to the current total time, then flip activity and synchronize. OFF uses only the saved elapsed value. Persist both fields; avoid using the daylight clock, which changes with time-setting commands. This policy catches up while an active tile is unloaded. If gameplay requires animation to advance only while loaded, use a ticked clock instead and define its save/sync policy.

Compute each part from this time in `render`: rotation `(time * degreesPerTick) % 360`, hover `baseY + sin(time * frequency) * amplitude`. Apply partial ticks before phase math; do not increment angles in the renderer, where frame rate would change motion speed. Keep the modulo in double precision until the final rotation cast.

For each part, push a matrix, translate to its pivot, rotate/translate, draw, and pop in `finally`. Give the whole renderer its own matrix scope. Expand `getRenderBoundingBox()` to include all extrema of rotation, hover and offsets; collision/selection boxes and item appearance are separate choices. A visible mesh does not automatically create collision above the base.

## GL state and the pinned LWJGL pitfall

Use `GlStateManager` for changes and restoration so its cache matches GL. Snapshot/restore the states actually changed; a matrix push only saves transforms. The proof temporarily disables texture, lighting and culling for opaque `POSITION_COLOR` geometry, then restores their prior values and current color in `finally`. It leaves depth and blending alone. If the requested effect needs blending, depth-write changes, texture binding or lightmap changes, scope those too.

Vanilla/Forge color-array upload invalidates the cached current color. Snapshot it before drawing and restore through `GlStateManager.color(...)`. With the pinned LWJGL 2, `GL11.glGetFloat(GL11.GL_CURRENT_COLOR, buffer)` requires a **16-float direct buffer**, even though this query returns four components; a four-float buffer crashes at runtime. Reuse a `BufferUtils.createFloatBuffer(16)`, clear it before reading, and restore indices 0..3. Avoid raw GL restoration that leaves stale manager caches.

## Bounded effects

Emit from client ticks, never from `render`. Run once at the selected tick phase, skip paused games, respect particle settings and require a nearby active loaded tile. Do not probe remote chunks just to create decoration. In the proof, one sparkle every four ticks within 24 blocks and a 24-tick lifetime limits each fixture to six live sparkles at normal ticking. Many fixtures still multiply that cost; add a global emission budget when the requested use needs one.

A custom `Particle` can hold its orbit center and starting phase, update `prevPosX/Y/Z` before moving, and calculate a small bounded orbit from its age. Expire at `particleMaxAge`; use age-based scale/fade rather than accumulating unbounded motion. Vanilla particle rendering interpolates the previous/current positions. Turning OFF stops emission; residual particles may finish their short lifetime. This visual cleanup does not require a server particle packet or saved particle objects.

## Enough verification

Run a focused build and dedicated-side check for new side-sensitive integration. Make one short graphical observation of changed part positions, visible effects and normal nearby world/UI rendering; exercise OFF/ON and verify residual effects clear. Check normal save/reopen for the saved control. Keep QA outside the production JAR and stop the client before building into its checkout. Resource reload needs a focused check when the implementation introduces reload-sensitive caches/resources; procedural geometry alone does not require a new reload campaign. Accepted mechanism reuse does not require cosmetic variants, a lifecycle matrix or a stress campaign unless an affected behavior or concrete risk warrants it.
