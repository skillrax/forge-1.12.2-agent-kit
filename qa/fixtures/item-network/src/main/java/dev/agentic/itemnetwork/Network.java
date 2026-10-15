package dev.agentic.itemnetwork;

import java.util.*;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.*;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.items.*;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

/** Fresh six-face BFS, max 128 ducts per component. A single world cadence owns the budget. */
@Mod.EventBusSubscriber(modid=ItemNetwork.ID)
public final class Network {
    public static final int LIMIT=128,BUDGET=8,CADENCE=10;
    public static final Comparator<DuctTile> ORDER=Comparator.comparingInt((DuctTile t)->t.getPos().getX()).thenComparingInt(t->t.getPos().getY()).thenComparingInt(t->t.getPos().getZ());
    public static TileEntity loaded(World w,BlockPos p){return new LoadedView(w).tile(p);}
    private static IItemHandler port(LoadedView v,DuctTile d){
        if(v.tile(d.getPos())!=d)return null;
        BlockPos p=d.getPos().offset(d.direction());TileEntity t=v.tile(p);
        if(t==null || t instanceof DuctTile || t instanceof TileEntityChest && !v.area(p,3))return null;
        return t.getCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY,d.direction().getOpposite());
    }
    private static boolean alias(LoadedView v,BlockPos a,BlockPos b){
        return a.equals(b) || v.tile(a) instanceof TileEntityChest && v.tile(b) instanceof TileEntityChest
            && a.getY()==b.getY() && Math.abs(a.getX()-b.getX())+Math.abs(a.getZ()-b.getZ())==1;
    }
    private static List<DuctTile> graph(LoadedView v,DuctTile root){
        List<DuctTile> out=new ArrayList<>();Set<BlockPos> seen=new HashSet<>();Deque<DuctTile> q=new ArrayDeque<>();q.add(root);seen.add(root.getPos());
        while(!q.isEmpty()){
            DuctTile d=q.removeFirst();out.add(d);
            for(EnumFacing f:EnumFacing.values()){
                TileEntity t=v.tile(d.getPos().offset(f));
                if(t instanceof DuctTile && ((DuctTile)t).enabled && seen.add(t.getPos())){
                    if(seen.size()>LIMIT)return Collections.emptyList();q.addLast((DuctTile)t);
                }
            }
        }return out;
    }
    private static List<DuctTile> destinations(LoadedView v,DuctTile owner,List<DuctTile> component,Set<BlockPos> sources){
        // BFS order gives shortest hop count; coordinate order breaks equal-distance ties.
        Map<BlockPos,Integer> distance=new HashMap<>();Deque<DuctTile> q=new ArrayDeque<>();q.add(owner);distance.put(owner.getPos(),0);
        while(!q.isEmpty()){DuctTile d=q.removeFirst();for(EnumFacing f:EnumFacing.values()){
            TileEntity t=v.tile(d.getPos().offset(f));if(t instanceof DuctTile && component.contains(t) && !distance.containsKey(t.getPos())){
                distance.put(t.getPos(),distance.get(d.getPos())+1);q.add((DuctTile)t);
            }
        }}
        List<DuctTile> out=new ArrayList<>();
        List<DuctTile> sorted=new ArrayList<>(component);sorted.sort(Comparator.comparingInt((DuctTile d)->distance.get(d.getPos())).thenComparing(ORDER));
        for(DuctTile d:sorted)if(d.role==DuctTile.DESTINATION){
            BlockPos p=d.getPos().offset(d.direction());boolean blocked=false;
            for(BlockPos s:sources)if(alias(v,s,p))blocked=true;
            if(!owner.isEmpty() && owner.origin!=null && alias(v,owner.origin,p))blocked=true;
            // Deduplicate physical inventories AFTER checking the filter: two filters on one chest remain useful.
            if(!blocked)out.add(d);
        }return out;
    }
    private static int room(IItemHandler h,ItemStack s){return h==null?0:s.getCount()-ItemHandlerHelper.insertItem(h,s.copy(),true).getCount();}
    private static int deliver(DuctTile owner,List<DuctTile> targets,int limit){
        int moved=0;Set<BlockPos> attempted=new HashSet<>();
        for(DuctTile dest:targets){
            if(owner.isEmpty() || moved>=limit)break;LoadedView v=new LoadedView(owner.getWorld());
            if(v.tile(owner.getPos())!=owner || v.tile(dest.getPos())!=dest)continue;
            ItemStack offer=owner.getStackInSlot(0).copy();offer.setCount(Math.min(offer.getCount(),limit-moved));
            if(!dest.matches(offer))continue;
            BlockPos p=dest.getPos().offset(dest.direction());boolean duplicate=false;for(BlockPos a:attempted)if(alias(v,a,p))duplicate=true;
            if(duplicate)continue;attempted.add(p);
            IItemHandler h=port(v,dest);int n=room(h,offer);if(n<=0)continue;offer.setCount(n);
            // Buffer stays owner until commit returns. Any unexpected remainder stays persisted, never dropped.
            ItemStack rem=ItemHandlerHelper.insertItem(h,offer.copy(),false);
            int accepted=offer.getCount()-rem.getCount();
            if(accepted<0 || accepted>offer.getCount() || !rem.isEmpty() && (!ItemStack.areItemsEqual(offer,rem) || !ItemStack.areItemStackTagsEqual(offer,rem)))
                throw new IllegalStateException("Handler violated insertion remainder contract");
            if(accepted>0){owner.decrStackSize(0,accepted);owner.delivered+=accepted;owner.markDirty();moved+=accepted;}
        }return moved;
    }
    private static int transfer(DuctTile owner,List<DuctTile> component,Set<BlockPos> sources,int budget){
        LoadedView v=new LoadedView(owner.getWorld());List<DuctTile> targets=destinations(v,owner,component,sources);
        if(!owner.isEmpty())return deliver(owner,targets,budget); // drain before any new extraction, including after role/filter edits
        if(owner.role!=DuctTile.SOURCE)return 0;IItemHandler h=port(v,owner);if(h==null)return 0;
        for(int slot=0;slot<h.getSlots();slot++){
            ItemStack simulated=h.extractItem(slot,budget,true);if(!owner.matches(simulated))continue;
            int capacity=0;for(DuctTile dest:targets)if(dest.matches(simulated)){
                capacity=Math.max(capacity,room(port(new LoadedView(owner.getWorld()),dest),simulated));
            }
            int n=Math.min(budget,capacity);if(n<=0)continue;
            // Owned slot is empty and private; retain the entire actual extraction before destination commit.
            h=port(new LoadedView(owner.getWorld()),owner);if(h==null)continue;
            ItemStack actual=h.extractItem(slot,n,false);if(actual.isEmpty())continue;owner.own(actual);
            deliver(owner,targets,Math.min(budget,actual.getCount()));return Math.min(budget,actual.getCount());
        }return 0;
    }
    @SubscribeEvent public static void tick(TickEvent.WorldTickEvent e){
        if(e.phase!=TickEvent.Phase.END || e.world.isRemote || e.world.getTotalWorldTime()%CADENCE!=0)return;
        LoadedView v=new LoadedView(e.world);List<DuctTile> roots=new ArrayList<>();Set<BlockPos> sources=new HashSet<>();
        for(TileEntity t:new ArrayList<>(e.world.loadedTileEntityList))if(t instanceof DuctTile && v.tile(t.getPos())==t){
            DuctTile d=(DuctTile)t;if(d.role==DuctTile.SOURCE)sources.add(d.getPos().offset(d.direction()));if(d.enabled)roots.add(d);
        }
        roots.sort(ORDER);if(!roots.isEmpty())Collections.rotate(roots,-(int)((e.world.getTotalWorldTime()/CADENCE)%roots.size()));
        Set<BlockPos> processed=new HashSet<>();int budget=BUDGET;
        for(DuctTile root:roots){if(budget<=0)break;if(processed.contains(root.getPos()))continue;
            List<DuctTile> c=graph(v,root);if(c.isEmpty())continue;
            List<DuctTile> owners=new ArrayList<>();
            for(DuctTile d:c){processed.add(d.getPos());if(d.role==DuctTile.SOURCE)sources.add(d.getPos().offset(d.direction()));if(d.role==DuctTile.SOURCE || !d.isEmpty())owners.add(d);}
            owners.sort(ORDER);if(!owners.isEmpty())Collections.rotate(owners,-(int)((e.world.getTotalWorldTime()/CADENCE)%owners.size()));
            for(DuctTile owner:owners){if(budget<=0)break;budget-=transfer(owner,c,sources,budget);}
        }
    }
}
