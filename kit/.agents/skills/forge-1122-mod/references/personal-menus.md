# Key-opened menus and saved player state

For Forge 1.12.2 / stable39. The accepted Personal Menu proof demonstrates a standalone screen, buttons/hover tooltip and independent saved player values through logout and normal server/world restart. Use the existing project's networking and side separation where available; no new block, item or inventory container is needed for a personal settings screen.

## Client input and screen

Register a configurable KeyBinding through ClientRegistry on the physical client. Keep Minecraft, GuiScreen, KeyBinding and LWJGL imports in a client proxy or client-only subscriber; common packet classes must not import them. A sided proxy can deliver a received snapshot to the client without loading GUI classes on a dedicated server.

```java
KeyBinding open = new KeyBinding("key.example.open",
    KeyConflictContext.IN_GAME, Keyboard.KEY_N, "key.categories.example");
ClientRegistry.registerKeyBinding(open);
// ClientTickEvent, Phase.END; consume presses even while another screen is open.
boolean pressed = false;
while (open.isPressed()) pressed = true;
Minecraft mc = Minecraft.getMinecraft();
if (pressed && mc.player != null && mc.world != null && mc.currentScreen == null)
    mc.displayGuiScreen(new PersonalScreen());
```

Provide the translation keys under assets/<modid>/lang/en_us.lang. Respect ordinary key conflicts and let the player rebind in Controls. Never open over chat or another screen. For a nonpausing GuiScreen, override doesGuiPauseGame() to return false; Esc uses normal GuiScreen closure. Add GuiButtons in initGui, handle actionPerformed, and call drawHoveringText only over the intended hover region. Reopening/resizing should request fresh state. Show a waiting status and disable mutation buttons until a server reply arrives. No container is required unless the feature actually transfers inventory slots.

## Server-owned actions

Use SimpleNetworkWrapper with a small validated action code. Requests identify an action, not an arbitrary player UUID/name or the client's proposed replacement values. In the server IMessageHandler, obtain `context.getServerHandler().player`, capture the action, and schedule work with `player.getServerWorld().addScheduledTask(...)`. Validate the action and bounds before mutation. Inside the scheduled task, discard it if the server's current PlayerList entry for that UUID is no longer the same player instance.

Apply the action only to the authenticated sender and send a snapshot back with `network.sendTo(snapshot, player)`. Do not broadcast personal state. The client handler schedules GUI updates through Minecraft.addScheduledTask; update the current screen only if it is still the appropriate screen. Keep counters bounded, reject unknown actions without mutation, and make reset affect only this feature. Settings that consume resources or grant benefits need their own server permission/accounting rules; the proof's free counter/toggle is demonstration policy.

## Player storage and replacement

For small saved values, use a namespaced compound under `player.getEntityData()` / `EntityPlayer.PERSISTED_NBT_TAG`. When the section is absent, getCompoundTag returns an unattached compound: attach both your feature compound and the persisted section after changing them.

```java
NBTTagCompound root = player.getEntityData();
NBTTagCompound persisted = root.getCompoundTag(EntityPlayer.PERSISTED_NBT_TAG);
NBTTagCompound state = persisted.getCompoundTag("ExamplePersonalState");
state.setInteger("Counter", count);
state.setBoolean("Enabled", enabled);
persisted.setTag("ExamplePersonalState", state);
root.setTag(EntityPlayer.PERSISTED_NBT_TAG, persisted);
```

Read defaults and clamp malformed/out-of-range values. Preserve unrelated feature data. Ownership follows the server player UUID; changing account/offline-mode identity changes ownership. Normal player autosave/logout/shutdown persists the section; the integrated owner also uses vanilla level player data. Do not flush disk on every click or claim forced-kill recovery. Pinned EntityPlayerMP.copyFrom already transfers PERSISTED_NBT_TAG on replacement/respawn, so this pattern needs no duplicate clone handler. Capabilities or other storage need an explicit serialization/clone policy of their own.

## Enough verification

Use one focused action/isolation check and a short client check for opening, buttons and tooltip. Confirm two real players keep independent values, then logout/relogin and normally stop/restart the same world. Synthetic UUID assertions support isolation but are not real multiplayer acceptance; an NBT roundtrip is not restart proof. Fix the development username and compare UUIDs across process launches, since default dev names can vary. Keep QA outside the production JAR, and run builds sequentially rather than replacing classes under a running client. Once representative behavior is accepted, reuse the pattern without another cosmetic or lifecycle campaign.
