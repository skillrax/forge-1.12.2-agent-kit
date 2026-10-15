package dev.agentic.workshop;
import net.minecraft.entity.EntityCreature;
import net.minecraft.entity.IMerchant;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.ai.*;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Items;
import net.minecraft.inventory.Container;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.village.MerchantRecipe;
import net.minecraft.village.MerchantRecipeList;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.PlayerEvent;

/** Custom passive entity; vanilla merchant containers own payment/output transactions. */
@Mod.EventBusSubscriber(modid=Workshop.ID)
public final class TraderNpc extends EntityCreature implements IMerchant {
    private EntityPlayer customer;
    private Container session;
    private MerchantRecipeList offers;
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent event) {
        if(event.player.world.isRemote)return;
        // Closing a merchant can drop items and mutate the world's entity list.
        for(net.minecraft.entity.Entity entity:new java.util.ArrayList<net.minecraft.entity.Entity>(event.player.world.loadedEntityList))if(entity instanceof TraderNpc) {
            TraderNpc npc=(TraderNpc)entity;
            if(npc.customer==event.player) {
                if(npc.session!=null && event.player.openContainer==npc.session)event.player.closeScreen();
                npc.setCustomer(null);
            }
        }
    }
    public TraderNpc(World world) {
        super(world);setSize(.6F,1.8F);setCustomNameTag("Test Trader");enablePersistence();
    }
    @Override protected void initEntityAI() {
        tasks.addTask(0,new EntityAISwimming(this));
        tasks.addTask(1,new EntityAIBase() {
            {setMutexBits(7);}
            @Override public boolean shouldExecute() {return getCustomer()!=null;}
            @Override public void startExecuting() {getNavigator().clearPath();}
            @Override public void updateTask() {
                EntityPlayer player=getCustomer();if(player!=null)getLookHelper().setLookPositionWithEntity(player,10,40);
            }
        });
        tasks.addTask(2,new EntityAIWander(this,1D,20));
        tasks.addTask(3,new EntityAIWatchClosest(this,EntityPlayer.class,8F));
        tasks.addTask(4,new EntityAILookIdle(this));
    }
    @Override protected void applyEntityAttributes() {
        super.applyEntityAttributes();getEntityAttribute(SharedMonsterAttributes.MAX_HEALTH).setBaseValue(20);
        getEntityAttribute(SharedMonsterAttributes.MOVEMENT_SPEED).setBaseValue(.25D);
    }
    @Override protected boolean canDespawn() {return false;}
    @Override public boolean processInteract(EntityPlayer player,EnumHand hand) {
        if(hand!=EnumHand.MAIN_HAND || !isEntityAlive() || !player.isEntityAlive() || player.world!=world)return false;
        if(world.isRemote)return true;
        if(getDistanceSq(player)>64)return false;
        if(player.isSneaking()) {PlayerQuest.interact(player);return true;}
        // Close an expired old session before handing the merchant to another player.
        if(customer!=null && getCustomer()==null) {
            EntityPlayer previous=customer;
            if(session!=null && previous.openContainer==session)previous.closeScreen();
            setCustomer(null);
        }
        if(getCustomer()!=null) {player.sendStatusMessage(new TextComponentString("Trader is busy."),true);return true;}
        setCustomer(player);getNavigator().clearPath();
        player.displayVillagerTradeGui(this);session=player.openContainer;return true;
    }
    @Override public EntityPlayer getCustomer() {
        return customer!=null && isEntityAlive() && customer.isEntityAlive() && customer.world==world
            && getDistanceSq(customer)<=64 && (session==null || customer.openContainer==session)?customer:null;
    }
    @Override public void setCustomer(EntityPlayer player) {customer=player;session=null;}
    @Override public MerchantRecipeList getRecipes(EntityPlayer player) {
        if(offers==null) {
            offers=new MerchantRecipeList();
            offers.add(new MerchantRecipe(new ItemStack(Items.APPLE,3),ItemStack.EMPTY,new ItemStack(Items.EMERALD),0,1000));
        }
        return offers;
    }
    @Override public void setRecipes(MerchantRecipeList recipes) {offers=recipes;}
    @Override public void useRecipe(MerchantRecipe recipe) {recipe.incrementToolUses();}
    @Override public void verifySellingItem(ItemStack stack) {}
    @Override public World getWorld() {return world;}
    @Override public BlockPos getPos() {return getPosition();}
    @Override public void writeEntityToNBT(NBTTagCompound tag) {
        super.writeEntityToNBT(tag);tag.setTag("TrialOffers",getRecipes(null).getRecipiesAsTags());
    }
    @Override public void readEntityFromNBT(NBTTagCompound tag) {
        super.readEntityFromNBT(tag);offers=tag.hasKey("TrialOffers",10)?new MerchantRecipeList(tag.getCompoundTag("TrialOffers")):null;
        setCustomer(null);
    }
}
