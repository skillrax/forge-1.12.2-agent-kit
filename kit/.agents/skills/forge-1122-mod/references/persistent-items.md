# Persistent ticking items

Use for stored charge/time or other changing per-stack state. Choose stackability and carried locations from the behavior; a non-stackable time store avoids charge-splitting ambiguity. Keep state in the ItemStack, not mutable fields on the singleton Item.

For seconds ingame, count 20 server ticks per game second unless real elapsed time is requested. Lag slows game time; absolute timestamps can accidentally grant offline catch-up. Bound charge, converted durations and doubled costs before arithmetic. Failed use must leave charge, effect and progression unchanged.

The pinned InventoryPlayer.decrementAnimations calls ItemStack.updateAnimation across main, armor and offhand inventories. Item.onUpdate therefore needs a server-side/player check and explicit carried-location policy; check actual stack identity in the permitted inventories rather than trusting the slot number. Dropped items use a different hook. Never tick container contents merely because a player has a chest open.

NBT preserves per-stack state through vanilla inventory save/load. Store fractional ticks if exact sub-second continuity is required. Potion durations normally pause offline; a world-time deadline may reset a sequence even while the player's saved potion remains active. Tie reset behavior to the agreed effect semantics.

Vanilla inventory synchronization carries item NBT. Frequent writes can cause frequent packets and held-item re-equip animation. Suppress only internal bookkeeping changes using Item.shouldCauseReequipAnimation; retain legitimate slot/item/metadata/count and visual-tag changes. A tested pattern is:

```java
@Override
public boolean shouldCauseReequipAnimation(ItemStack oldStack, ItemStack newStack, boolean slotChanged) {
    if (slotChanged || oldStack.getItem() != newStack.getItem()
            || oldStack.getMetadata() != newStack.getMetadata()
            || oldStack.getCount() != newStack.getCount()) return true;
    NBTTagCompound before = oldStack.hasTagCompound() ? oldStack.getTagCompound().copy() : new NBTTagCompound();
    NBTTagCompound after = newStack.hasTagCompound() ? newStack.getTagCompound().copy() : new NBTTagCompound();
    before.removeTag("YourInternalState");
    after.removeTag("YourInternalState");
    return !before.equals(after);
}
```

Use your own bookkeeping compound name; preserve other tags and never mutate the synchronized originals while comparing. This stops animation, not packet traffic. Optimize synchronization only while preserving truthful tooltips and persistence; no custom network layer is needed just for this hook.

Acceptance: independent stacks/players, cadence/cap/spend/failure, transfer/drop/pause and vanilla save/load. Use a dedicated isolated world across server processes for restart checks: the kit's ordinary server profile creates a fresh world every time. Separate game assertions from real player login/logout. Keep acceptance-only classes out of the JAR.

In a graphical client, inspect live tooltip charge, held main/offhand stability during ticking/spending, and normal item/slot switching. Compilation/server readiness cannot reveal hand bobbing. Bounded source anchors: Item.onUpdate, InventoryPlayer.decrementAnimations, Item.shouldCauseReequipAnimation and ItemRenderer.updateEquippedItem. This pattern has user-observed Clockwork Bottle1.0.1 acceptance on the maintainer host; it is not other-PC or arbitrary modpack proof.
