package dev.agentic.workshopchecks;

import java.util.UUID;
import com.mojang.authlib.GameProfile;
import dev.agentic.workshop.PlayerQuest;
import dev.agentic.workshop.TraderNpc;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.network.*;
import net.minecraft.server.management.PlayerInteractionManager;
import net.minecraft.util.EnumHand;
import net.minecraft.world.WorldServer;

/** Ordinary player data/respawn paths; stub connections are not real network evidence. */
public final class QuestChecks {
    private final WorldServer world;
    private int assertions;
    private final Item cobble=Item.getItemFromBlock(Blocks.COBBLESTONE);
    public QuestChecks(WorldServer world) {this.world=world;}
    private void require(boolean ok,String message) {
        assertions++;if(!ok)throw new IllegalStateException("WORKSHOP_ASSERTIONS_FAIL: Quest "+message);
    }
    private EntityPlayerMP player(String name) {
        return player(name,false);
    }
    private EntityPlayerMP player(String name,final boolean operator) {
        EntityPlayerMP p=new EntityPlayerMP(world.getMinecraftServer(),world,
            new GameProfile(UUID.nameUUIDFromBytes(name.getBytes(java.nio.charset.StandardCharsets.UTF_8)),name),new PlayerInteractionManager(world)) {
                @Override public boolean canUseCommand(int level,String command) {return operator && level<=2;}
            };
        p.connection=new NetHandlerPlayServer(world.getMinecraftServer(),new NetworkManager(EnumPacketDirection.SERVERBOUND),p);
        p.setPosition(-5,76,-4);p.setSneaking(true);return p;
    }
    private TraderNpc npc() {TraderNpc n=new TraderNpc(world);n.setPosition(-5,76,-5);return n;}
    private int count(EntityPlayerMP p,Item item) {
        int result=0;for(ItemStack stack:p.inventory.mainInventory)if(stack.getItem()==item)result+=stack.getCount();return result;
    }
    private void click(TraderNpc n,EntityPlayerMP p) {require(n.processInteract(p,EnumHand.MAIN_HAND),"interaction rejected");}
    private void save(EntityPlayerMP p) {world.getSaveHandler().getPlayerNBTManager().writePlayerData(p);}
    public int live() {
        TraderNpc first=npc(),second=npc();EntityPlayerMP one=player("QuestOne"),two=player("QuestTwo");
        one.getEntityData().getCompoundTag(net.minecraft.entity.player.EntityPlayer.PERSISTED_NBT_TAG); // Reads must not initialize quest.
        require(PlayerQuest.progress(one)==-1,"new player already accepted");
        one.inventory.setInventorySlotContents(0,new ItemStack(Blocks.COBBLESTONE,3));click(first,one);
        require(PlayerQuest.progress(one)==0 && count(one,cobble)==3 && count(one,Items.DIAMOND)==0,"accept consumed payment");
        require(PlayerQuest.progress(two)==-1,"cross-player acceptance leakage");click(first,two);
        two.inventory.setInventorySlotContents(0,new ItemStack(Blocks.DIRT,8));click(first,two);
        require(PlayerQuest.progress(two)==0 && count(two,Item.getItemFromBlock(Blocks.DIRT))==8,"wrong payment accepted");
        click(first,one);require(PlayerQuest.progress(one)==3 && count(one,cobble)==0,"partial delivery accounting");
        click(second,one);require(PlayerQuest.progress(one)==3,"empty delivery/second NPC reset progress");
        require(PlayerQuest.progress(two)==0,"cross-player progress leakage");
        one.inventory.setInventorySlotContents(0,new ItemStack(Blocks.COBBLESTONE,2));
        one.inventory.setInventorySlotContents(9,new ItemStack(Blocks.COBBLESTONE,10));click(second,one);
        require(count(one,cobble)==7 && count(one,Items.DIAMOND)==1,"multi-stack completion accounting");
        require(PlayerQuest.progress(one)==8,"completion persisted");
        click(first,one);click(second,one);
        require(count(one,cobble)==7 && count(one,Items.DIAMOND)==1,"repeat interaction granted reward or consumed payment");
        require(PlayerQuest.progress(two)==0 && count(two,Items.DIAMOND)==0,"cross-player reward leakage");
        // Full capacity blocks final payment, even if consuming cobble would later free a slot.
        for(int i=0;i<36;i++)two.inventory.setInventorySlotContents(i,new ItemStack(Blocks.DIRT,64));
        two.inventory.setInventorySlotContents(0,new ItemStack(Blocks.COBBLESTONE,8));click(first,two);
        require(PlayerQuest.progress(two)==0 && count(two,cobble)==8 && count(two,Items.DIAMOND)==0,"full inventory consumed completion");
        two.capabilities.isCreativeMode=true;click(first,two);
        require(PlayerQuest.progress(two)==0 && count(two,cobble)==8,"creative full inventory consumed completion");two.capabilities.isCreativeMode=false;
        two.inventory.setInventorySlotContents(1,new ItemStack(Items.DIAMOND,63));click(first,two);
        require(PlayerQuest.progress(two)==8 && count(two,cobble)==0 && count(two,Items.DIAMOND)==64,"reward merge accounting");
        EntityPlayerMP invalid=player("QuestInvalid");
        require(!first.processInteract(invalid,EnumHand.OFF_HAND) && PlayerQuest.progress(invalid)==-1,"offhand accepted");
        invalid.setPosition(100,76,100);
        require(!first.processInteract(invalid,EnumHand.MAIN_HAND) && PlayerQuest.progress(invalid)==-1,"distant accepted");
        invalid.setPosition(-5,76,-4);invalid.setSneaking(false);click(first,invalid);invalid.setSneaking(true);click(first,invalid);
        require(PlayerQuest.progress(invalid)==-1,"open merchant menu accepted quest");invalid.closeScreen();
        TraderNpc dead=npc();dead.setDead();
        require(!dead.processInteract(invalid,EnumHand.MAIN_HAND) && PlayerQuest.progress(invalid)==-1,"dead NPC accepted");
        invalid.setHealth(0);
        require(!first.processInteract(invalid,EnumHand.MAIN_HAND) && PlayerQuest.progress(invalid)==-1,"dead player accepted");
        // Exercise the actual pinned player replacement method, not a manually fired clone event.
        EntityPlayerMP respawn=player("QuestOne");respawn.copyFrom(one,false);
        require(PlayerQuest.progress(respawn)==8,"death respawn lost completion");
        respawn.inventory.clear();respawn.inventory.setInventorySlotContents(0,new ItemStack(Blocks.COBBLESTONE,8));click(second,respawn);
        require(count(respawn,Items.DIAMOND)==0 && count(respawn,cobble)==8,"respawn allowed duplicate reward");
        // Two actual player files retain different stages across JVMs.
        EntityPlayerMP active=player("QuestPersistActive");click(first,active);
        active.inventory.setInventorySlotContents(0,new ItemStack(Blocks.COBBLESTONE,3));click(first,active);
        active.getEntityData().getCompoundTag(net.minecraft.entity.player.EntityPlayer.PERSISTED_NBT_TAG).setString("OtherModMarker","retained");
        EntityPlayerMP replacement=player("QuestPersistActive");replacement.copyFrom(active,true);
        require(PlayerQuest.progress(replacement)==3 && replacement.inventory.isEmpty(),"replacement lost partial progress");save(replacement);
        EntityPlayerMP completed=player("QuestPersistComplete");click(first,completed);
        completed.inventory.setInventorySlotContents(0,new ItemStack(Blocks.COBBLESTONE,12));click(second,completed);
        require(PlayerQuest.progress(completed)==8 && count(completed,cobble)==4 && count(completed,Items.DIAMOND)==1,"saved completion setup");save(completed);
        System.out.println("QUEST_LIVE_PASS assertions="+assertions);return assertions;
    }
    public int restore() {
        EntityPlayerMP active=player("QuestPersistActive"),completed=player("QuestPersistComplete");
        world.getSaveHandler().getPlayerNBTManager().readPlayerData(active);
        world.getSaveHandler().getPlayerNBTManager().readPlayerData(completed);
        active.setPosition(-5,76,-4);completed.setPosition(-5,76,-4);active.setSneaking(true);completed.setSneaking(true);
        require(PlayerQuest.progress(active)==3 && PlayerQuest.progress(completed)==8,"player disk stages differ");
        require("retained".equals(active.getEntityData().getCompoundTag(net.minecraft.entity.player.EntityPlayer.PERSISTED_NBT_TAG).getString("OtherModMarker")),"unrelated persisted data lost");
        require(count(completed,cobble)==4 && count(completed,Items.DIAMOND)==1,"player disk inventory differs");
        click(npc(),completed);require(count(completed,cobble)==4 && count(completed,Items.DIAMOND)==1,"disk completion allowed repeat reward");
        active.inventory.setInventorySlotContents(0,new ItemStack(Blocks.COBBLESTONE,7));click(npc(),active);
        require(PlayerQuest.progress(active)==8 && count(active,cobble)==2 && count(active,Items.DIAMOND)==1,"disk partial progress failed to resume");
        require("retained".equals(active.getEntityData().getCompoundTag(net.minecraft.entity.player.EntityPlayer.PERSISTED_NBT_TAG).getString("OtherModMarker")),"submission overwrote unrelated data");
        System.out.println("QUEST_RESTART_PASS assertions="+assertions);return assertions;
    }
    public int resetChecks() {
        int before=assertions;TraderNpc trader=npc();
        EntityPlayerMP operator=player("QuestResetOp",true),target=player("QuestResetDisk"),other=player("QuestResetOther");
        click(trader,operator);operator.inventory.setInventorySlotContents(0,new ItemStack(Blocks.COBBLESTONE,8));click(trader,operator);
        click(trader,target);target.inventory.setInventorySlotContents(0,new ItemStack(Blocks.COBBLESTONE,8));click(trader,target);
        click(trader,other);other.inventory.setInventorySlotContents(0,new ItemStack(Blocks.COBBLESTONE,3));click(trader,other);
        target.getEntityData().getCompoundTag(net.minecraft.entity.player.EntityPlayer.PERSISTED_NBT_TAG).setString("OtherModMarker","reset-retained");
        net.minecraft.command.ICommandManager commands=world.getMinecraftServer().getCommandManager();
        require(commands.getCommands().get("workshopreset") instanceof dev.agentic.workshop.ResetQuestCommand,"reset command registration");
        require(commands.executeCommand(target,"workshopreset")==0 && PlayerQuest.progress(target)==8,"non-operator reset permitted");
        require(commands.executeCommand(operator,"workshopreset")==1 && PlayerQuest.progress(operator)==-1 && count(operator,Items.DIAMOND)==1,"self reset/inventory conservation");
        operator.inventory.setInventorySlotContents(0,new ItemStack(Blocks.COBBLESTONE,8));click(trader,operator);click(trader,operator);
        require(PlayerQuest.progress(operator)==8 && count(operator,Items.DIAMOND)==2,"reset did not allow replay");
        java.util.List<EntityPlayerMP> players=world.getMinecraftServer().getPlayerList().getPlayers();players.add(target);
        try {
            require(commands.executeCommand(operator,"workshopreset QuestResetDisk")==1 && PlayerQuest.progress(target)==-1,"named target reset");
            require(commands.executeCommand(operator,"workshopreset MissingPlayer")==0 && PlayerQuest.progress(operator)==8,"missing target changed sender");
            require(commands.executeCommand(operator,"workshopreset QuestResetDisk extra")==0,"extra reset arguments accepted");
        }finally {players.remove(target);}
        require(PlayerQuest.progress(other)==3 && PlayerQuest.progress(operator)==8,"reset changed another player");
        require(count(target,Items.DIAMOND)==1 && "reset-retained".equals(target.getEntityData().getCompoundTag(net.minecraft.entity.player.EntityPlayer.PERSISTED_NBT_TAG).getString("OtherModMarker")),"reset wiped inventory/unrelated data");
        EntityPlayerMP respawn=player("QuestResetDisk");respawn.copyFrom(target,false);
        require(PlayerQuest.progress(respawn)==-1,"respawn resurrected reset quest");save(target);
        System.out.println("QUEST_RESET_LIVE_PASS assertions="+(assertions-before));return assertions-before;
    }
    public int resetRestore() {
        int before=assertions;EntityPlayerMP target=player("QuestResetDisk");world.getSaveHandler().getPlayerNBTManager().readPlayerData(target);
        target.setPosition(-5,76,-4);target.setSneaking(true);
        require(PlayerQuest.progress(target)==-1 && count(target,Items.DIAMOND)==1,"reset player disk state differs");
        require("reset-retained".equals(target.getEntityData().getCompoundTag(net.minecraft.entity.player.EntityPlayer.PERSISTED_NBT_TAG).getString("OtherModMarker")),"reset disk unrelated data lost");
        TraderNpc trader=npc();target.inventory.setInventorySlotContents(0,new ItemStack(Blocks.COBBLESTONE,8));click(trader,target);
        require(PlayerQuest.progress(target)==0 && count(target,cobble)==8,"reset disk acceptance consumed payment");click(trader,target);
        require(PlayerQuest.progress(target)==8 && count(target,Items.DIAMOND)==2 && count(target,cobble)==0,"reset disk replay failed");
        System.out.println("QUEST_RESET_RESTART_PASS assertions="+(assertions-before));return assertions-before;
    }
}
