# Entities (mobs, projectiles)

Simplest robust path: extend a vanilla entity and reuse its renderer and model. A brand-new model needs a `ModelBase` subclass and `RenderLiving` (client only).

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
                .spawn(EnumCreatureType.MONSTER, 20, 1, 3, Biomes.PLAINS, Biomes.DESERT)   // weight, min, max group
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
- Saved fields: `writeEntityToNBT`/`readEntityFromNBT`. Values the client must see (size, variant): `EntityDataManager` with `DataParameter` registered in `entityInit()`.
- Projectiles: extend `EntityThrowable` (override `onImpact`), render with `RenderSnowball` and an item.
- Textures for custom renderers: `assets/mymod/textures/entity/<name>.png`.
- Entities only tick when the 32-block area around them is loaded.

## Villagers and trades

Prefer vanilla `ContainerMerchant`/`GuiMerchant` through `IMerchant`. The merchant must update a recipe's uses exactly once when the result is taken (`SlotMerchantResult.onTake` calls `useRecipe`). Release the previous customer before assigning a new one; closing the old menu afterwards can clear the new customer.

## Test

`t.spawn("mymod:ruby_zombie", pos)`, then check attributes. Loot table loads: `t.world().getLootTableManager().getLootTableFromLocation(RubyZombie.LOOT) != LootTable.EMPTY_LOOT_TABLE`. The test world is daytime: zombies and skeletons burn. Spawning in biomes and rendering need the client.
