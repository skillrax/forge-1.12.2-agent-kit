package com.example.networkstarter;
import java.util.*;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.energy.*;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
/** Rebuild bounded loaded topology per offer; simple representative network, no cached neighbor or energy buffer. */
@Mod.EventBusSubscriber(modid=NetworkMod.ID)
public final class Network {
    public static TileEntity loaded(World w,BlockPos p){return new LoadedView(w).tile(p);}
    private static IEnergyStorage port(TileEntity t,EnumFacing f){return t==null?null:t.getCapability(CapabilityEnergy.ENERGY,f);}
    private static final class Step {
        final CableTile cable;final List<CableTile> path;
        Step(CableTile c,List<CableTile> p){cable=c;path=new ArrayList<>(p);path.add(c);}
    }
    private static final class Route {
        final TileEntity tile;final EnumFacing face;final List<CableTile> path;final int amount;
        Route(TileEntity t,EnumFacing f,List<CableTile> p,int a){tile=t;face=f;path=p;amount=a;}
    }
    private static List<Route> plan(CableTile start,int amount,BlockPos excluded){
        World w=start.getWorld();LoadedView view=new LoadedView(w);List<Route> result=new ArrayList<>();
        if(view.tile(start.getPos())!=start)return result;
        Map<BlockPos,Integer> shadow=new HashMap<>(),accepted=new HashMap<>();Set<BlockPos> seen=new HashSet<>();
        // Multiple passes allow another path once a short path saturates. Each pass must allocate energy or stop.
        int left=amount;
        while(left>0){
            int before=left;seen.clear();Deque<Step> queue=new ArrayDeque<>();queue.add(new Step(start,Collections.emptyList()));seen.add(start.getPos());
            while(!queue.isEmpty() && left>0){
                Step s=queue.removeFirst();int pathLimit=left;
                for(CableTile c:s.path)pathLimit=Math.min(pathLimit,c.remaining()-shadow.getOrDefault(c.getPos(),0));
                if(pathLimit<=0)continue;
                int rotation=(int)(w.getTotalWorldTime()%6);
                for(int j=0;j<6 && left>0;j++){
                    EnumFacing f=EnumFacing.values()[(j+rotation)%6];BlockPos p=s.cable.getPos().offset(f);
                    if(p.equals(excluded))continue;TileEntity t=view.tile(p);
                    if(t instanceof CableTile){
                        CableTile c=(CableTile)t;
                        if(c.remaining()-shadow.getOrDefault(p,0)>0 && seen.add(p))queue.add(new Step(c,s.path));
                        continue;
                    }
                    IEnergyStorage target=port(t,f.getOpposite());if(target==null || !target.canReceive())continue;
                    int previous=accepted.getOrDefault(p,0);
                    // Subtract planned receipt for all faces of this endpoint, so simulation cannot multiply its shared budget/capacity.
                    int n=Math.min(pathLimit,Math.max(0,target.receiveEnergy(Math.min(Integer.MAX_VALUE-left,previous)+left,true)-previous));
                    if(n<=0)continue;result.add(new Route(t,f.getOpposite(),s.path,n));accepted.put(p,previous+n);left-=n;
                    for(CableTile c:s.path)shadow.put(c.getPos(),shadow.getOrDefault(c.getPos(),0)+n);
                    pathLimit=left;for(CableTile c:s.path)pathLimit=Math.min(pathLimit,c.remaining()-shadow.getOrDefault(c.getPos(),0));
                    if(pathLimit<=0)break;
                }
            }
            if(left==before)break;
        }return result;
    }
    private static int total(List<Route> r){int n=0;for(Route x:r)n+=x.amount;return n;}
    private static int commit(List<Route> routes){
        if(routes.isEmpty())return 0;
        LoadedView view=new LoadedView(routes.get(0).tile.getWorld());
        int n=0;for(Route r:routes){
            boolean valid=true;for(CableTile c:r.path)if(view.tile(c.getPos())!=c){valid=false;break;}
            if(!valid)continue;
            IEnergyStorage target=port(view.tile(r.tile.getPos()),r.face);
            int moved=target==null?0:target.receiveEnergy(r.amount,false);
            if(moved<0 || moved>r.amount)throw new IllegalStateException("FE endpoint returned invalid accepted amount: "+r.tile.getPos());
            for(CableTile c:r.path)TickLedger.spend(c.getWorld(),c.getPos(),"cable",moved);n+=moved;
        }return n;
    }
    public static int offer(CableTile c,int amount,boolean sim,BlockPos excluded){List<Route> r=plan(c,amount,excluded);return sim?total(r):commit(r);}
    public static void push(EnergyTile source){
        World w=source.getWorld();LoadedView view=new LoadedView(w);if(view.tile(source.getPos())!=source)return;int rotation=(int)(w.getTotalWorldTime()%6);
        for(int i=0;i<6;i++){
            EnumFacing f=EnumFacing.values()[(i+rotation)%6];IEnergyStorage out=source.port(f);if(!out.canExtract())continue;
            int available=out.extractEnergy(Integer.MAX_VALUE,true);if(available==0)continue;
            TileEntity t=view.tile(source.getPos().offset(f));
            if(t instanceof CableTile){
                List<Route> r=plan((CableTile)t,available,source.getPos());int n=total(r);
                if(n>0){int cost=source.fuelCost();if(out.extractEnergy(n,false)!=n)throw new IllegalStateException("Source changed during transfer");int received=commit(r);source.refund(n-received,cost);}
            }else{
                IEnergyStorage in=port(t,f.getOpposite());if(in==null || !in.canReceive())continue;
                int n=Math.min(available,in.receiveEnergy(available,true));
                if(n>0){int cost=source.fuelCost();int extracted=out.extractEnergy(n,false);int received=in.receiveEnergy(extracted,false);if(received<0 || received>extracted)throw new IllegalStateException("FE endpoint returned invalid accepted amount");source.refund(extracted-received,cost);}
            }
        }
    }
    @SubscribeEvent public static void tick(TickEvent.WorldTickEvent e){
        if(e.phase!=TickEvent.Phase.END || e.world.isRemote)return;
        LoadedView view=new LoadedView(e.world);
        List<EnergyTile> generators=new ArrayList<>(),storage=new ArrayList<>();List<SinkTile> sinks=new ArrayList<>();
        for(TileEntity t:new ArrayList<>(e.world.loadedTileEntityList))if(!t.isInvalid() && view.tile(t.getPos())==t){if(t instanceof GeneratorTile)generators.add((EnergyTile)t);else if(t instanceof StorageTile)storage.add((EnergyTile)t);else if(t instanceof SinkTile)sinks.add((SinkTile)t);}
        // Generators first; rotate order for shared-demand contention. Storage follows with its own shared output budget.
        for(List<EnergyTile> list:Arrays.asList(generators,storage)){if(!list.isEmpty())Collections.rotate(list,(int)(e.world.getTotalWorldTime()%list.size()));for(EnergyTile t:list)push(t);}
        for(SinkTile s:sinks)s.recordTick();
    }
}
