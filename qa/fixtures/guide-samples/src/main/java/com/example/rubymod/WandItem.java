package com.example.rubymod;

import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.client.resources.I18n;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.projectile.EntitySmallFireball;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ActionResult;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/** Right-click shoots a fireball: 64 uses, 1 s cooldown, per-stack shot counter in NBT. */
public class WandItem extends Item {
    public WandItem() {
        setMaxStackSize(1);
        setMaxDamage(64);
        setCreativeTab(CreativeTabs.COMBAT);
    }

    @Override
    public ActionResult<ItemStack> onItemRightClick(World world, EntityPlayer player, EnumHand hand) {
        ItemStack stack = player.getHeldItem(hand);
        if (!world.isRemote) {
            Vec3d look = player.getLookVec();
            EntitySmallFireball ball = new EntitySmallFireball(world, player, look.x, look.y, look.z);
            ball.setPosition(player.posX + look.x, player.posY + player.getEyeHeight() + look.y, player.posZ + look.z);
            world.spawnEntity(ball);
            NBTTagCompound tag = stack.hasTagCompound() ? stack.getTagCompound() : new NBTTagCompound();
            tag.setInteger("Shots", tag.getInteger("Shots") + 1);
            stack.setTagCompound(tag);
            stack.damageItem(1, player);
        }
        player.getCooldownTracker().setCooldown(this, 20);
        return new ActionResult<>(EnumActionResult.SUCCESS, stack);
    }

    /** The Shots counter changes on use; only other changes re-equip (bob) the held wand. */
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

    @SideOnly(Side.CLIENT)
    @Override
    public void addInformation(ItemStack stack, @Nullable World world, List<String> tooltip, ITooltipFlag flag) {
        int shots = stack.hasTagCompound() ? stack.getTagCompound().getInteger("Shots") : 0;
        tooltip.add(I18n.format("item.rubymod.wand.tooltip", shots));
    }
}
