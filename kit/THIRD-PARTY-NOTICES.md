# License and third-party notices

The kit's own files (scripts, docs, harness, generators, templates and examples) are under the MIT License: [LICENSE](LICENSE). Mods you build with the kit are yours; code the generators write into them may be used without attribution.

## Third-party components

The included Gradle wrapper JAR and launch scripts are Gradle components under Apache License 2.0. Copyright headers in the scripts are preserved; the accompanying license and Gradle notice are under licenses/. Those files are copied from the local pinned Gradle9.6.0 distribution without modification. No Gradle distribution, Minecraft/Forge source/runtime, JDK or cache is bundled in this ZIP.

Build dependencies are resolved from their pinned metadata and retain their upstream licenses. See docs/DEPENDENCIES.md. Minecraft and Forge are not bundled: the first build downloads them under their own terms (Minecraft EULA, Forge license), and mods you share must follow those terms.

The static-model starter's checker texture and pyramid mesh are original procedural sample assets, reproducible with the included Python sample generator. Downloaded model bundles, their converted assets and maintainer QA are excluded from the kit. Blender is an optional external authoring tool and is not bundled.

The RF network starter contains original Java and model JSON using references to vanilla textures supplied by the user's Minecraft installation; no Minecraft texture/source/runtime bytes or CoFH/Ender IO/Thermal code are bundled. Its Forge APIs use the existing pinned build dependency. Maintainer assertion mods and proof evidence are excluded.
