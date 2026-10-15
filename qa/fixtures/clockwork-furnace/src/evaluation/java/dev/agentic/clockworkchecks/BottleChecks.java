package dev.agentic.clockworkchecks;
import java.util.UUID;
import com.mojang.authlib.GameProfile;
import io.netty.buffer.Unpooled;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Items;
import net.minecraft.init.MobEffects;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.EnumPacketDirection;
import net.minecraft.network.NetHandlerPlayServer;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.PacketBuffer;
import net.minecraft.network.play.server.SPacketSetSlot;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumHand;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLServerStartingEvent;
import net.minecraftforge.fml.common.registry.ForgeRegistries;
@Mod(modid="clockworkchecks",name="Clockwork Acceptance Checks",version="1.0",dependencies="required-after:clockworkbottle")
public final class BottleChecks {
    private int assertions;
    private void require(boolean ok,String message) {
        assertions++;
        if(!ok)throw new IllegalStateException("CLOCKWORK_ASSERTIONS_FAIL: "+message);
    }
    private static final class TestPlayer extends FakePlayer {
        int messages;
        TestPlayer(WorldServer world,String name) {
            super(world,new GameProfile(UUID.nameUUIDFromBytes(name.getBytes(java.nio.charset.StandardCharsets.UTF_8)),name));
            connection=new NetHandlerPlayServer(world.getMinecraftServer(),new NetworkManager(EnumPacketDirection.SERVERBOUND),this);
        }
        @Override public void sendStatusMessage(ITextComponent message,boolean actionBar) {messages++;}
    }
    private long ticks(ItemStack stack) {
        return stack.hasTagCompound()?stack.getTagCompound().getCompoundTag("ClockworkBottle").getLong("ChargeTicks"):0;
    }
    private void seed(ItemStack stack,long ticks) {
        NBTTagCompound root=new NBTTagCompound(),state=new NBTTagCompound();
        state.setLong("ChargeTicks",ticks);root.setTag("ClockworkBottle",state);stack.setTagCompound(root);
    }
    private void tick(TestPlayer player,int count) {
        // Actual vanilla InventoryPlayer -> ItemStack.updateAnimation -> Item.onUpdate path.
        for(int i=0;i<count;i++)player.inventory.decrementAnimations();
    }
    private void sequence(Item item,WorldServer world) {
        TestPlayer p=new TestPlayer(world,"ClickSequence");ItemStack bottle=new ItemStack(item);
        seed(bottle,20000);p.setHeldItem(EnumHand.MAIN_HAND,bottle);
        int stored=20000;
        for(int seconds:new int[]{15,30,60,120}) {
            require(item.onItemRightClick(world,p,EnumHand.MAIN_HAND).getType()==EnumActionResult.SUCCESS,"Successful sequence click rejected");
            stored-=seconds*20;
            require(ticks(bottle)==stored,"Wrong charge for "+seconds+" second click");
            require(p.getActivePotionEffect(MobEffects.NIGHT_VISION).getDuration()==seconds*20,"Duration was added instead of set");
            require(p.getActivePotionEffect(MobEffects.NIGHT_VISION).getAmplifier()==0,"Wrong Night Vision amplifier");
        }
        // The next 240-second request cannot fit in this temporarily reduced balance.
        bottle.getTagCompound().getCompoundTag("ClockworkBottle").setInteger("ChargeTicks",1000);
        NBTTagCompound before=bottle.getTagCompound().copy();int duration=p.getActivePotionEffect(MobEffects.NIGHT_VISION).getDuration();
        require(item.onItemRightClick(world,p,EnumHand.MAIN_HAND).getType()==EnumActionResult.FAIL,"Insufficient request succeeded");
        require(before.equals(bottle.getTagCompound()),"Failed click changed charge/sequence");
        require(p.getActivePotionEffect(MobEffects.NIGHT_VISION).getDuration()==duration,"Failed click changed existing effect");
        require(p.messages==1,"Insufficient message missing");
        p.removePotionEffect(MobEffects.NIGHT_VISION);
        require(item.onItemRightClick(world,p,EnumHand.MAIN_HAND).getType()==EnumActionResult.SUCCESS,"Reset after removed/expired effect failed");
        require(ticks(bottle)==700 && p.getActivePotionEffect(MobEffects.NIGHT_VISION).getDuration()==300,"Reset did not spend/set 15 seconds");
        // An inventory tick notices expiry/removal and clears the per-bottle series.
        p.removePotionEffect(MobEffects.NIGHT_VISION);tick(p,1);
        p.addPotionEffect(new PotionEffect(MobEffects.NIGHT_VISION,1200,0));
        require(item.onItemRightClick(world,p,EnumHand.MAIN_HAND).getType()==EnumActionResult.SUCCESS,"Expired deadline reset failed");
        require(ticks(bottle)==401 && p.getActivePotionEffect(MobEffects.NIGHT_VISION).getDuration()==300,"External effect revived cleared sequence");
        p.removePotionEffect(MobEffects.NIGHT_VISION);
        for(int i=0;i<8;i++)p.addPotionEffect(new PotionEffect(MobEffects.NIGHT_VISION,600,0));
        bottle.getTagCompound().getCompoundTag("ClockworkBottle").setLong("NextSeconds",Long.MAX_VALUE);
        before=bottle.getTagCompound().copy();
        require(item.onItemRightClick(world,p,EnumHand.MAIN_HAND).getType()==EnumActionResult.FAIL,"Overflow request accepted");
        require(before.equals(bottle.getTagCompound()),"Overflow failure modified storage");
        TestPlayer empty=new TestPlayer(world,"EmptyBottle");empty.setHeldItem(EnumHand.MAIN_HAND,new ItemStack(item));
        require(item.onItemRightClick(world,empty,EnumHand.MAIN_HAND).getType()==EnumActionResult.FAIL,"Empty bottle granted effect");
        require(!empty.isPotionActive(MobEffects.NIGHT_VISION) && empty.messages==1,"Empty failure did not notify or gave effect");
        ItemStack offhand=new ItemStack(item);seed(offhand,4000);empty.setHeldItem(EnumHand.OFF_HAND,offhand);
        require(item.onItemRightClick(world,empty,EnumHand.OFF_HAND).getType()==EnumActionResult.PASS,"Offhand use accepted");
        require(ticks(offhand)==4000,"Offhand charge spent");
        TestPlayer recipient=new TestPlayer(world,"OtherHolder");recipient.setHeldItem(EnumHand.MAIN_HAND,bottle);
        recipient.addPotionEffect(new PotionEffect(MobEffects.NIGHT_VISION,1200,0));
        require(item.onItemRightClick(world,recipient,EnumHand.MAIN_HAND).getType()==EnumActionResult.SUCCESS,"New holder inherited invalid sequence");
        require(ticks(bottle)==101 && recipient.getActivePotionEffect(MobEffects.NIGHT_VISION).getDuration()==300,"New holder failed 15-second reset");
    }
    @Mod.EventHandler public void check(FMLServerStartingEvent event) throws Exception {
        if(!Boolean.getBoolean("clockwork.checks"))return;
        WorldServer world=event.getServer().getWorld(0);
        Item item=ForgeRegistries.ITEMS.getValue(new ResourceLocation("clockworkbottle","clockwork_bottle"));
        require(item!=null && item.getItemStackLimit()==1,"Nonstackable bottle registration missing");
        IRecipe recipe=ForgeRegistries.RECIPES.getValue(new ResourceLocation("clockworkbottle","clockwork_bottle"));
        require(recipe!=null,"Recipe missing");
        InventoryCrafting grid=new InventoryCrafting(new Container(){@Override public boolean canInteractWith(EntityPlayer p){return true;}},3,3);
        for(int i=0;i<9;i++)grid.setInventorySlotContents(i,new ItemStack(i%3==1?(i==1?Items.CLOCK:i==4?Items.GOLD_INGOT:Items.GLASS_BOTTLE):Items.DIAMOND));
        require(recipe.matches(grid,world) && recipe.getCraftingResult(grid).getItem()==item,"Chosen recipe failed");
        grid.setInventorySlotContents(8,new ItemStack(Items.IRON_INGOT));require(!recipe.matches(grid,world),"Wrong recipe ingredient accepted");
        grid.setInventorySlotContents(8,ItemStack.EMPTY);require(!recipe.matches(grid,world),"Recipe missing sixth diamond accepted");
        for(int i=0;i<9;i++)grid.setInventorySlotContents(i,ItemStack.EMPTY);
        grid.setInventorySlotContents(0,new ItemStack(Items.GLASS_BOTTLE));grid.setInventorySlotContents(4,new ItemStack(Items.CLOCK));grid.setInventorySlotContents(8,new ItemStack(Items.GOLD_INGOT));
        require(!recipe.matches(grid,world),"Old three-ingredient recipe accepted");
        TestPlayer p=new TestPlayer(world,"InventoryTicks");ItemStack first=new ItemStack(item),second=new ItemStack(item),offhand=new ItemStack(item),armor=new ItemStack(item);
        p.inventory.mainInventory.set(0,first);p.inventory.mainInventory.set(9,second);p.inventory.offHandInventory.set(0,offhand);p.inventory.armorInventory.set(0,armor);
        tick(p,19);require(ticks(first)==19 && ticks(second)==19 && ticks(offhand)==19,"Inventory tick cadence wrong");
        require(ticks(armor)==0,"Armor charged");
        tick(p,1);require(ticks(first)==20,"One second did not equal 20 ticks");
        p.inventory.mainInventory.set(0,ItemStack.EMPTY);tick(p,20);
        item.onUpdate(first,world,p,0,true);require(ticks(first)==20,"Detached/chest stack charged");
        require(ticks(second)==40 && ticks(offhand)==40,"Independent inventory bottles did not charge");
        EntityItem dropped=new EntityItem(world,0,100,0,first);
        for(int i=0;i<20;i++)dropped.onUpdate();require(ticks(first)==20,"Dropped bottle charged");
        TestPlayer other=new TestPlayer(world,"TransferTicks");other.inventory.mainInventory.set(0,dropped.getItem());tick(other,20);
        require(ticks(other.inventory.mainInventory.get(0))==40,"Transferred charge lost or duplicated");
        require(ticks(second)==40,"Other player's tick charged first player");
        seed(first,4319999);other.inventory.mainInventory.set(0,first);tick(other,30);require(ticks(first)==4320000,"60-hour cap incorrect");
        seed(first,Long.MAX_VALUE);tick(other,1);require(ticks(first)==4320000,"Corrupt oversized save overflowed");
        seed(first,Long.MIN_VALUE);tick(other,1);require(ticks(first)==1,"Corrupt negative save not repaired");
        other.isDead=true;tick(other,20);require(ticks(first)==1,"Dead player charged");other.isDead=false;
        // Encode and decode the actual vanilla inventory packet that carries the tooltip's NBT.
        SPacketSetSlot packet=new SPacketSetSlot(0,36,second);PacketBuffer buffer=new PacketBuffer(Unpooled.buffer());
        try {packet.writePacketData(buffer);require(buffer.readByte()==0 && buffer.readShort()==36,"Wrong inventory packet slot");require(ticks(buffer.readItemStack())==40,"Inventory packet lost tooltip charge");}finally{buffer.release();}
        sequence(item,world);
        TestPlayer saved=new TestPlayer(world,"RestartBottle");
        if("save".equals(System.getProperty("clockwork.phase"))) {
            ItemStack bottle=new ItemStack(item);saved.inventory.mainInventory.set(0,bottle);tick(saved,319);
            require(ticks(bottle)==319,"Fractional charge before persistence wrong");
            require(item.onItemRightClick(world,saved,EnumHand.MAIN_HAND).getType()==EnumActionResult.SUCCESS,"Save-stage use failed");
            require(ticks(bottle)==19,"Save-stage fractional remainder wrong");
            world.getSaveHandler().getPlayerNBTManager().writePlayerData(saved);
            require(world.getSaveHandler().getPlayerNBTManager().readPlayerData(new TestPlayer(world,"RestartBottle"))!=null,"Vanilla player file not written");
        } else {
            require(event.getServer().getPlayerList().readPlayerDataFromFile(saved)!=null,"Prior-process player file missing");
            ItemStack bottle=saved.inventory.mainInventory.get(0);
            require(bottle.getItem()==item && ticks(bottle)==19,"Restart lost charge/fraction or applied catch-up");
            require(bottle.getTagCompound().getCompoundTag("ClockworkBottle").getInteger("NextSeconds")==30,"Restart lost click sequence");
            tick(saved,1);require(ticks(bottle)==20,"Restart remainder failed next tick");
            NBTTagCompound before=bottle.getTagCompound().copy();
            require(item.onItemRightClick(world,saved,EnumHand.MAIN_HAND).getType()==EnumActionResult.FAIL,"Restart insufficient charge succeeded");
            require(before.equals(bottle.getTagCompound()),"Restart failure advanced sequence");
            bottle.getTagCompound().getCompoundTag("ClockworkBottle").setInteger("ChargeTicks",1000);
            require(item.onItemRightClick(world,saved,EnumHand.MAIN_HAND).getType()==EnumActionResult.SUCCESS,"Post-reload next click failed");
            require(ticks(bottle)==400 && saved.getActivePotionEffect(MobEffects.NIGHT_VISION).getDuration()==600,"Offline/restart reset an unexpired 30-second sequence");
        }
        System.out.println("CLOCKWORK_ASSERTIONS_PASS phase="+System.getProperty("clockwork.phase")+" count="+assertions);
    }
}
