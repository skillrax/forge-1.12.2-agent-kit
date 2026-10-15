package dev.agentic.menuchecks;

import com.mojang.authlib.GameProfile;
import dev.agentic.playermenu.*;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.management.PlayerInteractionManager;
import net.minecraft.world.WorldServer;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.event.FMLServerStartedEvent;
import java.util.UUID;

@Mod(modid="menuchecks",name="Menu Checks (QA only)",version="1",dependencies="required-after:personalmenu")
public final class Checks {
    private int checks;
    private void ok(boolean b,String s){checks++;if(!b)throw new IllegalStateException("MENU_ASSERTIONS_FAIL: "+s);}
    private EntityPlayerMP player(MinecraftServer s,WorldServer w,String name){return new EntityPlayerMP(s,w,new GameProfile(UUID.nameUUIDFromBytes(name.getBytes(java.nio.charset.StandardCharsets.UTF_8)),name),new PlayerInteractionManager(w));}
    @Mod.EventHandler public void started(FMLServerStartedEvent event)throws Exception {
        MinecraftServer s=FMLCommonHandler.instance().getMinecraftServerInstance();if(!s.isDedicatedServer())return;
        WorldServer w=s.getWorld(0);EntityPlayerMP a=player(s,w,"MenuAlice"),b=player(s,w,"MenuBob");
        w.getSaveHandler().getPlayerNBTManager().readPlayerData(a);w.getSaveHandler().getPlayerNBTManager().readPlayerData(b);
        if(PersonalState.counter(a)==7){
            ok(PersonalState.enabled(a),"Alice toggle from previous JVM");ok(PersonalState.counter(b)==2 && !PersonalState.enabled(b),"Bob UUID file from previous JVM");
            System.out.println("MENU_SERVER_RESTART_PASS checks="+checks+" actualPlayerFiles=true syntheticIdentities=true");
        }else{
            ok(PersonalState.counter(a)==0 && !PersonalState.enabled(a),"fresh state");
            for(int i=0;i<7;i++)PersonalState.apply(a,1);PersonalState.apply(a,2);
            for(int i=0;i<2;i++)PersonalState.apply(b,1);
            ok(PersonalState.counter(a)==7 && PersonalState.enabled(a),"increment/toggle");
            ok(PersonalState.counter(b)==2 && !PersonalState.enabled(b),"two UUID isolation");
            ok(PersonalState.apply(a,255)==4 && PersonalState.counter(a)==7 && PersonalState.enabled(a),"invalid request unchanged");
            PersonalState.apply(b,3);ok(PersonalState.counter(b)==0 && !PersonalState.enabled(b) && PersonalState.counter(a)==7,"reset own state only");
            PersonalState.apply(b,1);PersonalState.apply(b,1);
            NBTTagCompound data=a.getEntityData().getCompoundTag(net.minecraft.entity.player.EntityPlayer.PERSISTED_NBT_TAG).getCompoundTag(PersonalState.KEY);
            data.setInteger("Counter",PersonalState.MAX);ok(PersonalState.apply(a,1)==5 && PersonalState.counter(a)==PersonalState.MAX,"cap without overflow");a.getEntityData().getCompoundTag(net.minecraft.entity.player.EntityPlayer.PERSISTED_NBT_TAG).getCompoundTag(PersonalState.KEY).setInteger("Counter",7);
            EntityPlayerMP clone=player(s,w,"MenuAlice");clone.copyFrom(a,false);ok(PersonalState.counter(clone)==7 && PersonalState.enabled(clone),"pinned clone persisted path");
            // Actual vanilla UUID player files, not an NBT in-memory roundtrip.
            w.getSaveHandler().getPlayerNBTManager().writePlayerData(a);w.getSaveHandler().getPlayerNBTManager().writePlayerData(b);w.saveAllChunks(true,null);
            System.out.println("MENU_SERVER_ACTIONS_PASS checks="+checks+" syntheticIdentities=true next=separateJVM");
        }
        System.out.println("MENU_LIVE_PASS");
    }
}
