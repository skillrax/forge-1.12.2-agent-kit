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
    before.removeTag("Charge");
    after.removeTag("Charge");
    return !before.equals(after);
}
```

## Test

`useItem(p, hand)` then check NBT, durability and spawned entities; `finishUsingItem` for food. The hand bobbing and tooltips need the client.
