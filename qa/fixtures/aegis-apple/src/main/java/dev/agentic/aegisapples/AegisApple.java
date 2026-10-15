package dev.agentic.aegisapples;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.MobEffects;
import net.minecraft.item.ItemFood;
import net.minecraft.item.ItemStack;
import net.minecraft.potion.PotionEffect;
import net.minecraft.world.World;
public final class AegisApple extends ItemFood {
    public AegisApple() {
        // Vanilla caps saturation to hunger: 20 hunger and 40 saturation gained fill both from empty.
        super(20, 1.0F, false);
        setAlwaysEdible();
    }
    @Override
    protected void onFoodEaten(ItemStack stack, World world, EntityPlayer player) {
        if (world.isRemote) return;
        player.addPotionEffect(new PotionEffect(MobEffects.RESISTANCE, 6000, 1));
        player.addPotionEffect(new PotionEffect(MobEffects.REGENERATION, 600, 1));
        player.addPotionEffect(new PotionEffect(MobEffects.ABSORPTION, 6000, 3));
        player.addPotionEffect(new PotionEffect(MobEffects.FIRE_RESISTANCE, 6000, 0));
        player.addPotionEffect(new PotionEffect(MobEffects.WATER_BREATHING, 6000, 0));
    }
}
