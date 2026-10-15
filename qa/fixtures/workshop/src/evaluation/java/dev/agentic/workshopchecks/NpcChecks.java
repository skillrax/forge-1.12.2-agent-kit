package dev.agentic.workshopchecks;
import java.util.ArrayList;
import java.util.UUID;
import com.mojang.authlib.GameProfile;
import dev.agentic.workshop.TraderNpc;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityList;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.inventory.*;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.*;
import net.minecraft.server.management.PlayerInteractionManager;
import net.minecraft.util.EnumHand;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldServer;
import net.minecraftforge.fml.common.registry.EntityRegistry;

public final class NpcChecks {
    private final WorldServer world;
    private TraderNpc walker;
    private int assertions;
    private double previousX,previousZ,distance;
    public NpcChecks(WorldServer world) {this.world=world;}
    private void require(boolean ok,String message) {
        assertions++;if(!ok)throw new IllegalStateException("WORKSHOP_ASSERTIONS_FAIL: NPC "+message);
    }
    private EntityPlayerMP player(String name) {
        EntityPlayerMP p=new EntityPlayerMP(world.getMinecraftServer(),world,
            new GameProfile(UUID.nameUUIDFromBytes(name.getBytes(java.nio.charset.StandardCharsets.UTF_8)),name),new PlayerInteractionManager(world));
        p.connection=new NetHandlerPlayServer(world.getMinecraftServer(),new NetworkManager(EnumPacketDirection.SERVERBOUND),p);
        p.setPosition(-5,76,-4);return p;
    }
    private TraderNpc create() {
        Entity entity=EntityList.createEntityByIDFromName(new ResourceLocation("kitworkshop","test_trader"),world);
        require(entity instanceof TraderNpc,"registry constructor failed");
        TraderNpc trader=(TraderNpc)entity;trader.setPosition(-5,76,-5);return trader;
    }
    private int count(IInventory inventory,net.minecraft.item.Item item) {
        int n=0;for(int i=0;i<inventory.getSizeInventory();i++)if(inventory.getStackInSlot(i).getItem()==item)n+=inventory.getStackInSlot(i).getCount();return n;
    }
    private int appleDrops() {
        int n=0;for(Entity entity:world.loadedEntityList)if(entity instanceof EntityItem) {
            ItemStack stack=((EntityItem)entity).getItem();if(stack.getItem()==Items.APPLE)n+=stack.getCount();
        }return n;
    }
    public int liveStart() {
        // Vanilla skips non-player entity ticks unless the surrounding32-block area is loaded.
        // QA loads a bounded test area; production NPCs never request chunk loads.
        for(int x=-4;x<=3;x++)for(int z=-4;z<=3;z++)world.getChunk(x,z);
        for(Entity entity:new ArrayList<Entity>(world.loadedEntityList))if(entity instanceof TraderNpc)entity.setDead();
        for(int x=-20;x<=20;x++)for(int z=-20;z<=20;z++)world.setBlockState(new BlockPos(x,75,z),Blocks.STONE.getDefaultState(),2);
        require(EntityRegistry.instance().lookupModSpawn(TraderNpc.class,false)!=null,"Forge tracking registration missing");
        TraderNpc trader=create();trader.setNoAI(true);require(world.spawnEntity(trader),"world spawn failed");
        EntityPlayerMP one=player("NpcOne"),two=player("NpcTwo");
        require(trader.processInteract(one,EnumHand.MAIN_HAND) && one.openContainer instanceof ContainerMerchant,"merchant interaction failed");
        ContainerMerchant menu=(ContainerMerchant)one.openContainer;IInventory input=menu.getMerchantInventory();
        require(trader.getCustomer()==one && menu.canInteractWith(one) && !menu.canInteractWith(two),"customer authorization");
        trader.processInteract(two,EnumHand.MAIN_HAND);
        require(trader.getCustomer()==one && two.openContainer==two.inventoryContainer,"second player stole session");
        input.setInventorySlotContents(0,new ItemStack(Items.APPLE,2));menu.slotClick(2,0,ClickType.PICKUP,one);
        require(input.getStackInSlot(2).isEmpty() && input.getStackInSlot(0).getCount()==2 && one.inventory.getItemStack().isEmpty(),"insufficient payment");
        input.setInventorySlotContents(0,new ItemStack(Blocks.DIRT,3));
        require(input.getStackInSlot(2).isEmpty(),"wrong payment accepted");
        input.setInventorySlotContents(0,new ItemStack(Items.APPLE,3));menu.slotClick(2,0,ClickType.PICKUP,one);
        require(input.getStackInSlot(0).isEmpty() && one.inventory.getItemStack().getItem()==Items.EMERALD
            && one.inventory.getItemStack().getCount()==1,"normal trade accounting");
        require(trader.getRecipes(one).get(0).getToolUses()==1,"trade use counter");one.inventory.setItemStack(ItemStack.EMPTY);
        input.setInventorySlotContents(0,new ItemStack(Items.APPLE,9));menu.slotClick(2,0,ClickType.QUICK_MOVE,one);
        require(input.getStackInSlot(0).isEmpty() && count(one.inventory,Items.EMERALD)==3 && trader.getRecipes(one).get(0).getToolUses()==4,"shift trade accounting");
        for(int i=0;i<36;i++)one.inventory.setInventorySlotContents(i,new ItemStack(Blocks.DIRT,64));
        input.setInventorySlotContents(0,new ItemStack(Items.APPLE,3));menu.slotClick(2,0,ClickType.QUICK_MOVE,one);
        require(input.getStackInSlot(0).getCount()==3 && input.getStackInSlot(2).getCount()==1
            && trader.getRecipes(one).get(0).getToolUses()==4,"full inventory consumed trade");
        int before=appleDrops();one.closeScreen();
        require(trader.getCustomer()==null && appleDrops()-before==3,"close did not return unused payment");
        require(trader.processInteract(two,EnumHand.MAIN_HAND) && trader.getCustomer()==two,"customer handoff");
        two.setPosition(100,76,100);two.onUpdate();
        require(two.openContainer==two.inventoryContainer && trader.getCustomer()==null,"range invalidation");
        two.setPosition(-5,76,-4);trader.processInteract(two,EnumHand.MAIN_HAND);two.setPosition(100,76,100);
        trader.processInteract(one,EnumHand.MAIN_HAND);two.onUpdate();
        require(two.openContainer==two.inventoryContainer && trader.getCustomer()==one,"expired customer handoff closed new session");
        one.closeScreen();
        trader.processInteract(two,EnumHand.MAIN_HAND); // Out of range: must not reserve the merchant.
        require(trader.getCustomer()==null,"distant interaction reserved NPC");
        two.setPosition(-5,76,-4);trader.processInteract(two,EnumHand.MAIN_HAND);
        ((ContainerMerchant)two.openContainer).getMerchantInventory().setInventorySlotContents(0,new ItemStack(Items.APPLE,2));
        int logoutDrops=appleDrops();net.minecraftforge.fml.common.FMLCommonHandler.instance().firePlayerLoggedOut(two);
        require(two.openContainer==two.inventoryContainer && trader.getCustomer()==null && appleDrops()-logoutDrops==2,"logout payment/session cleanup");
        one.setPosition(-5,76,-4);one.inventory.clear();trader.processInteract(one,EnumHand.MAIN_HAND);
        NBTTagCompound tag=new NBTTagCompound();require(trader.writeToNBTOptional(tag),"entity NBT ID absent");
        Entity restored=EntityList.createEntityFromNBT(tag,world);
        require(restored instanceof TraderNpc && ((TraderNpc)restored).getRecipes(null).get(0).getToolUses()==4
            && ((TraderNpc)restored).getCustomer()==null,"entity offer round-trip/session persisted");
        trader.setDead();one.onUpdate();require(one.openContainer==one.inventoryContainer,"dead NPC session remained open");
        walker=create();walker.getRNG().setSeed(7L);require(world.spawnEntity(walker),"wandering NPC spawn failed");
        previousX=walker.posX;previousZ=walker.posZ;
        // Give the persistent NPC one real trade before exercising free AI ticks.
        EntityPlayerMP saved=player("NpcPersist");walker.processInteract(saved,EnumHand.MAIN_HAND);
        ContainerMerchant savedMenu=(ContainerMerchant)saved.openContainer;
        savedMenu.getMerchantInventory().setInventorySlotContents(0,new ItemStack(Items.APPLE,3));
        savedMenu.slotClick(2,0,ClickType.PICKUP,saved);saved.inventory.setItemStack(ItemStack.EMPTY);saved.closeScreen();
        require(walker.getRecipes(null).get(0).getToolUses()==1,"persistent NPC trade setup");
        System.out.println("NPC_STATIC_PASS assertions="+assertions);return assertions;
    }
    public void tick() {
        require(walker.isEntityAlive(),"NPC died during AI trial");
        distance+=Math.hypot(walker.posX-previousX,walker.posZ-previousZ);previousX=walker.posX;previousZ=walker.posZ;
    }
    public int finish() {
        require(walker.ticksExisted>=135,"NPC did not receive actual entity ticks");
        require(distance>.5D,"wandering did not move during actual ticks: age="+walker.ticksExisted+", ground="+walker.onGround+", noAI="+walker.isAIDisabled()+", idle="+walker.getIdleTime());
        walker.setNoAI(true);System.out.println("NPC_LIVE_PASS distance="+distance+" assertions="+assertions);return assertions;
    }
    public NBTTagCompound snapshot() {NBTTagCompound tag=new NBTTagCompound();walker.writeToNBTOptional(tag);return tag;}
    public int restore(NBTTagCompound expected) {
        world.getChunk(new BlockPos(expected.getTagList("Pos",6).getDoubleAt(0),expected.getTagList("Pos",6).getDoubleAt(1),expected.getTagList("Pos",6).getDoubleAt(2)));
        UUID uuid=expected.getUniqueId("UUID");TraderNpc npc=null;
        for(Entity entity:world.loadedEntityList)if(entity instanceof TraderNpc && entity.getUniqueID().equals(uuid))npc=(TraderNpc)entity;
        require(npc!=null,"NPC not restored from chunk disk");
        require(npc.getRecipes(null).get(0).getToolUses()==1 && npc.getRecipes(null).get(0).getItemToBuy().getCount()==3,"saved offer/use count differs");
        require(npc.getCustomer()==null && npc.getCustomNameTag().equals("Test Trader"),"saved name/session invalid");
        require(npc.getDistanceSq(expected.getTagList("Pos",6).getDoubleAt(0),expected.getTagList("Pos",6).getDoubleAt(1),expected.getTagList("Pos",6).getDoubleAt(2))<.001,"saved location differs");
        npc.setNoAI(false);EntityPlayerMP p=player("NpcReload");p.setPosition(npc.posX,npc.posY,npc.posZ+1);
        require(npc.processInteract(p,EnumHand.MAIN_HAND) && p.openContainer instanceof ContainerMerchant,"restored interaction failed");
        ContainerMerchant menu=(ContainerMerchant)p.openContainer;menu.getMerchantInventory().setInventorySlotContents(0,new ItemStack(Items.APPLE,3));
        menu.slotClick(2,0,ClickType.PICKUP,p);
        require(p.inventory.getItemStack().getItem()==Items.EMERALD && npc.getRecipes(null).get(0).getToolUses()==2,"restored trade failed");
        p.inventory.setItemStack(ItemStack.EMPTY);p.closeScreen();System.out.println("NPC_RESTART_PASS assertions="+assertions);return assertions;
    }
}
