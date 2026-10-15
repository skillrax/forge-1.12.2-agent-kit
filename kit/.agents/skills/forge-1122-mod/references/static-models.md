# Textured static models

Use vanilla block/item JSON for ordinary cuboids. For a complex static polygon model, the optional [starter](../assets/static-model-starter/src/main/java/com/example/staticmodel/ModelStatue.java) provides a pedestal block, facing state, tile and client-only renderer. Copy `assets/static-model-starter/src/main` into an empty mod checkout, or adapt the relevant classes to the existing mod. Keep package, mod ID, registry IDs, mesh/texture paths and metadata consistent. The original checker pyramid is six triangles; no downloaded model is included. The starter is not installed automatically.

## Intake and adapters

Inspect the supplied bundle before choosing tools: file signatures/extensions, companion textures, source axes/units, mesh parts, UV layers, material slots, armatures/pose and usage permissions/credit. Preserve original files; author generated scenes and exports in a new directory. Use a bounded file list first. An extension or successful import does not prove correct assembly.

Keep three layers separate:

- A source-format adapter imports the inspected format using a compatible installed tool/plugin. Choose it per asset; this kit does not import arbitrary formats. FBX, OBJ, glTF, PSK and other bundles can need different tooling. Unsupported imports require a concrete adapter or an author-supplied interchange export.
- A model configuration/assembly step names retained objects, material slots, textures, omitted overlays and any attachment/pose transforms. Names and sockets belong to that asset, never the runtime contract. Bake/inspect attachments in a prepared scene; do not assume matching rigs or automatic socket alignment.
- A normalized exporter produces static triangles in ST3D. The Java renderer reads those triangles and texture resource IDs without knowing the original format, object names or skeleton.

The maintainer's accepted complex-model proof used an asset-specific FBX/socket mapping. That demonstrates one conversion, not a universal importer. Reuse the runtime pattern, then inspect and configure each new source independently.

## Prepared Blender scene export

The optional [Blender exporter](../scripts/static-model/export_blender.py) targets the Blender 4.2 API. Blender is an authoring dependency, not bundled or required by the mod. Start with an assembled `.blend` scene produced by the chosen import adapter. It must have active UV layers; set the desired static pose and evaluated modifiers deliberately. Center its intended footprint around X/Y zero, use Blender Z up, and orient its front toward Blender -Y. Export maps `(x,y,z)` to Minecraft `(x,z,-y)`, moves the retained lowest Z to zero and uniformly scales to `heightBlocks`. Normals use inverse-transpose object transforms; mirrored transforms reverse triangle winding. UV V is flipped once. Check the resulting orientation/texture against the actual source.

From the kit root (replace paths with the inspected assets):

```powershell
$scripts = '.agents/skills/forge-1122-mod/scripts/static-model'
& 'C:/path/to/blender.exe' -b 'C:/models/prepared.blend' --python-exit-code 1 --python "$scripts/export_blender.py" -- inspect
& 'C:/path/to/blender.exe' -b 'C:/models/prepared.blend' --python-exit-code 1 --python "$scripts/export_blender.py" -- export 'C:/models/scene.json' 'C:/models/new-export'
```

Copy/adapt [scene.example.json](../assets/static-model-starter/scene.example.json). `objects[].name` selects meshes; `materials` maps every retained triangle's numeric slot to `{texture, cutout}` or explicit `null` to omit it. The example's `Pyramid` and checker path are sample configuration. Inspection prints object/material/UV/modifier information without changing the scene. Export fails on missing objects, UVs, unmapped slots, degenerate height or invalid mesh data. It creates `sample.mesh` and `report.json` in a new output directory, leaving inputs intact. Config and scene hashes, bounds, group counts and omissions are recorded. Keep this report outside production resources.

Textures are supplied separately: convert selected diffuse/base-color images to PNG, review color space, UVs and alpha, and copy them to the configured resource paths. Do not silently treat a normal/roughness/packed shader map as diffuse. This exporter does not extract textures, run foreign import plugins or interpret PBR nodes. The renderer supports opaque and alpha-test cutout groups, not translucent sorting or PBR. Make approximations/omissions explicit. Preserve aspect ratio if resizing; choose texture and triangle budgets for the actual use.

For the starter, copy `sample.mesh` to `src/main/resources/assets/staticmodel/models/statue/` and its PNGs under `assets/staticmodel/textures/`. Adapt `StatueRenderer.MODEL` for a different mesh filename. Use the report bounds to adjust `StatueTile.getRenderBoundingBox()` for every horizontal facing plus the renderer's +0.15 pedestal offset. Collision and inventory appearance are only the pedestal; change them if requested. Geometry does not create multiblock collision automatically.

## Runtime contract and checks

[MeshData.java](../assets/static-model-starter/src/main/java/com/example/staticmodel/MeshData.java) is a plain bounded reader with no client classes. [st3d.py](../scripts/static-model/st3d.py) writes the same contract: big-endian `ST3D`, int32 version 1, int32 group count (1..32); for each group, uint16 ASCII texture-ID byte length and bytes, boolean cutout, int32 vertex count, then eight float32 values per vertex `(x,y,z,u,v,nx,ny,nz)`. Counts are multiples of three with at most 1,500,000 vertices total. ASCII is compatible with Java `readUTF` here; arbitrary Unicode strings are outside the contract. Texture IDs must match `[a-z0-9_]+:textures/[a-z0-9_/.-]+.png` with no `..`. Data must be finite with no trailing bytes. Resource existence and PNG validity need separate checks.

[StatueRenderer.java](../assets/static-model-starter/src/main/java/com/example/staticmodel/StatueRenderer.java) shares compiled display lists across placed instances and deletes them on resource reload. Keep renderer imports/registration in the client-only [ClientHooks](../assets/static-model-starter/src/main/java/com/example/staticmodel/ClientHooks.java). Preserve GL state around drawing and avoid mixing raw GL changes with stale `GlStateManager` caches. Caching reduces repeated uploads; it does not establish performance for large statue counts or shader compatibility.

Run a focused mod build and dedicated-server side-loading check for changed runtime integration. Read the exported mesh, check referenced PNGs and bounds before launching the client. Then inspect placement/facing, attachments, UVs, nearby block/item rendering and one resource reload in the graphical client. Check save/reopen when persistence changes. Build/server readiness cannot prove the picture. This static proof does not establish animation, PBR, shader compatibility or arbitrary-format support. For independently moving parts and bounded particles, use the separate accepted mechanism in [animation-and-particles.md](animation-and-particles.md); imported clips/skeletal animation still need their own adapter and evidence.

The [sample generator](../scripts/static-model/make_sample.py) uses Python's standard library to recreate the original sample in a **new** assets root: `python .agents/skills/forge-1122-mod/scripts/static-model/make_sample.py C:/models/new-sample`. Its `models/` and `textures/` folders belong under `assets/staticmodel/`; the generator and authoring reports do not belong in a production JAR.
