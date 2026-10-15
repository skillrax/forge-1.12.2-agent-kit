# Textured static model proof

Placeable Bunny statue for Minecraft 1.12.2 / Forge 14.23.5.2859 / Java 8. No extra rendering mod required. Install `releases/model-statue/1.0.0/textured-model-statue-1.0.0-srg.jar` in the client and server mods folders. Find **Bunny Model Statue** in Decorations or use `/give @p modelstatue:bunny_statue`. Recipe: lapis block above three stone bricks.

Actual client screenshots before and after resource reload are in `evidence/model-statue/1.0.0/`. Dedicated server checks decode the geometry and all textures, place the block/tile and check facing/render bounds. These checks are isolated from the production JAR. The user reported manual testing all passed and accepted the proof in its current form on 2026-10-13; individual steps and multiplayer coverage were not separately recorded. See `user-acceptance.json` alongside the evidence.

Small manual checklist:

1. Place statues while facing different directions; walk around and inspect textures/head attachment.
2. Place a few together; inspect nearby blocks/items and use F3+T to reload resources.
3. Save, fully close and reopen the world; confirm statues and orientation remain.

## Reproduction and limits

Original downloads stay in ignored `external-testing-assets/models/`. With Blender 4.2.21, run `blender.exe -b --factory-startup --python qa/fixtures/model-statue/tools/convert_model.py -- "external-testing-assets/models/Bunny (The First Descecdant)" .work/model-converted`. Copy generated `models/statue` and `textures/statue` into `src/main/resources/assets/modelstatue/` of an active-kit checkout, alongside this fixture's production source/resources. Apply `build-overlay.gradle` only for isolated evaluation; build/export with the kit commands. The dedicated server and graphical client runners live in `qa/integration/Invoke-StatueServer.ps1` and `Invoke-StatueClient.ps1`.

Conversion imports body, face and head FBX, attaches the head using skeletal socket transforms, maps diffuse materials explicitly, caps textures at 1024 and normalizes height to three blocks. The output has 188542 triangles / 12 material groups. Static renderer shares cached display lists across statues and invalidates them on resource reload; it preserves GL state. Expanded render bounds prevent the tall geometry disappearing when the base leaves the view. Collision covers only the pedestal; inventory icon is the pedestal.

This is a static A-pose, with simplified diffuse lighting. Hair/visor use chosen solid colors; game-specific wet/cornea overlay and PBR effects are omitted. Animation, arbitrary input formats, shader compatibility and large statue counts are not demonstrated. Asset conversion needs format/material inspection and a visual check; a successful build alone cannot establish correct attachment or UV mapping.

Downloaded model credit requested by its readme: **nekopixil**, https://www.deviantart.com/nekopixil. Character: Bunny from The First Descendant. Original downloaded files are preserved unchanged; only converted runtime assets are in the JAR.
