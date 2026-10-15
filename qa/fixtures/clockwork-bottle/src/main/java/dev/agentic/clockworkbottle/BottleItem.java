package dev.agentic.clockworkbottle;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.client.resources.I18n;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.MobEffects;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.ActionResult;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumHand;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
public final class BottleItem extends Item {
    public BottleItem() {
        setRegistryName(ClockworkBottle.MOD_ID,"clockwork_bottle");
        setTranslationKey("clockworkbottle.clockwork_bottle");
        setCreativeTab(CreativeTabs.TOOLS);setMaxStackSize(1);
    }
    private static NBTTagCompound data(ItemStack stack) {
        NBTTagCompound root=stack.getTagCompound();
        return root==null?new NBTTagCompound():root.getCompoundTag("ClockworkBottle");
    }
    private static void save(ItemStack stack,NBTTagCompound state) {
        if(!stack.hasTagCompound())stack.setTagCompound(new NBTTagCompound());
        stack.getTagCompound().setTag("ClockworkBottle",state);
    }
    public static int chargeTicks(ItemStack stack) {return ChargeMath.boundedTicks(data(stack).getLong("ChargeTicks"));}
    @Override public boolean shouldCauseReequipAnimation(ItemStack oldStack,ItemStack newStack,boolean slotChanged) {
        if(slotChanged || oldStack.getItem()!=newStack.getItem()
            || oldStack.getMetadata()!=newStack.getMetadata() || oldStack.getCount()!=newStack.getCount())return true;
        // Charge/sequence updates are not a new held item. Preserve visual NBT changes.
        NBTTagCompound oldVisual=oldStack.hasTagCompound()?oldStack.getTagCompound().copy():new NBTTagCompound();
        NBTTagCompound newVisual=newStack.hasTagCompound()?newStack.getTagCompound().copy():new NBTTagCompound();
        oldVisual.removeTag("ClockworkBottle");newVisual.removeTag("ClockworkBottle");
        return !oldVisual.equals(newVisual);
    }
    private static boolean inInventory(EntityPlayer player,ItemStack stack) {
        for(ItemStack candidate:player.inventory.mainInventory)if(candidate==stack)return true;
        for(ItemStack candidate:player.inventory.offHandInventory)if(candidate==stack)return true;
        return false;
    }
    @Override public void onUpdate(ItemStack stack,World world,Entity entity,int slot,boolean selected) {
        if(world.isRemote || !(entity instanceof EntityPlayerMP) || entity.isDead)return;
        EntityPlayer player=(EntityPlayer)entity;
        if(!inInventory(player,stack))return;
        NBTTagCompound state=data(stack);
        boolean reset=player.getUniqueID().toString().equals(state.getString("Owner"))
            && !player.isPotionActive(MobEffects.NIGHT_VISION);
        if(reset) {state.removeTag("Owner");state.removeTag("NextSeconds");}
        int before=chargeTicks(stack),after=ChargeMath.advance(before);
        if(reset || before!=after || state.getLong("ChargeTicks")!=after) {
            state.setInteger("ChargeTicks",after);save(stack,state);
        }
    }
    @Override public ActionResult<ItemStack> onItemRightClick(World world,EntityPlayer player,EnumHand hand) {
        ItemStack stack=player.getHeldItem(hand);
        if(hand!=EnumHand.MAIN_HAND)return new ActionResult<ItemStack>(EnumActionResult.PASS,stack);
        if(world.isRemote)return new ActionResult<ItemStack>(EnumActionResult.SUCCESS,stack);
        NBTTagCompound state=data(stack);
        boolean continuing=player.isPotionActive(MobEffects.NIGHT_VISION)
            && player.getUniqueID().toString().equals(state.getString("Owner"));
        long seconds=continuing?state.getLong("NextSeconds"):15L;
        if(seconds<15)seconds=15;
        int remaining=ChargeMath.spend(chargeTicks(stack),seconds);
        if(remaining<0) {
            player.sendStatusMessage(new TextComponentTranslation("clockworkbottle.insufficient",seconds),true);
            return new ActionResult<ItemStack>(EnumActionResult.FAIL,stack);
        }
        PotionEffect previous=player.getActivePotionEffect(MobEffects.NIGHT_VISION);
        PotionEffect effect=new PotionEffect(MobEffects.NIGHT_VISION,(int)(seconds*20L),0);
        if(!player.isPotionApplicable(effect))return blocked(player,stack);
        player.removePotionEffect(MobEffects.NIGHT_VISION);
        if(player.isPotionActive(MobEffects.NIGHT_VISION))return blocked(player,stack);
        player.addPotionEffect(effect);
        PotionEffect actual=player.getActivePotionEffect(MobEffects.NIGHT_VISION);
        if(actual==null || actual.getDuration()!=seconds*20L || actual.getAmplifier()!=0) {
            if(previous!=null && actual==null)player.addPotionEffect(previous);
            return blocked(player,stack);
        }
        state.setInteger("ChargeTicks",remaining);
        state.setInteger("NextSeconds",ChargeMath.next(seconds));
        state.setString("Owner",player.getUniqueID().toString());
        save(stack,state);
        return new ActionResult<ItemStack>(EnumActionResult.SUCCESS,stack);
    }
    private static ActionResult<ItemStack> blocked(EntityPlayer player,ItemStack stack) {
        player.sendStatusMessage(new TextComponentTranslation("clockworkbottle.blocked"),true);
        return new ActionResult<ItemStack>(EnumActionResult.FAIL,stack);
    }
    @SideOnly(Side.CLIENT)
    @Override public void addInformation(ItemStack stack,@Nullable World world,List<String> tooltip,ITooltipFlag flag) {
        int seconds=chargeTicks(stack)/20;
        tooltip.add(I18n.format("clockworkbottle.charge",String.format(java.util.Locale.ROOT,"%02d:%02d:%02d",seconds/3600,(seconds/60)%60,seconds%60)));
        tooltip.add(I18n.format("clockworkbottle.use"));
    }
}
