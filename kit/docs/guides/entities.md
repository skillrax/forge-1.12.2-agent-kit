# Entities (mobs, projectiles)

Simplest robust path: extend a vanilla entity and reuse its renderer and model. `agent.ps1 new mob <name> -Like <base>` writes all of the below (docs/GENERATORS.md); add behavior to the generated class. A brand-new model needs a `ModelBase` subclass and `RenderLiving` (client only).

```java
public class RubyZombie extends EntityZombie {
    public static final ResourceLocation LOOT = LootTableList.register(new ResourceLocation(MyMod.MODID, "entities/ruby_zombie"));

    public RubyZombie(World world) { super(world); }              // required (World) constructor

    @Override protected void applyEntityAttributes() {
        super.applyEntityAttributes();
        getEntityAttribute(SharedMonsterAttributes.MAX_HEALTH).setBaseValue(40.0D);
        getEntityAttribute(SharedMonsterAttributes.ATTACK_DAMAGE).setBaseValue(6.0D);
    }
    @Override protected ResourceLocation getLootTable() { return LOOT; }
}

@Mod.EventBusSubscriber(modid = MyMod.MODID)
public final class ModEntities {
    private static int nextId = 0;                                   // network id, unique within the mod
    @SubscribeEvent public static void register(RegistryEvent.Register<EntityEntry> event) {
        event.getRegistry().register(EntityEntryBuilder.create()
                .entity(RubyZombie.class)
                .id(new ResourceLocation(MyMod.MODID, "ruby_zombie"), nextId++)
                .name(MyMod.MODID + ".ruby_zombie")                // lang: entity.mymod.ruby_zombie.name
                .tracker(64, 3, true)                               // tracking range, update interval, velocity updates
                .egg(0x3a5f2e, 0xc01f3f)                            // spawn egg in the Miscellaneous tab
                .spawn(EnumCreatureType.MONSTER, ModConfig.zombieWeight, 1, 3, Biomes.PLAINS, Biomes.DESERT)   // weight (20 = like zombies; here a config value), min, max group
                .build());
    }
}

// ClientEvents.models(...):
RenderingRegistry.registerEntityRenderingHandler(RubyZombie.class, RenderZombie::new);
```

`assets/mymod/loot_tables/entities/ruby_zombie.json`:

```json
{ "pools": [ { "name": "rubies", "rolls": 1,
  "entries": [ { "type": "item", "name": "mymod:ruby", "weight": 1,
    "functions": [ { "function": "set_count", "count": { "min": 0, "max": 2 } },
                   { "function": "looting_enchant", "count": { "min": 0, "max": 1 } } ] } ] } ] }
```

Notes:
- Tracker values: mobs `64, 3, true`; projectiles `64, 10, true`; slow decorative entities `64, 20, false`.
- AI: override `initEntityAI()` and add `tasks.addTask(priority, new EntityAIxxx(...))`, `targetTasks` for targets. Vanilla AI classes are in `net.minecraft.entity.ai`.
- Movement comes cheapest from the vanilla class you extend: `EntityBlaze` hovers and is fire immune, `EntityGhast` flies (`EntityFlying` + its move helper), `EntityBat` flutters (ambient), `EntityVex` flies through blocks, `EntitySpider` climbs walls, `EntityGuardian`/`EntitySquid` swim. Its renderer can usually be reused (`RenderBlaze::new`).
- Targeting players: `EntityAINearestAttackableTarget` asks `world.getNearestAttackablePlayer`, which searches only real players, then `EntityAITarget.isSuitableTarget`: alive, `canAttackClass`, not same team or owner, not a creative player (`capabilities.disableDamage`), visible if sight is checked, within home distance.
- Saved fields: `writeEntityToNBT`/`readEntityFromNBT`. Values the client must see (size, variant): `EntityDataManager` with `DataParameter` registered in `entityInit()`.
- Projectiles: extend `EntityThrowable` (override `onImpact`), render with `RenderSnowball` and an item.
- Textures for custom renderers: `assets/mymod/textures/entity/<name>.png`.
- Entities only tick when the 32-block area around them is loaded (more under "Ticking" below).

## Ticking, removing, copying

- `onUpdate` runs on both sides; the client copy only predicts. Server-only logic: `if (world.isRemote) return;` after `super.onUpdate()`. Timers in a field are lost on reload unless saved in `writeEntityToNBT`.
- `setDead()` removes it at the end of the entity loop, but subclasses may react (chest and hopper minecarts drop their contents), and it is not a kill: no death event, no loot. To take an entity out of the world unchanged (to store or move it), detach it instead. `onEntityRemoved` frees its UUID; without that, spawning it again is refused.

```java
w.getChunk(e.chunkCoordX, e.chunkCoordZ).removeEntity(e);
w.loadedEntityList.remove(e);
w.onEntityRemoved(e);
```

  Only from a click, command or `WorldTickEvent` END, never during the entity loop (docs/FORGE-1.12.md).
- Copy: `e.writeToNBTOptional(tag)` (false for dead entities and riders; saves the type `id` and the UUID); restore: `EntityList.createEntityFromNBT(tag, world)` (null if the mod is missing), then `world.spawnEntity(e)`. Same UUID: refuse if any `server.worlds` returns non-null from `getEntityFromUuid`, or you have a duplicate.
- Moving the copy: edit the `Pos` list in the NBT, plus `TileX/TileY/TileZ` (paintings, item frames, leash knots) and `xTile/yTile/zTile` (stuck arrows). Another dimension: set `e.dimension` to the target world's `provider.getDimension()` and spawn into that `WorldServer`. A live teleport of an existing entity is `e.changeDimension(dim)` instead.
- Players, riders and multipart entities (`getParts() != null`, the dragon) need their own handling: usually refuse them.

## Villagers and trades

Prefer vanilla `ContainerMerchant`/`GuiMerchant` through `IMerchant`. The merchant must update a recipe's uses exactly once when the result is taken (`SlotMerchantResult.onTake` calls `useRecipe`). Release the previous customer before assigning a new one; closing the old menu afterwards can clear the new customer.

## Test

`new mob` already writes checks for stats, egg, drop range and spawn biomes. For custom behavior: `t.spawn("mymod:ruby_zombie", pos)`, then check attributes. Hostility: put a `t.targetPlayer(name)` nearby and `waitUntil` `getAttackTarget() == player` (a plain `t.player` is never targeted). Drops: `t.loot("mymod:entities/ruby_zombie")` per roll. The test world is daytime: zombies and skeletons burn. Natural spawning in the world and rendering need the client.
