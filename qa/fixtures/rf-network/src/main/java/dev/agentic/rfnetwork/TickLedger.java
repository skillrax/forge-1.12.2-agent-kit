package dev.agentic.rfnetwork;
import java.util.*;
import net.minecraft.world.World;
import net.minecraft.util.math.BlockPos;
/** World/position ledger survives capability reacquisition and same-tick topology replacement. No energy lives here. */
public final class TickLedger {
    private static final Map<World,TickLedger> WORLDS=new WeakHashMap<>();
    private long tick=Long.MIN_VALUE;
    private final Map<String,Integer> used=new HashMap<>();
    private static TickLedger ledger(World w){
        TickLedger l=WORLDS.computeIfAbsent(w,k->new TickLedger());
        if(l.tick!=w.getTotalWorldTime()){l.tick=w.getTotalWorldTime();l.used.clear();}return l;
    }
    private static String key(BlockPos p,String kind){return p.toLong()+":"+kind;}
    public static int remaining(World w,BlockPos p,String kind,int limit){return Math.max(0,limit-ledger(w).used.getOrDefault(key(p,kind),0));}
    public static void spend(World w,BlockPos p,String kind,int amount){TickLedger l=ledger(w);String k=key(p,kind);l.used.put(k,l.used.getOrDefault(k,0)+amount);}
    public static int used(World w,BlockPos p,String kind){return ledger(w).used.getOrDefault(key(p,kind),0);}
}
