package dev.agentic.networkkitchecks;
import com.example.networkstarter.*;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldServer;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.tileentity.TileEntity;
import net.minecraftforge.common.DimensionManager;
import net.minecraftforge.energy.*;
import net.minecraftforge.fluids.*;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLServerStartedEvent;

@Mod(modid="networkkitchecks",name="Isolated RF Starter Checks",version="1",dependencies="required-after:networkstarter")
public final class StarterChecks {
    private int checks;
    private void require(boolean ok,String why){checks++;if(!ok)throw new IllegalStateException("RF_ASSERTIONS_FAIL: starter: "+why);}
    private IEnergyStorage port(TileEntity t,EnumFacing f){return t.getCapability(CapabilityEnergy.ENERGY,f);}
    private void next(WorldServer w){w.getWorldInfo().setWorldTotalTime(w.getTotalWorldTime()+1);}
    private StorageTile battery(WorldServer w,BlockPos p){w.setBlockState(p,Content.STORAGE.getDefaultState(),3);StorageTile t=(StorageTile)w.getTileEntity(p);NBTTagCompound n=t.writeToNBT(new NBTTagCompound());n.setInteger("RF",200000);t.readFromNBT(n);for(EnumFacing f:EnumFacing.values())while(t.role(f)!=2)t.control(f.getIndex());return t;}
    @Mod.EventHandler public void start(FMLServerStartedEvent e){
        if(!net.minecraftforge.fml.common.FMLCommonHandler.instance().getMinecraftServerInstance().isDedicatedServer())return;
        WorldServer w=DimensionManager.getWorld(0);BlockPos p=new BlockPos(8,80,8);w.getChunk(0,0);
        w.setBlockState(p,Content.GENERATOR.getDefaultState(),3);GeneratorTile g=(GeneratorTile)w.getTileEntity(p);g.setInventorySlotContents(1,new ItemStack(Content.UPGRADE));g.fluids().fill(new FluidStack(FluidRegistry.LAVA,1),true);next(w);
        IEnergyStorage gp=port(g,EnumFacing.EAST);require(gp.extractEnergy(2000,true)==1333 && g.lava()==1 && g.credit()==0,"Lava simulation is pure");require(gp.extractEnergy(2000,false)==1333 && g.credit()==1 && g.lava()==0,"Upgraded fractional fuel ledger");
        g.fluids().fill(new FluidStack(FluidRegistry.LAVA,2),true);next(w);long before=g.credit();require(gp.extractEnergy(2000,false)==2000 && before+8000-g.credit()==6000,"Settings preserve 2x output / 1.5x fuel-time economy");
        GeneratorTile saved=new GeneratorTile();saved.readFromNBT(g.writeToNBT(new NBTTagCompound()));require(saved.credit()==g.credit() && saved.upgraded(),"Saved fractional reserve and upgrade");w.setBlockToAir(p);
        StorageTile s=battery(w,p);next(w);IEnergyStorage west=port(s,EnumFacing.WEST),east=port(s,EnumFacing.EAST);
        require(west.extractEnergy(12000,false)==12000 && east.extractEnergy(20000,false)==8000 && west.extractEnergy(1,false)==0,"Combined all-face external budget");
        s.control(EnumFacing.WEST.getIndex());require(west.extractEnergy(1,false)==0,"Held face view follows configuration");w.setBlockToAir(p);
        w.setBlockState(p,Content.CABLE.getDefaultState(),3);CableTile c=(CableTile)w.getTileEntity(p);
        StorageTile[] sources={battery(w,p.west()),battery(w,p.up()),battery(w,p.down())};
        w.setBlockState(p.east(),Content.SINK.getDefaultState(),3);SinkTile sink=(SinkTile)w.getTileEntity(p.east());sink.control(100+100000);next(w);for(StorageTile source:sources)Network.push(source);
        int rf=0;for(StorageTile source:sources)rf+=source.stored();require(sink.consumed==60000 && rf==540000 && c.remaining()==0,"Renamed network sources/path conservation and shared budget");require(port(c,EnumFacing.NORTH).getEnergyStored()==0,"No cable RF buffer");
        for(EnumFacing f:EnumFacing.values())w.setBlockToAir(p.offset(f));w.setBlockToAir(p);
        // New extraction safeguards: retain pending unload during dirty marking and block held ports.
        BlockPos remote=new BlockPos(6145,80,6152);w.getChunk(remote);s=battery(w,remote);east=port(s,EnumFacing.EAST);Chunk owner=w.getChunk(remote);w.getChunkProvider().queueUnload(owner);require(owner.unloadQueued,"Remote owner queued");
        s.markDirty();require(owner.unloadQueued && Network.loaded(w,remote)==null,"Dirty marking/lookup does not cancel pending unload");require(east.extractEnergy(1000,false)==0 && s.stored()==200000,"Held FE port rejects queued owner");
        owner.unloadQueued=false;w.setBlockToAir(remote);require(east.extractEnergy(1000,false)==0,"Held FE port rejects replaced tile");
        System.out.println("RF_STARTER_ADAPTATION_PASS assertions="+checks+" settings=true held-ports=true safe-dirty=true");
    }
}
