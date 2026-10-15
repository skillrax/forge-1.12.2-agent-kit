package dev.agentic.aegischecks;
import java.util.UUID;
import com.mojang.authlib.GameProfile;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.init.MobEffects;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.Item;
import net.minecraft.item.ItemFood;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.EnumPacketDirection;
import net.minecraft.network.NetHandlerPlayServer;
import net.minecraft.network.NetworkManager;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumHand;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLServerStartingEvent;
import net.minecraftforge.fml.common.registry.ForgeRegistries;
@Mod(modid="aegischecks", name="Aegis Acceptance Checks", version="1.0", dependencies="required-after:aegisapples")
public final class AegisChecks {
    private int assertions;
    private void require(boolean condition, String message) {
        assertions++;
        if (!condition) throw new IllegalStateException("AEGIS_ASSERTIONS_FAIL: " + message);
    }
    private FakePlayer player(WorldServer world, String name) {
        FakePlayer player = new FakePlayer(world, new GameProfile(UUID.nameUUIDFromBytes(name.getBytes(java.nio.charset.StandardCharsets.UTF_8)), name));
        // No real socket: queue vanilla player packets so normal potion/save code can execute.
        player.connection = new NetHandlerPlayServer(world.getMinecraftServer(), new NetworkManager(EnumPacketDirection.SERVERBOUND), player);
        return player;
    }
    private void food(FakePlayer player, int hunger, float saturation) {
        NBTTagCompound state = new NBTTagCompound();
        state.setInteger("foodLevel", hunger);
        state.setFloat("foodSaturationLevel", saturation);
        state.setFloat("foodExhaustionLevel", 0.0F);
        player.getFoodStats().readNBT(state);
    }
    private void effects(FakePlayer player) {
        Potion[] types={MobEffects.RESISTANCE,MobEffects.REGENERATION,MobEffects.ABSORPTION,MobEffects.FIRE_RESISTANCE,MobEffects.WATER_BREATHING};
        int[] levels={1,1,3,0,0};
        int[] ticks={6000,600,6000,6000,6000};
        for (int i=0;i<types.length;i++) {
            PotionEffect effect=player.getActivePotionEffect(types[i]);
            require(effect!=null,"Missing effect " + types[i].getRegistryName());
            require(effect.getAmplifier()==levels[i],"Wrong amplifier " + types[i].getRegistryName());
            require(effect.getDuration()==ticks[i],"Wrong duration " + types[i].getRegistryName());
        }
    }
    private InventoryCrafting recipeGrid() {
        InventoryCrafting grid=new InventoryCrafting(new Container(){
            @Override public boolean canInteractWith(net.minecraft.entity.player.EntityPlayer p){return false;}
        },3,3);
        for(int i=0;i<9;i++) grid.setInventorySlotContents(i,new ItemStack(i==4?Items.GOLDEN_APPLE:Item.getItemFromBlock(i%2==0?Blocks.DIAMOND_BLOCK:Blocks.GOLD_BLOCK),1,i==4?1:0));
        return grid;
    }
    @Mod.EventHandler public void check(FMLServerStartingEvent event) {
        if(!Boolean.getBoolean("aegis.checks"))return;
        WorldServer world=event.getServer().getWorld(0);
        Item item=ForgeRegistries.ITEMS.getValue(new ResourceLocation("aegisapples","aegis_apple"));
        require(item instanceof ItemFood,"Food registration missing");
        IRecipe recipe=ForgeRegistries.RECIPES.getValue(new ResourceLocation("aegisapples","aegis_apple"));
        require(recipe!=null,"Recipe missing");
        InventoryCrafting grid=recipeGrid();
        require(recipe.matches(grid,world),"Exact DGD/GAG/DGD recipe rejected");
        ItemStack output=recipe.getCraftingResult(grid);
        require(output.getItem()==item && output.getCount()==1,"Wrong recipe output");
        grid.setInventorySlotContents(4,new ItemStack(Items.GOLDEN_APPLE,1,0));
        require(!recipe.matches(grid,world),"Ordinary golden apple accepted");
        grid=recipeGrid();grid.setInventorySlotContents(0,new ItemStack(Blocks.GOLD_BLOCK));
        require(!recipe.matches(grid,world),"Wrong corner accepted");
        grid=recipeGrid();grid.setInventorySlotContents(1,new ItemStack(Blocks.DIAMOND_BLOCK));
        require(!recipe.matches(grid,world),"Wrong lateral block accepted");
        grid=recipeGrid();grid.setInventorySlotContents(8,ItemStack.EMPTY);
        require(!recipe.matches(grid,world),"Missing ingredient accepted");
        for(int[] state:new int[][]{{0,0},{7,2},{20,0},{20,20}}) {
            FakePlayer eater=player(world,"Aegis"+state[0]+"x"+state[1]);food(eater,state[0],state[1]);
            ItemStack stack=new ItemStack(item,2);eater.setHeldItem(EnumHand.MAIN_HAND,stack);
            require(item.onItemRightClick(world,eater,EnumHand.MAIN_HAND).getType()==EnumActionResult.SUCCESS,"Eating could not start at hunger "+state[0]);
            eater.resetActiveHand();
            require(eater.getFoodStats().getFoodLevel()==state[0],"Cancelled use changed food");
            require(eater.getActivePotionEffects().isEmpty(),"Cancelled use applied effects");
            ItemStack remaining=item.onItemUseFinish(stack,world,eater);
            require(remaining.getCount()==1,"Consumption did not remove exactly one apple");
            require(eater.getFoodStats().getFoodLevel()==20,"Hunger not full from "+state[0]);
            require(eater.getFoodStats().getSaturationLevel()==20.0F,"Saturation not full from "+state[0]);
            effects(eater);
            require(eater.getAbsorptionAmount()==16.0F,"Absorption hearts not granted");
            require(eater.getActivePotionEffects().size()==5,"Unexpected effect count");
            eater.setAbsorptionAmount(4.0F);
            ItemStack last=item.onItemUseFinish(remaining,world,eater);
            require(last.isEmpty(),"Second consumption did not exhaust stack");
            require(eater.getAbsorptionAmount()==16.0F,"Repeat eating did not replenish bounded absorption");
            effects(eater);
            NBTTagCompound saved=new NBTTagCompound();eater.writeEntityToNBT(saved);
            FakePlayer restored=player(world,"Restored"+state[0]+"x"+state[1]);restored.readEntityFromNBT(saved);
            require(restored.getFoodStats().getFoodLevel()==20,"Saved hunger lost");
            require(restored.getFoodStats().getSaturationLevel()==20.0F,"Saved saturation lost");
            require(restored.getAbsorptionAmount()==16.0F,"Saved absorption lost");
            effects(restored);
        }
        FakePlayer existing=player(world,"StrongerExisting");
        existing.addPotionEffect(new PotionEffect(MobEffects.RESISTANCE,7000,2));
        item.onItemUseFinish(new ItemStack(item),world,existing);
        require(existing.getActivePotionEffect(MobEffects.RESISTANCE).getAmplifier()==2,"Existing stronger effect downgraded");
        System.out.println("AEGIS_ASSERTIONS_PASS count="+assertions);
    }
}
