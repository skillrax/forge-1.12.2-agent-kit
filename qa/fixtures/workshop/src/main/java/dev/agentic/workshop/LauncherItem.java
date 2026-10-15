package dev.agentic.workshop;

import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ActionResult;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumHand;
import net.minecraft.world.World;

public final class LauncherItem extends Item {
    public LauncherItem() {
        setRegistryName(Workshop.ID,"test_launcher");setTranslationKey(Workshop.ID+".test_launcher");
        setMaxStackSize(1);setCreativeTab(CreativeTabs.COMBAT);
    }
    @Override public ActionResult<ItemStack> onItemRightClick(World world,EntityPlayer player,EnumHand hand) {
        ItemStack held=player.getHeldItem(hand);
        if(hand!=EnumHand.MAIN_HAND)return new ActionResult<>(EnumActionResult.PASS,held);
        if(!player.isEntityAlive() || player.isSpectator() || player.getCooldownTracker().hasCooldown(this))
            return new ActionResult<>(EnumActionResult.FAIL,held);
        if(!world.isRemote) {
            TestProjectile shot=new TestProjectile(world,player);
            shot.shoot(player,player.rotationPitch,player.rotationYaw,0F,1.5F,0F);
            if(!world.spawnEntity(shot))return new ActionResult<>(EnumActionResult.FAIL,held);
            player.getCooldownTracker().setCooldown(this,10);
        }
        return new ActionResult<>(EnumActionResult.SUCCESS,held);
    }
}
