# Events and config

## Event handlers

Change vanilla behavior without replacing vanilla classes: static `@SubscribeEvent` methods in a class annotated `@Mod.EventBusSubscriber(modid = MyMod.MODID)`.

```java
@Mod.EventBusSubscriber(modid = MyMod.MODID)
public final class ModEvents {
    @SubscribeEvent
    public static void onHurt(LivingHurtEvent event) {                 // server; change event.getAmount()
        if (!(event.getSource().getTrueSource() instanceof EntityLivingBase)) return;
        EntityLivingBase attacker = (EntityLivingBase) event.getSource().getTrueSource();
        int level = EnchantmentHelper.getMaxEnchantmentLevel(ModEnchantments.RUBY_EDGE, attacker);
        if (level > 0) event.setAmount(event.getAmount() + 1.5F * level);
    }

    @SubscribeEvent
    public static void onDrops(BlockEvent.HarvestDropsEvent event) {   // add/remove block drops
        if (event.getState().getBlock() == Blocks.GRAVEL && !event.isSilkTouching() && event.getWorld().rand.nextDouble() < ModConfig.gravelRubyChance)
            event.getDrops().add(new ItemStack(ModItems.RUBY));
    }

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {   // both sides
        EntityPlayer player = event.getEntityPlayer();
        if (player.isSneaking() && event.getWorld().getBlockState(event.getPos()).getBlock() == ModBlocks.RUBY_BLOCK) {
            if (!event.getWorld().isRemote) player.sendStatusMessage(new TextComponentTranslation("message.mymod.shiny"), true);
            event.setCanceled(true);                                    // stop the normal interaction
        }
    }
}
```

| Event (package net.minecraftforge.event...) | Use |
|---|---|
| `entity.living.LivingHurtEvent`, `LivingDamageEvent`, `LivingAttackEvent` | change or cancel damage |
| `entity.living.LivingDeathEvent`, `LivingDropsEvent`, `LivingExperienceDropEvent` | death, mob drops |
| `entity.living.LivingEvent.LivingUpdateEvent` | every entity tick (keep it cheap) |
| `entity.player.PlayerInteractEvent.RightClickBlock / RightClickItem / LeftClickBlock / EntityInteract` | clicks (both sides) |
| `entity.player.PlayerEvent.BreakSpeed`, `HarvestCheck`, `ItemCraftedEvent` (fml...gameevent.PlayerEvent), `PlayerLoggedInEvent` | mining speed, crafting, login |
| `world.BlockEvent.BreakEvent`, `PlaceEvent`, `HarvestDropsEvent` | break/place permission, drops |
| `entity.EntityJoinWorldEvent`, `entity.living.LivingSpawnEvent.CheckSpawn` | spawning |
| `fml.common.gameevent.TickEvent.ServerTickEvent / WorldTickEvent / PlayerTickEvent` | per-tick logic; check `event.phase == TickEvent.Phase.END` and `event.side`. 1.12.2 `ServerTickEvent` carries no server or world: for "every player each tick" use `WorldTickEvent` and `event.world.playerEntities` (includes the test `targetPlayer`; `PlayerTickEvent` never fires for test players, which do not tick) |
| `AnvilUpdateEvent`, `furnace.FurnaceFuelBurnTimeEvent` | anvil recipes, fuel |
| client only: `RenderGameOverlayEvent`, `GuiScreenEvent`, `InputEvent.KeyInputEvent`, `TickEvent.ClientTickEvent` | put in ClientEvents |

Rules: handler methods must be `public static` in a `@Mod.EventBusSubscriber` class; the event type decides what fires. Many events fire on both sides: check `world.isRemote`. Cancel only `@Cancelable` events. Find others: `./agent.ps1 api -Find Event` or `api LivingHurtEvent`.

## Config

```java
@Config(modid = MyMod.MODID)                                         // config/mymod.cfg, editable in Mods > Config
public final class ModConfig {
    @Config.Comment("Chance (0-1) that gravel also drops a ruby")
    @Config.RangeDouble(min = 0, max = 1)
    public static double gravelRubyChance = 0.1;

    @Config.Comment("Ruby zombie spawn weight")
    @Config.RangeInt(min = 0, max = 100)
    @Config.RequiresMcRestart
    public static int zombieWeight = 20;

    @Mod.EventBusSubscriber(modid = MyMod.MODID)
    public static final class Sync {
        @SubscribeEvent public static void onChanged(ConfigChangedEvent.OnConfigChangedEvent event) {
            if (event.getModID().equals(MyMod.MODID)) ConfigManager.sync(MyMod.MODID, Config.Type.INSTANCE);
        }
    }
}
```

Read the static fields anywhere. Values used during registration (spawn weights) need a restart. Nested categories: a `public static` field holding an object with its own public fields.

## Test

Events fire in game tests through the real code paths (`attack`, `breakBlock`, `useOnBlock`). Example: hit two equal mobs, one with the enchanted weapon, compare health; FakePlayer attacks are at ~20% cooldown strength, so compare differences, not absolute damage.
