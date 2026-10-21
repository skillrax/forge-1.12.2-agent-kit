# Compatibility with other mods (JEI, Baubles, ...)

Optional ("soft") dependencies: your mod works without the other mod and adds features when it is present.

## Build

Add the other mod as compile-only, from a repository limited to its group, below the existing entries in build.gradle (the only build edit needed; keep the rest pinned):

```groovy
repositories {
    maven { url = 'https://maven.blamejared.com'; content { includeGroup 'mezz.jei' } }       // JEI
    maven { url = 'https://cursemaven.com'; content { includeGroup 'curse.maven' } }           // any CurseForge file
}
dependencies {
    compileOnly 'mezz.jei:jei_1.12.2:4.16.1.1013:api'
    compileOnly 'curse.maven:baubles-227083:2518667'    // curse.maven:<slug>-<project id>:<file id>
}
```

Pin exact versions. Prefer a mod's published `api` artifact. A CurseForge jar is the release build: call only the other mod's own classes, not Minecraft members through them (those carry obfuscated names).

## Code

- Declare it: `@Mod(..., dependencies = "after:jei")` (load order only, never `required-after` for optional mods).
- Guard every use with `Loader.isModLoaded("baubles")` and keep the classes that import the other mod separate: your common classes must load without it. An interface from the other mod on your own item: `@Optional.Interface(iface = "baubles.api.IBauble", modid = "baubles")`, methods `@Optional.Method(modid = "baubles")`.
- JEI finds plugins itself (`@JEIPlugin` on a class implementing `IModPlugin`); nothing else to register.
- Screens: JEI places its overlay from the event `drawDefaultBackground()` fires (guides/gui.md screen; `check` warns when a screen never calls it).

## Test

The other mod is not in the dev runs (`check game`, `check client`, `runClient`): check without it that nothing breaks (`check server` proves your mod loads alone). With it, either test the exported jar together with the exact dependency jars on an installed server (docs/INSTALLED-TESTING.md), or tell the user what to try in a real instance and which jar versions you compiled against.
