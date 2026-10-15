# Potion effects and enchantments

Vanilla effects on food: `agent.ps1 new food ... -Effect name:ticks:amplifier`. Apply effects in code: `entity.addPotionEffect(new PotionEffect(MobEffects.SPEED, ticks, amplifier))` (amplifier 0 = level I).

New effects and enchantments are registry entries; what they do is your code.

```java
@Mod.EventBusSubscriber(modid = MyMod.MODID)
public final class ModEnchantments {
    public static final Enchantment RUBY_EDGE = new RubyEdge();
    public static final Potion SHINE = new Shine();

    @SubscribeEvent public static void enchantments(RegistryEvent.Register<Enchantment> event) { event.getRegistry().register(RUBY_EDGE); }
    @SubscribeEvent public static void potions(RegistryEvent.Register<Potion> event) { event.getRegistry().register(SHINE); }

    static class RubyEdge extends Enchantment {                            // lang: enchantment.mymod.ruby_edge=Ruby Edge
        RubyEdge() {
            super(Rarity.UNCOMMON, EnumEnchantmentType.WEAPON, new EntityEquipmentSlot[] {EntityEquipmentSlot.MAINHAND});
            setName(MyMod.MODID + ".ruby_edge");
            setRegistryName(MyMod.MODID, "ruby_edge");
        }
        @Override public int getMaxLevel() { return 3; }
        @Override public int getMinEnchantability(int level) { return 5 + (level - 1) * 10; }
        @Override public int getMaxEnchantability(int level) { return getMinEnchantability(level) + 20; }
    }

    static class Shine extends Potion {                                    // lang: effect.mymod.shine=Shine
        Shine() {
            super(false, 0xC01F3F);                                         // isBadEffect, color
            setPotionName("effect." + MyMod.MODID + ".shine");
            setRegistryName(MyMod.MODID, "shine");
        }
        @Override public boolean isReady(int duration, int amplifier) { return duration % (40 >> Math.min(amplifier, 3)) == 0; }
        @Override public void performEffect(EntityLivingBase entity, int amplifier) { if (!entity.world.isRemote) entity.heal(1.0F); }
        @SideOnly(Side.CLIENT) @Override public boolean hasStatusIcon() { return false; }   // client-only method; see below for an icon
    }
}
```

- The enchantment's effect is an event: `LivingHurtEvent` + `EnchantmentHelper.getMaxEnchantmentLevel(RUBY_EDGE, attacker)` (guides/events.md). For tools: `EnchantmentHelper.getEnchantmentLevel(ench, stack)`.
- `EnumEnchantmentType`: WEAPON, DIGGER, ARMOR, ARMOR_HEAD/CHEST/LEGS/FEET, BOW, FISHING_ROD, BREAKABLE, ALL. Treasure-only: override `isTreasureEnchantment()`. Incompatible with another: `canApplyTogether`.
- Effect icons: keep `hasStatusIcon()` true and override `@SideOnly(Side.CLIENT) renderInventoryEffect(...)`/`renderHUDEffect(...)` to draw your own 18x18 texture; otherwise return false as above.
- Brewable potions need a `PotionType` (registered in `RegistryEvent.Register<PotionType>`) and `PotionHelper.addMix(PotionTypes.AWKWARD, Items.X, MY_TYPE)`.

## Test

Add the effect to a mob that doesn't burn in daylight (`minecraft:cow`), `waitTicks`, check health. Enchantment: hit two equal mobs with and without the enchanted item and compare.
