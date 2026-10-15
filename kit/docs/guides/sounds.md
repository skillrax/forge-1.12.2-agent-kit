# Sounds

```java
@Mod.EventBusSubscriber(modid = MyMod.MODID)
public final class ModSounds {
    public static final SoundEvent ZAP = sound("zap");

    private static SoundEvent sound(String name) {
        ResourceLocation id = new ResourceLocation(MyMod.MODID, name);
        return new SoundEvent(id).setRegistryName(id);                     // registry name = sound name
    }
    @SubscribeEvent public static void register(RegistryEvent.Register<SoundEvent> event) { event.getRegistry().register(ZAP); }
}
```

`assets/mymod/sounds.json` (subtitle keys go in the lang file):

```json
{ "zap": { "category": "block", "subtitle": "mymod.subtitle.zap", "sounds": [ "mymod:zap" ] } }
```

The file is `assets/mymod/sounds/zap.ogg` (Ogg Vorbis, mono for positional sounds). `check server` reports missing sounds.json entries and .ogg files.

Play on the server so everyone nearby hears it: `world.playSound(null, pos, ModSounds.ZAP, SoundCategory.BLOCKS, 1.0F, 1.0F)`. With a player instead of null, that player does not hear it (for sounds their client already played). Vanilla sounds: `SoundEvents.*`. Block step/break sounds come from the block's `SoundType`.
