# Player data, commands, keybinds

## Saved per-player values

Small values: a compound under the player's persisted NBT. It survives logout, restart and respawn (EntityPlayerMP.copyFrom copies it; no clone handler needed).

```java
static NBTTagCompound data(EntityPlayer player) {                        // read
    return player.getEntityData().getCompoundTag(EntityPlayer.PERSISTED_NBT_TAG).getCompoundTag("MyModData");
}
static void save(EntityPlayer player, NBTTagCompound state) {             // write: attach both levels
    NBTTagCompound root = player.getEntityData();
    NBTTagCompound persisted = root.getCompoundTag(EntityPlayer.PERSISTED_NBT_TAG);
    persisted.setTag("MyModData", state);
    root.setTag(EntityPlayer.PERSISTED_NBT_TAG, persisted);
}
```

`getCompoundTag` returns a new detached compound when missing, so always write back as above. Server side only; clamp values read from NBT; keep data namespaced. Ownership follows the player UUID. For data the client needs (HUD), send a packet (guides/networking.md). Larger structured data: a capability attached in `AttachCapabilitiesEvent<Entity>` with `PlayerEvent.Clone` copying.

## Saved data for the whole server

Data that belongs to no block, entity or player (a registry of teleporters, contents stored by an item) goes in a `WorldSavedData`, saved as `data/<name>.dat` in the world folder:

```java
public final class BagStore extends WorldSavedData {
    private static final String NAME = MyMod.MODID + "_bags";
    private NBTTagCompound contents = new NBTTagCompound();
    public BagStore() { super(NAME); }
    public BagStore(String name) { super(name); }      // used by MapStorage when loading

    public static BagStore get(WorldServer world) {
        MapStorage storage = world.getMinecraftServer().getWorld(0).getMapStorage();   // one store for all dimensions
        BagStore store = (BagStore) storage.getOrLoadData(BagStore.class, NAME);
        if (store == null) { store = new BagStore(); storage.setData(NAME, store); }
        return store;
    }
    public void put(String id, NBTTagCompound data) { contents.setTag(id, data); markDirty(); }
    @Override public void readFromNBT(NBTTagCompound nbt) { contents = nbt.getCompoundTag("Contents"); }
    @Override public NBTTagCompound writeToNBT(NBTTagCompound nbt) { nbt.setTag("Contents", contents); return nbt; }
}
```

Both constructors are required (loading uses the `String` one). Call `markDirty()` after every change or it is not saved. `world.getPerWorldStorage()` instead keeps one copy per dimension. Server only: the client has no saved data.

## Commands

```java
public class RubyCommand extends CommandBase {
    @Override public String getName() { return "rubies"; }
    @Override public String getUsage(ICommandSender sender) { return "/rubies <player> [count]"; }
    @Override public int getRequiredPermissionLevel() { return 2; }   // ops/cheats; CommandBase default 4 = console only
    @Override public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
        if (args.length < 1) throw new WrongUsageException(getUsage(sender));
        EntityPlayerMP target = getPlayer(server, sender, args[0]);     // names and selectors like @p
        int count = args.length > 1 ? parseInt(args[1], 1, 64) : 1;
        target.inventory.addItemStackToInventory(new ItemStack(ModItems.RUBY, count));
        sender.sendMessage(new TextComponentString("Gave " + count + " rubies to " + target.getName()));
    }
}
// Main class:
@Mod.EventHandler public void serverStarting(FMLServerStartingEvent event) { event.registerServerCommand(new RubyCommand()); }
```

Tab completion: override `getTabCompletions(...)` with `getListOfStringsMatchingLastWord(args, server.getOnlinePlayerNames())`. Permission 0 = everyone.

## Keybinds and personal screens (client)

```java
// ClientEvents (client only). Register once, e.g. in a static init method called from ClientEvents.models:
public static final KeyBinding OPEN = new KeyBinding("key.mymod.open", KeyConflictContext.IN_GAME, Keyboard.KEY_N, "key.categories.mymod");
ClientRegistry.registerKeyBinding(OPEN);

@SubscribeEvent public static void onClientTick(TickEvent.ClientTickEvent event) {
    if (event.phase != TickEvent.Phase.END) return;
    boolean pressed = false;
    while (OPEN.isPressed()) pressed = true;                       // drain presses
    Minecraft mc = Minecraft.getMinecraft();
    if (pressed && mc.player != null && mc.currentScreen == null) mc.displayGuiScreen(new MyScreen());
}
```

Lang keys `key.mymod.open` and `key.categories.mymod`. The screen (a `GuiScreen`) asks the server for state and sends actions by packet; the server validates and replies to that player only. Disable buttons until the first reply arrives.

## Test

Persisted data: write and read on a FakePlayer within a test. Commands: call `new RubyCommand().execute(server, fakePlayer, args)` directly; selectors and player-name lookups do not find FakePlayers (not in the player list), and `getRequiredPermissionLevel` can be asserted. Keybinds, screens and real login/logout need the client.
