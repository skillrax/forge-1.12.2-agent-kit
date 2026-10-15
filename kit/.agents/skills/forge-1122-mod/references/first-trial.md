# First feature trial

Use a fresh Codex chat with this kit workspace as the project. Request the forge-1122-mod skill explicitly if discovery is uncertain.

Suggested request:

> Create a Minecraft 1.12.2 mod with modid agenttrial and display name Agent Trial. Add agenttrial:trial_gem named Trial Gem, stack limit 8, creative tab MATERIALS, using the vanilla emerald texture. Add a shapeless recipe with one iron ingot and one emerald yielding one Trial Gem. Use the pinned environment and the forge-1122-mod skill. Record acceptance checks in TASK.md, implement the mod, validate the mod and dedicated server, and report the new run IDs and unverified behavior. Keep validation-only code out of production.

Check the model uses CreativeTabs.MATERIALS, item/generated model parent, minecraft:items/emerald texture, and correct legacy API names. Expected automated evidence: mod PASS with nonzero entry points and server PASS. These alone do not assert crafting, stack behavior, rendering or a production-JAR install. Meaningful runtime assertions can establish server-side behavior; manual client checks establish visuals.

Manual follow-up: run the graphical client, inspect the creative item name/texture, obtain the item, test stack size and craft the recipe. Record observations in TASK.md.

For a repeatability comparison, use fresh copies of the empty toolkit and the same request. Record model/reasoning settings, human interventions, validation run IDs, behavior evidence and final artifact. Do not compare completed code against an empty starting point.
