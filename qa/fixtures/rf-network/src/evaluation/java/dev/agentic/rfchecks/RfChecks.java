package dev.agentic.rfchecks;

import java.util.*;
import dev.agentic.rfnetwork.*;
import net.minecraft.block.Block;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.*;
import net.minecraft.world.*;
import net.minecraft.world.chunk.Chunk;
import net.minecraftforge.common.*;
import net.minecraftforge.energy.*;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.fluids.*;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.*;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

/** Isolated QA only. Synchronous accounting followed by real-tick partial chunk unload/save/reload. */
@Mod(modid="rfchecks",name="RF Network Isolated Checks",version="1",dependencies="required-after:rfnetwork")
public final class RfChecks {
    private WorldServer w;
    private int checks,age,phase;
    private boolean done;
    private ForgeChunkManager.Ticket ticket;
    private BlockPos gpos,cpos,spos;
    private ChunkPos held,cut;
    private long beforeGenerated,beforeConsumed,beforeCredit;
    private int beforeLava;
    private int absent;
    private void require(boolean ok,String why){checks++;if(!ok)throw new IllegalStateException("RF_ASSERTIONS_FAIL: "+why);}
    private void next(){w.getWorldInfo().setWorldTotalTime(w.getTotalWorldTime()+1);}
    private TileEntity place(BlockPos p,Block b){w.setBlockToAir(p);w.setBlockState(p,b.getDefaultState(),3);return w.getTileEntity(p);}
    private IEnergyStorage port(TileEntity t,EnumFacing f){return t.getCapability(CapabilityEnergy.ENERGY,f);}
    private void role(StorageTile s,EnumFacing f,int r){while(s.role(f)!=r)s.control(f.getIndex());}
    private StorageTile battery(BlockPos p,int rf){StorageTile s=(StorageTile)place(p,Content.STORAGE);NBTTagCompound n=s.writeToNBT(new NBTTagCompound());n.setInteger("RF",rf);s.readFromNBT(n);return s;}
    private void outputs(StorageTile s){for(EnumFacing f:EnumFacing.values())role(s,f,2);}
    @Mod.EventHandler public void pre(FMLPreInitializationEvent e){ForgeChunkManager.setForcedChunkLoadingCallback(this,(ForgeChunkManager.LoadingCallback)(tickets,world)->{for(ForgeChunkManager.Ticket t:tickets)ForgeChunkManager.releaseTicket(t);});}
    @Mod.EventHandler public void start(FMLServerStartedEvent e){
        if(Boolean.getBoolean("rfnetwork.chunkOnly"))return;
        if(!net.minecraftforge.fml.common.FMLCommonHandler.instance().getMinecraftServerInstance().isDedicatedServer())return;
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.register(this);
        w=DimensionManager.getWorld(0);w.getGameRules().setOrCreateGameRule("doMobSpawning","false");w.getChunk(0,0);
        fuel();budgets();topology();partialCommit();persistence();boundary();
    }
    private void fuel(){
        BlockPos p=new BlockPos(2,80,2);GeneratorTile g=(GeneratorTile)place(p,Content.GENERATOR);IEnergyStorage out=port(g,EnumFacing.EAST);
        require(out.extractEnergy(1000,true)==0,"Empty generator supplies no free RF");
        require(g.getSlotsForFace(EnumFacing.UP).length==1 && g.getSlotsForFace(EnumFacing.UP)[0]==0 && !g.canInsertItem(1,new ItemStack(Content.UPGRADE),EnumFacing.UP),"Vanilla hopper sided inventory hides upgrade slot");
        net.minecraftforge.items.IItemHandler items=g.getCapability(net.minecraftforge.items.CapabilityItemHandler.ITEM_HANDLER_CAPABILITY,EnumFacing.UP);
        require(!items.insertItem(1,new ItemStack(Content.UPGRADE),false).isEmpty() && g.getStackInSlot(1).isEmpty(),"Forge item handler rejects automated upgrade insertion");
        g.setInventorySlotContents(0,new ItemStack(Items.COAL));next();
        require(out.extractEnergy(9999,true)==1000 && g.credit()==0 && g.getStackInSlot(0).getCount()==1,"Simulation leaves solid fuel untouched and respects generation rate");
        require(out.extractEnergy(1,false)==1 && g.credit()==6399996 && g.getStackInSlot(0).isEmpty(),"One coal prepayment minus exact four-unit charge");
        require(out.extractEnergy(9999,false)==999 && out.extractEnergy(1,false)==0,"Solid generation shared across calls");
        g.setInventorySlotContents(1,new ItemStack(Content.UPGRADE));next();long credit=g.credit();
        require(out.extractEnergy(9999,false)==2000 && credit-g.credit()==6000,"Upgrade doubles RF and uses 1.5x fuel per full tick");
        g.setInventorySlotContents(1,ItemStack.EMPTY);next();credit=g.credit();require(out.extractEnergy(1000,false)==1000 && credit-g.credit()==4000,"Removing upgrade changes existing prepaid fuel economy");
        g=(GeneratorTile)place(p,Content.GENERATOR);out=port(g,EnumFacing.UP);g.setInventorySlotContents(1,new ItemStack(Content.UPGRADE));
        require(g.fluids().fill(new FluidStack(FluidRegistry.WATER,1000),true)==0,"Non-lava fluid rejected");g.fluids().fill(new FluidStack(FluidRegistry.LAVA,1),true);next();
        require(out.extractEnergy(2000,true)==1333 && g.lava()==1 && g.credit()==0,"Fractional liquid availability simulation");
        require(out.extractEnergy(2000,false)==1333 && g.lava()==0 && g.credit()==1,"No rounded-up free RF from 1mB upgraded lava");next();
        require(out.extractEnergy(2000,false)==0 && g.credit()==1,"Sub-RF remainder retained, no free fuel");
        g.fluids().fill(new FluidStack(FluidRegistry.LAVA,2),true);long start=g.credit();next();
        require(out.extractEnergy(2000,false)==2000 && g.lava()==0 && g.credit()==start+8000-6000,"Two mB prepaid exactly for upgraded full tick");
        next();require(out.extractEnergy(2000,false)==667 && g.credit()==0,"Fractional carry across additions totals exactly 4000 RF for 3mB upgraded");
        // Many one-RF calls establish fractional accounting without rounding each request.
        g=(GeneratorTile)place(p,Content.GENERATOR);g.setInventorySlotContents(1,new ItemStack(Content.UPGRADE));g.fluids().fill(new FluidStack(FluidRegistry.LAVA,1),true);out=port(g,EnumFacing.NORTH);next();
        for(int i=0;i<1333;i++)require(out.extractEnergy(1,false)==1,"One RF fractional ledger request "+i);
        require(g.charged==3999 && g.credit()==1 && out.extractEnergy(1,false)==0,"Repeated fractional requests have no rounding drift");
        g=(GeneratorTile)place(p,Content.GENERATOR);g.setInventorySlotContents(0,new ItemStack(Items.STICK));g.setInventorySlotContents(1,new ItemStack(Content.UPGRADE));out=port(g,EnumFacing.EAST);
        for(int i=0;i<67;i++){next();out.extractEnergy(2000,false);}
        require(g.generated==133333 && g.charged==399999 && g.credit()==1 && g.getStackInSlot(0).isEmpty(),"Single stick upgraded exhaustion retains sub-RF fuel without extra output");
        // Lava has lower priority than a valid solid item, and backpressure never reserves a fuel item.
        g=(GeneratorTile)place(p,Content.GENERATOR);g.setInventorySlotContents(0,new ItemStack(Items.COAL));g.fluids().fill(new FluidStack(FluidRegistry.LAVA,1000),true);next();Network.push(g);
        require(g.generated==0 && g.credit()==0 && g.getStackInSlot(0).getCount()==1 && g.lava()==1000,"No demand leaves both fuels untouched");
        out=port(g,EnumFacing.EAST);out.extractEnergy(1000,false);require(g.lava()==1000 && g.getStackInSlot(0).isEmpty(),"Solid priority over lava");
        g=(GeneratorTile)place(p,Content.GENERATOR);g.setInventorySlotContents(0,new ItemStack(Items.COAL));StorageTile full=battery(p.east(),StorageTile.CAPACITY);next();Network.push(g);
        require(full.stored()==StorageTile.CAPACITY && g.generated==0 && g.credit()==0 && g.getStackInSlot(0).getCount()==1,"Full storage backpressure preserves fuel");
        port(full,EnumFacing.EAST).extractEnergy(20000,false);Network.push(g);require(g.generated==1000 && full.stored()==StorageTile.CAPACITY-19000,"Cleared capacity resumes fuel conversion once");w.setBlockToAir(p.east());
        w.setBlockToAir(p);
        System.out.println("RF_ACCOUNTING fuel: base 1000/4000 units, upgrade 2000/6000; 3mB -> 4000 RF; fractional calls passed");
    }
    private void budgets(){
        BlockPos p=new BlockPos(5,80,5);StorageTile s=battery(p,100000);outputs(s);IEnergyStorage west=port(s,EnumFacing.WEST),east=port(s,EnumFacing.EAST);next();
        require(west.extractEnergy(99999,true)==20000 && east.extractEnergy(99999,true)==20000 && s.stored()==100000,"Output simulations do not consume shared budget");
        require(west.extractEnergy(7500,false)==7500 && east.extractEnergy(4500,false)==4500,"External extraction shares all-face budget");
        SinkTile sink=(SinkTile)place(p.up(),Content.SINK);sink.control(100+100000);Network.push(s);
        require(sink.consumed==8000 && west.extractEnergy(1,false)==0 && s.stored()==80000,"Active push plus external extraction totals 20000");
        next();require(east.extractEnergy(99999,false)==20000,"Output allowance resets on next world tick");
        role(s,EnumFacing.WEST,1);require(!west.canExtract() && west.extractEnergy(1000,false)==0 && west.canReceive(),"Held capability follows live face configuration");
        role(s,EnumFacing.EAST,1);next();require(west.receiveEnergy(45000,false)==45000 && east.receiveEnergy(45000,false)==15000,"Input budget combined across faces");
        role(s,EnumFacing.WEST,0);require(!west.canReceive() && west.receiveEnergy(1,false)==0,"Disabled held face rejects receipt");
        require(!sink.control(99) && !sink.control(100+100001),"Out-of-range numeric control rejected");sink.control(100);next();require(port(sink,EnumFacing.DOWN).receiveEnergy(99,false)==0,"Zero sink demand blocks");
        sink.control(100+999);require(port(sink,EnumFacing.DOWN).receiveEnergy(500,false)==500 && port(sink,EnumFacing.EAST).receiveEnergy(500,false)==499 && port(sink,EnumFacing.UP).receiveEnergy(1,false)==0,"Sink demand shared across faces");
        w.setBlockToAir(p);w.setBlockToAir(p.up());
    }
    private void topology(){
        BlockPos center=new BlockPos(8,80,8);CableTile cable=(CableTile)place(center,Content.CABLE);
        List<StorageTile> sources=new ArrayList<>();for(EnumFacing f:new EnumFacing[]{EnumFacing.WEST,EnumFacing.EAST,EnumFacing.UP,EnumFacing.DOWN}){StorageTile s=battery(center.offset(f),200000);outputs(s);sources.add(s);}
        CableTile north=(CableTile)place(center.north(),Content.CABLE),south=(CableTile)place(center.south(),Content.CABLE);
        SinkTile a=(SinkTile)place(center.north(2),Content.SINK),b=(SinkTile)place(center.south(2),Content.SINK);a.control(100+50000);b.control(100+50000);
        next();for(StorageTile s:sources)Network.push(s);
        int stored=0;for(StorageTile s:sources)stored+=s.stored();
        require(a.consumed+b.consumed==60000 && stored==740000,"Four sources/two consumers: branch shared bottleneck and conservation");
        require(cable.remaining()==0 && port(cable,EnumFacing.EAST).getEnergyStored()==0 && port(cable,EnumFacing.EAST).getMaxEnergyStored()==0,"Cable throughput exhausted with zero buffer");
        cable=(CableTile)place(center,Content.CABLE);require(cable.remaining()==0 && port(cable,EnumFacing.WEST).receiveEnergy(1,false)==0,"Same-tick cable replacement does not reset budget");
        next();w.setBlockToAir(center);long consumed=a.consumed+b.consumed;for(StorageTile s:sources)Network.push(s);require(a.consumed+b.consumed==consumed,"Split network blocks transfer without deleting source RF");
        cable=(CableTile)place(center,Content.CABLE);for(StorageTile s:sources)Network.push(s);require(a.consumed+b.consumed==consumed+60000,"Network merge resumes transfer");
        // Cleanup branch fixture, then build an actual cable ring with two consumers adjacent to multiple paths.
        for(EnumFacing f:EnumFacing.values())w.setBlockToAir(center.offset(f));w.setBlockToAir(center);w.setBlockToAir(center.north(2));w.setBlockToAir(center.south(2));
        List<BlockPos> ring=Arrays.asList(center,center.east(),center.east(2),center.east(2).south(),center.east(2).south(2),center.east().south(2),center.south(2),center.south());
        for(BlockPos p:ring)place(p,Content.CABLE);a=(SinkTile)place(center.east().south(),Content.SINK);a.control(100+100000);b=(SinkTile)place(center.north(),Content.SINK);b.control(100+100000);
        CableTile inlet=(CableTile)w.getTileEntity(center);next();
        require(port(inlet,EnumFacing.WEST).receiveEnergy(90000,true)==60000 && a.consumed+b.consumed==0,"Loop simulation accounts endpoint/path budgets without mutation");
        require(port(inlet,EnumFacing.WEST).receiveEnergy(90000,false)==60000 && a.consumed+b.consumed==60000,"Loop cannot multiply source injection");
        require(port(inlet,EnumFacing.UP).receiveEnergy(1,false)==0 && inlet.remaining()==0,"All ingress faces share cable budget");
        for(BlockPos p:ring)w.setBlockToAir(p);w.setBlockToAir(center.east().south());w.setBlockToAir(center.north());
        System.out.println("RF_ACCOUNTING topology: 800000 stored -> 740000 + 60000 consumed; branches/loop/merge/split/replacement passed");
    }
    private void persistence(){
        BlockPos p=new BlockPos(3,80,3);GeneratorTile g=(GeneratorTile)place(p,Content.GENERATOR);g.setInventorySlotContents(0,new ItemStack(Items.COAL,2));g.setInventorySlotContents(1,new ItemStack(Content.UPGRADE));g.fluids().fill(new FluidStack(FluidRegistry.LAVA,777),true);next();port(g,EnumFacing.UP).extractEnergy(123,false);
        NBTTagCompound n=g.writeToNBT(new NBTTagCompound());GeneratorTile restored=new GeneratorTile();restored.readFromNBT(n);require(restored.credit()==g.credit() && restored.lava()==777 && restored.upgraded() && restored.getStackInSlot(0).getCount()==1 && restored.generated==123 && restored.charged==369,"Saved generator inventory/upgrade/tank/fractional credit/counters");
        StorageTile s=battery(p.east(),1234567);role(s,EnumFacing.UP,0);role(s,EnumFacing.SOUTH,2);StorageTile r=new StorageTile();r.readFromNBT(s.writeToNBT(new NBTTagCompound()));require(r.stored()==1234567 && r.role(EnumFacing.UP)==0 && r.role(EnumFacing.SOUTH)==2,"Storage large RF and face NBT");
        SinkTile sink=(SinkTile)place(p.west(),Content.SINK);sink.control(100+87654);SinkTile rs=new SinkTile();rs.readFromNBT(sink.writeToNBT(new NBTTagCompound()));require(rs.requested()==87654,"Saved numeric sink demand");
        // Signed-short transport reconstructed into a large client RF value.
        for(int i=0;i<32;i++)r.setField(i,(short)s.getField(i));require(r.stored()==1234567,"Split window-property reconstruction above signed-short limit");
        w.setBlockToAir(p);w.setBlockToAir(p.east());w.setBlockToAir(p.west());
    }
    /** Simulation is a query, not a reservation: acceptance can legitimately be smaller at commit. */
    private static final class PartialReceiver extends TileEntity {
        private int received;
        private final IEnergyStorage energy=new IEnergyStorage(){
            public int receiveEnergy(int n,boolean sim){int a=Math.min(n,sim?1000:500);if(!sim)received+=a;return a;}
            public int extractEnergy(int n,boolean sim){return 0;}public int getEnergyStored(){return received;}public int getMaxEnergyStored(){return 100000;}
            public boolean canReceive(){return true;}public boolean canExtract(){return false;}
        };
        public <T>T getCapability(Capability<T> c,EnumFacing f){return c==CapabilityEnergy.ENERGY?CapabilityEnergy.ENERGY.cast(energy):super.getCapability(c,f);}
    }
    private void partialCommit(){
        BlockPos p=new BlockPos(5,80,3);GeneratorTile g=(GeneratorTile)place(p,Content.GENERATOR);g.setInventorySlotContents(0,new ItemStack(Items.COAL));place(p.east(),Content.CABLE);place(p.east(2),Content.SINK);PartialReceiver receiver=new PartialReceiver();w.setTileEntity(p.east(2),receiver);next();Network.push(g);
        require(receiver.received==500 && g.generated==500 && g.charged==2000 && g.credit()==6398000,"Cable receiver accepts less at commit: unused solid fuel credit restored at source");
        require(((CableTile)w.getTileEntity(p.east())).remaining()==59500,"Partial commit spends cable budget only for delivered RF");
        require(port(g,EnumFacing.UP).extractEnergy(1,false)==0,"Rejected portion retains attempted source budget for this tick");
        w.setBlockToAir(p.east());w.setBlockToAir(p.east(2));g=(GeneratorTile)place(p,Content.GENERATOR);g.setInventorySlotContents(1,new ItemStack(Content.UPGRADE));g.fluids().fill(new FluidStack(FluidRegistry.LAVA,1),true);place(p.east(),Content.SINK);receiver=new PartialReceiver();w.setTileEntity(p.east(),receiver);next();Network.push(g);
        require(receiver.received==500 && g.generated==500 && g.charged==1500 && g.credit()==2500 && g.lava()==0,"Direct partial receiver restores exact upgraded fractional liquid credit");
        w.setBlockToAir(p);w.setBlockToAir(p.east());
    }
    private void boundary(){
        gpos=new BlockPos(2046,80,2056);cpos=gpos.east();spos=cpos.east();held=new ChunkPos(gpos);cut=new ChunkPos(spos);
        ticket=ForgeChunkManager.requestTicket(this,w,ForgeChunkManager.Type.NORMAL);require(ticket!=null,"QA ticking ticket");ForgeChunkManager.forceChunk(ticket,held);ForgeChunkManager.forceChunk(ticket,cut);w.getChunk(held.x,held.z);w.getChunk(cut.x,cut.z);
        GeneratorTile g=(GeneratorTile)place(gpos,Content.GENERATOR);g.setInventorySlotContents(1,new ItemStack(Content.UPGRADE));g.fluids().fill(new FluidStack(FluidRegistry.LAVA,1000),true);place(cpos,Content.CABLE);SinkTile s=(SinkTile)place(spos,Content.SINK);s.control(100+2000);
        StorageTile saved=battery(gpos.south(3),1234567);for(EnumFacing f:EnumFacing.values())role(saved,f,0);role(saved,EnumFacing.SOUTH,2);
        // Flush placement before unload; never ask World for the missing chunk during the absent interval.
        w.getChunkProvider().saveChunks(true);age=0;phase=0;
    }
    @SubscribeEvent public void tick(TickEvent.ServerTickEvent e){
        if(e.phase!=TickEvent.Phase.END || w==null || done)return;age++;
        if(phase==0 && age==8){
            GeneratorTile g=(GeneratorTile)w.getTileEntity(gpos);SinkTile s=(SinkTile)w.getTileEntity(spos);require(g.generated>0 && g.generated==s.consumed,"Actual network ticks conserve across loaded chunk boundary");
            beforeGenerated=g.generated;beforeConsumed=s.consumed;beforeCredit=g.credit();beforeLava=g.lava();
            ForgeChunkManager.unforceChunk(ticket,cut);Chunk c=w.getChunkProvider().getLoadedChunk(cut.x,cut.z);w.getChunkProvider().queueUnload(c);phase=1;age=0;
        }else if(phase==1){
            if(w.getChunkProvider().getLoadedChunk(cut.x,cut.z)==null){
                require(!w.isBlockLoaded(spos) && Network.loaded(w,spos)==null,"Absent neighbor stays unavailable without loading");GeneratorTile g=(GeneratorTile)w.getTileEntity(gpos);
                require(g.generated==beforeGenerated && g.credit()==beforeCredit && g.lava()==beforeLava,"Backpressure across absent consumer spends no fuel");absent++;
                if(absent==6){ForgeChunkManager.forceChunk(ticket,cut);w.getChunk(cut.x,cut.z);SinkTile s=(SinkTile)w.getTileEntity(spos);require(s.consumed==beforeConsumed && s.requested()==2000,"Actual chunk save/reload retains sink counter/settings");phase=2;age=0;}
            }else if(age>80)require(false,"Chunk did not unload");
        }else if(phase==2 && age==6){
            GeneratorTile g=(GeneratorTile)w.getTileEntity(gpos);SinkTile s=(SinkTile)w.getTileEntity(spos);require(g.generated>beforeGenerated && g.generated==s.consumed,"Chunk rejoin resumes conserved generation");
            // Save/unload the generator side instead; sink remains ticking but receives nothing.
            beforeGenerated=g.generated;beforeConsumed=s.consumed;beforeCredit=g.credit();beforeLava=g.lava();ForgeChunkManager.forceChunk(ticket,cut);ForgeChunkManager.unforceChunk(ticket,held);w.getChunkProvider().queueUnload(w.getChunkProvider().getLoadedChunk(held.x,held.z));phase=3;age=0;absent=0;
        }else if(phase==3){
            if(w.getChunkProvider().getLoadedChunk(held.x,held.z)==null){require(Network.loaded(w,cpos)==null && Network.loaded(w,gpos)==null,"Absent source/cable chunks stay absent");SinkTile s=(SinkTile)w.getTileEntity(spos);require(s.consumed==beforeConsumed,"No phantom generation from unloaded source");absent++;
                if(absent==6){ForgeChunkManager.forceChunk(ticket,held);w.getChunk(held.x,held.z);GeneratorTile g=(GeneratorTile)w.getTileEntity(gpos);require(g.generated==beforeGenerated && g.credit()==beforeCredit && g.lava()==beforeLava && g.upgraded(),"Actual generator unload/save/reload retains exact prepaid credit/tank/upgrade");StorageTile saved=(StorageTile)w.getTileEntity(gpos.south(3));require(saved.stored()==1234567 && saved.role(EnumFacing.SOUTH)==2 && saved.role(EnumFacing.NORTH)==0,"Actual storage chunk save/reload retains large RF and face settings");phase=4;age=0;}
            }else if(age>80)require(false,"Source chunk did not unload");
        }else if(phase==4 && age==6){GeneratorTile g=(GeneratorTile)w.getTileEntity(gpos);SinkTile s=(SinkTile)w.getTileEntity(spos);require(g.generated==s.consumed && g.generated>beforeGenerated,"Source reload resumes without duplicated fuel/RF");done=true;ForgeChunkManager.releaseTicket(ticket);System.out.println("RF_SERVER_PASS assertions="+checks+" actual-unloaded-intervals=2 boundary-conservation="+g.generated);}
    }
}
