package dev.agentic.workshop;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ActionResult;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumHand;
import net.minecraft.world.World;
public final class PouchItem extends Item {
    public PouchItem() {
        setRegistryName(Workshop.ID,"pouch");setTranslationKey(Workshop.ID+".pouch");
        setMaxStackSize(1);setCreativeTab(CreativeTabs.TOOLS);
    }
    @Override public ActionResult<ItemStack> onItemRightClick(World world,EntityPlayer player,EnumHand hand) {
        ItemStack stack=player.getHeldItem(hand);
        if(hand!=EnumHand.MAIN_HAND)return new ActionResult<>(EnumActionResult.PASS,stack);
        if(!world.isRemote)player.openGui(Workshop.instance,1,world,player.inventory.currentItem,0,0);
        return new ActionResult<>(EnumActionResult.SUCCESS,stack);
    }
}
