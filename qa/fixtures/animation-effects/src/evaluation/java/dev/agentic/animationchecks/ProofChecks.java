package dev.agentic.animationchecks;

import dev.agentic.animation.*;
import net.minecraft.init.Blocks;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldServer;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLServerStartedEvent;

@Mod(modid="animationchecks",name="Isolated Animation Checks",version="1",dependencies="required-after:animationproof")
public final class ProofChecks {
    public static final BlockPos POS=new BlockPos(0,4,0);
    public static void require(boolean ok,String why){if(!ok)throw new IllegalStateException("ANIMATION_ASSERTIONS_FAIL "+why);}
    @Mod.EventHandler public void started(FMLServerStartedEvent e){
        WorldServer w=net.minecraftforge.common.DimensionManager.getWorld(0);w.getChunk(0,0);
        w.getGameRules().setOrCreateGameRule("doDaylightCycle","false");w.getGameRules().setOrCreateGameRule("doMobSpawning","false");w.setWorldTime(6000);
        w.setBlockState(POS.down(),Blocks.STONEBRICK.getDefaultState(),3);w.setBlockState(POS,AnimationProof.FIXTURE.getDefaultState(),3);
        AnimationTile t=(AnimationTile)w.getTileEntity(POS);require(t!=null && t.active,"tile/default on");
        require(t.motionTime(.75F)-t.motionTime(.25F)==.5,"partial-tick interpolation");
        t.toggle();require(!t.active && t.motionTime(.75F)==t.motionTime(.25F),"pause freezes clock");
        NBTTagCompound n=t.writeToNBT(new NBTTagCompound());AnimationTile copy=new AnimationTile();copy.readFromNBT(n);copy.setWorld(w);
        require(!copy.active && copy.motionTime(0)==t.motionTime(0),"paused NBT roundtrip");
        t.toggle();require(t.active,"resume");
        System.out.println("ANIMATION_SERVER_PASS tile=true interpolation=true toggle=true pausedNbt=true clientClassesNotLoaded=true");
    }
}
