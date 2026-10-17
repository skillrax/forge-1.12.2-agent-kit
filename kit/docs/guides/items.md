# Items with behavior

Plain items, food, tools, armor: use generators. This guide covers custom behavior: subclass `Item` and register it with `item("name", new MyItem())` in ModItems (model and lang as for any item).

## Right-click, durability, cooldown, per-stack data

```java
public class WandItem extends Item {
    public WandItem() { setMaxStackSize(1); setMaxDamage(64); setCreativeTab(CreativeTabs.COMBAT); }

    @Override
    public ActionResult<ItemStack> onItemRightClick(World world, EntityPlayer player, EnumHand hand) {
        ItemStack stack = player.getHeldItem(hand);
        if (!world.isRemote) {                                     // world changes: server only
            Vec3d look = player.getLookVec();
            EntitySmallFireball ball = new EntitySmallFireball(world, player, look.x, look.y, look.z);
            ball.setPosition(player.posX + look.x, player.posY + player.getEyeHeight() + look.y, player.posZ + look.z);
            world.spawnEntity(ball);
            NBTTagCompound tag = stack.hasTagCompound() ? stack.getTagCompound() : new NBTTagCompound();
            tag.setInteger("Shots", tag.getInteger("Shots") + 1);   // per-stack state lives in NBT, never in fields
            stack.setTagCompound(tag);
            stack.damageItem(1, player);                            // breaks at 0 durability
        }
        player.getCooldownTracker().setCooldown(this, 20);          // both sides, 20 ticks
        return new ActionResult<>(EnumActionResult.SUCCESS, stack);
    }

    @SideOnly(Side.CLIENT)                                          // tooltips are client-only
    @Override
    public void addInformation(ItemStack stack, @Nullable World world, List<String> tooltip, ITooltipFlag flag) {
        int shots = stack.hasTagCompound() ? stack.getTagCompound().getInteger("Shots") : 0;
        tooltip.add(I18n.format("item.mymod.wand.tooltip", shots));    // net.minecraft.client.resources.I18n; lang: ...tooltip=Shots: %s
    }
}
```

Clicking a block: `onItemUse` runs only after the block's `onBlockActivated` declined (a chest or furnace opens instead) or while sneaking. An item that must work on any block (wrench, selector, region tool) overrides `onItemUseFirst`, which runs before the block reacts, and returns `SUCCESS` on both sides.

Other hooks: `onItemUse` (on a block), `hitEntity`, `onUpdate` (each tick in an inventory), `onItemUseFinish` + `getMaxItemUseDuration` + `getItemUseAction` (eat/drink/bow), `getItemBurnTime` (fuel). Signatures: docs/FORGE-1.12.md.

## Metadata variants (one item, several textures)

`setHasSubtypes(true)`, override `getTranslationKey(ItemStack)` to append the variant, and `getSubItems(CreativeTabs tab, NonNullList<ItemStack> items)` (`if (isInCreativeTab(tab))` add each meta). Implement `ClientEvents.CustomModel` and register each meta in `ClientEvents.models`: `ModelLoader.setCustomModelResourceLocation(item, meta, new ModelResourceLocation(MODID + ":name_" + meta, "inventory"))` with `models/item/name_<meta>.json`. Separate items are usually simpler.

## Own creative tab

```java
public static final CreativeTabs TAB = new CreativeTabs(MODID) {      // in the main class; lang: itemGroup.<modid>=My Mod
    @SideOnly(Side.CLIENT) @Override public ItemStack createIcon() { return new ItemStack(ModItems.RUBY); }
};
```

Then `.setCreativeTab(MyMod.TAB)` on items/blocks.

## Items that hold a lot of data

Stack NBT is sent to the client in every inventory update, and a packet over 2 MiB disconnects the player. Keep large or growing data (captured blocks, stored entities, big lists) in server-wide saved data (guides/players.md) under a random `UUID` key. The stack holds only that key plus what the client must show (a size, a short preview). Remove the entry when it is used, so a copied stack (creative middle-click) cannot use it twice. Limit the size before accepting new data.

## Items that tick or store charge

- Keep state in the stack's NBT; the `Item` object is shared by all stacks.
- `onUpdate` runs for main inventory, armor and offhand on both sides: check `!world.isRemote`, the entity type and which inventory the stack is really in. Dropped items do not call it.
- Count server ticks (20 per second); `world.getTotalWorldTime()` deadlines also count while the player is offline.
- Changing NBT every tick makes the held item bob. Suppress it for your bookkeeping key only:

```java
@Override
public boolean shouldCauseReequipAnimation(ItemStack oldStack, ItemStack newStack, boolean slotChanged) {
    if (slotChanged || oldStack.getItem() != newStack.getItem() || oldStack.getMetadata() != newStack.getMetadata()
            || oldStack.getCount() != newStack.getCount()) return true;
    NBTTagCompound before = oldStack.hasTagCompound() ? oldStack.getTagCompound().copy() : new NBTTagCompound();
    NBTTagCompound after = newStack.hasTagCompound() ? newStack.getTagCompound().copy() : new NBTTagCompound();
    before.removeTag("Shots");
    after.removeTag("Shots");
    return !before.equals(after);
}
```

## Coloured armor

`ItemArmor`'s `hasColor/getColor/setColor` and the vanilla dye recipe work only for `ArmorMaterial.LEATHER`: for your own material override those three (NBT `display.color`) and add your own dye `IRecipe` (guides/recipes.md). Once `getColor` returns a colour, the worn layer also binds `textures/models/armor/<material>_layer_1_overlay.png` (and `_layer_2_`): provide them (transparent is fine), or the head renders as a missing texture.

## Test

`useItem(p, hand)` then check NBT, durability and spawned entities; `finishUsingItem` for food; chat feedback with `t.messages(p)`. Held-item behavior (`onUpdate`) never runs on a FakePlayer: call it yourself each tick (docs/TESTING.md). The hand bobbing and tooltips need the client.
