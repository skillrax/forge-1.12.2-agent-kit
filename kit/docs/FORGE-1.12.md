# Forge 1.12.2 essentials

Read once per session before hand-writing Forge code. Verify anything else with `./agent.ps1 api`.

## Files

| Path under src/main/resources/assets/<modid>/ | Content |
|---|---|
| `lang/en_us.lang` | `item.<modid>.<name>.name=Text`, `tile.<modid>.<name>.name=Text` (lowercase file name) |
| `blockstates/<block>.json` | one variant per state: `"normal"` without properties, else `"facing=north"`, `"age=0,lit=true"` (properties alphabetical) |
| `models/block/<name>.json`, `models/item/<name>.json` | parents: `block/cube_all`, `block/cube_column`, `block/orientable`, `item/generated`, `item/handheld` |
| `textures/blocks/`, `textures/items/` | 16x16 PNG; referenced as `<modid>:blocks/<name>` (no `textures/`, no `.png`) |
| `recipes/<name>.json` | crafting recipes (not `data/`) |
| `loot_tables/`, `sounds.json`, `sounds/`, `textures/gui/`, `textures/entity/`, `textures/models/armor/` | as named |

A path without `modid:` means `minecraft:`. That is the most common texture/model mistake.

## Lifecycle

1. Registry events (`@Mod.EventBusSubscriber` + `@SubscribeEvent static void x(RegistryEvent.Register<Block|Item|Potion|Enchantment|EntityEntry|SoundEvent|Biome|IRecipe> e)`): register objects. Blocks fire before items.
2. `preInit`: config, network channel, GUI handler, world generators, tile entity registration if not done in the block event.
3. `init`: smelting, ore dictionary (`OreDictionary.registerOre("gemRuby", ModItems.RUBY)`), repair items.
4. `FMLServerStartingEvent`: commands (`event.registerServerCommand`).

Never create or register registry objects outside these events. An object gets exactly one registry name: `setRegistryName(MODID, "name")`, lowercase.

## Sides

- Physical client = game window; physical server = dedicated server. Logical server runs the world (also in single player); logical client draws it.
- `world.isRemote` true = logical client. Game rules, inventory changes, spawning, damage: server only (`if (!world.isRemote)`). Methods like `onItemRightClick` run on both sides; return the same result on both.
- Classes under `net.minecraft.client.*` (Minecraft, Gui*, Render*, models, KeyBinding, I18n of client.resources) only exist on the client. Use them only in classes annotated `@SideOnly(Side.CLIENT)` or `@Mod.EventBusSubscriber(value = Side.CLIENT)`, or in methods that only run on the client (and mark overriding client methods `@SideOnly(Side.CLIENT)`). `./agent.ps1 api` marks such members `[client-only]`.
- Server to client data: tile entity sync (guides/tile-entities.md) or packets (guides/networking.md).

## What ticks, where, when

20 ticks per second. Each server tick first handles queued player actions (right-clicks, GUI clicks: so item and block click methods never run inside a world tick), then each world in turn: `WorldTickEvent` START, block ticks, entities, tile entities, `WorldTickEvent` END.

| Code | Runs | Side | Only while |
|---|---|---|---|
| `Entity.onUpdate` | every tick | both: the client runs its copy and interpolates; the server decides | 32 blocks around it are loaded (or a forced chunk) |
| `ITickable.update` on a tile | every tick, only if the tile class implements `ITickable` | both | its chunk is loaded and the tile is valid |
| `Block.updateTick` | once, `world.scheduleUpdate(pos, block, delay)` ticks later; or random (`setTickRandomly(true)`, ~every 68 s per block) | server | chunk loaded |
| `Item.onUpdate` | every tick while in a player's inventory, armor or offhand; worn armor also `onArmorTick`; dropped: `onEntityItemUpdate`; mobs' held items: never | both | the player ticks |
| `TickEvent.*` | twice per tick: `event.phase` START and END | Server/World: server; Player: **both**; Client/Render: client | always (World per loaded world) |

- Filter tick events or they run twice, and on the wrong side: `if (event.phase != TickEvent.Phase.END || event.side != Side.SERVER) return;` (`event.side` exists on World/Player events; `WorldTickEvent` also has `event.world`).
- Nothing ticks in unloaded chunks: a machine stops when its player leaves. Catch up on load with `world.getTotalWorldTime()` deltas instead of counting ticks, or keep the chunk loaded with a Forge chunk ticket.
- `getTotalWorldTime()` always counts up; `getWorldTime()` is the time of day (`/time set` changes it).
- Inside an entity or tile tick, don't edit `world.loadedEntityList` or chunk entity lists. `entity.setDead()` is safe: the world removes dead entities after the entity loop. `world.spawnEntity` and `world.setTileEntity` are safe too (tiles added during the tile loop are queued). For larger edits (moving many blocks or entities), queue the work and run it in `WorldTickEvent` END.
- A client-side `onUpdate` that changes the world desyncs; the server copy wins on the next update.

## Common overrides (exact 1.12.2 signatures)

| Item | |
|---|---|
| `ActionResult<ItemStack> onItemRightClick(World, EntityPlayer, EnumHand)` | right-click air |
| `EnumActionResult onItemUse(EntityPlayer, World, BlockPos, EnumHand, EnumFacing, float, float, float)` | right-click a block, only if the block's `onBlockActivated` returned false (a chest opens instead) or the player sneaks |
| `EnumActionResult onItemUseFirst(EntityPlayer, World, BlockPos, EnumFacing, float, float, float, EnumHand)` | right-click a block before the block reacts (tools that click any block: wrenches, selectors); return SUCCESS on both sides |
| `boolean hitEntity(ItemStack, EntityLivingBase target, EntityLivingBase attacker)` | after a melee hit |
| `void onUpdate(ItemStack, World, Entity, int slot, boolean selected)` | every tick in an inventory |
| `ItemStack onItemUseFinish(ItemStack, World, EntityLivingBase)` + `getMaxItemUseDuration`, `getItemUseAction` | eat/drink style |
| `@SideOnly(CLIENT) void addInformation(ItemStack, World, List<String>, ITooltipFlag)` | tooltip |
| `int getItemBurnTime(ItemStack)` | furnace fuel ticks (coal = 1600) |

| Block | |
|---|---|
| `boolean onBlockActivated(World, BlockPos, IBlockState, EntityPlayer, EnumHand, EnumFacing, float, float, float)` | right-click; return true when handled |
| `IBlockState getStateForPlacement(World, BlockPos, EnumFacing, float, float, float, int meta, EntityLivingBase)` | state when placed |
| `void onBlockPlacedBy(World, BlockPos, IBlockState, EntityLivingBase, ItemStack)` | after placing |
| `void breakBlock(World, BlockPos, IBlockState)` | removed (drop tile contents here, then `super`) |
| `void getDrops(NonNullList<ItemStack>, IBlockAccess, BlockPos, IBlockState, int fortune)` | drops |
| `void neighborChanged(IBlockState, World, BlockPos, Block, BlockPos fromPos)` | redstone/neighbor updates |
| `void updateTick(World, BlockPos, IBlockState, Random)` | scheduled (`world.scheduleUpdate`) or random ticks (`setTickRandomly(true)`) |
| `void onEntityCollision(World, BlockPos, IBlockState, Entity)` / `onEntityWalk(World, BlockPos, Entity)` | entity inside / walking on |
| `int getWeakPower(IBlockState, IBlockAccess, BlockPos, EnumFacing)` + `canProvidePower` | emit redstone |

Blocks with properties also need `createBlockState`, `getStateFromMeta`, `getMetaFromState` (16 metadata values max) and a blockstate JSON variant per state (example in guides/blocks.md).

## Everyday API

- `ItemStack.EMPTY` / `stack.isEmpty()` (never null); `getCount`, `setCount`, `grow`, `shrink`, `copy`.
- NBT: `stack.getTagCompound()` may be null; `NBTTagCompound#setInteger/getInteger/setTag/getCompoundTag/hasKey`.
- Messages: `player.sendMessage(new TextComponentString("text"))`, action bar `player.sendStatusMessage(component, true)`, translated `new TextComponentTranslation("key", args)`.
- Spawning: `world.spawnEntity(entity)`; drops `Block.spawnAsEntity(world, pos, stack)` or `InventoryHelper.spawnItemStack`.
- Sounds: `world.playSound(null, pos, SoundEvents.X, SoundCategory.BLOCKS, 1.0F, 1.0F)` (null player = everyone hears it).
- Time: 20 ticks = 1 second. `world.getTotalWorldTime()` for timers.
- Effects: `new PotionEffect(MobEffects.SPEED, ticks, amplifier)`; amplifier 0 = level I.
- `setHeldItem`/`setItemStackToSlot` play the equip sound; to move items silently set `player.inventory.mainInventory`/`armorInventory` entries. XP: `addExperience` cannot remove points (negative values break the bar); use `addExperienceLevel(-n)` or set `experienceLevel`, `experience` (bar 0-1) and `experienceTotal` together.

Names from newer versions or older mappings are wrong here: `setTranslationKey` (not setUnlocalizedName), `CreativeTabs.MATERIALS`, `EntityPlayer`, `EnumHand`, `EnumFacing`, `NBTTagCompound`, `IBlockState`, `world.getChunk`, `MathHelper.floor`. The compiler hints list more. Vanilla translation keys often differ from registry names (dye is `item.dyePowder.<colour>.name`): never assemble one; take it from a stack, `stack.getTranslationKey() + ".name"` (or `stack.getDisplayName()` on the client).
