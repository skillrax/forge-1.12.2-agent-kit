package dev.agentic.installedtest;

import com.google.gson.*;
import java.util.*;
import java.util.function.*;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.Item;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldServer;

/** Small sequencing/assertion API. Minecraft/Forge remain available for feature-specific actions. */
public final class TestContext {
    @FunctionalInterface public interface Action { void run() throws Exception; }
    private interface Step { boolean poll() throws Exception; }
    private final InstalledTestDriver driver;
    private final MinecraftServer server;
    private final JsonObject parameters;
    private final int maxTicks;
    private final List<Step> steps=new ArrayList<>();
    private final List<Invariant> invariants=new ArrayList<>();
    private final Set<String> names=new HashSet<>();
    private int cursor,assertionCount;
    private long ticks;
    private boolean running;
    private String phase="plan";

    TestContext(InstalledTestDriver driver,MinecraftServer server,JsonObject parameters,int maxTicks) {
        this.driver=driver;this.server=server;this.parameters=parameters==null?new JsonObject():parameters;this.maxTicks=maxTicks;
        // Suppress incidental spawns without the server deleting explicitly spawned test animals.
        world().getGameRules().setOrCreateGameRule("doMobSpawning","false");
    }
    public MinecraftServer server(){return server;}
    public WorldServer world(){return server.getWorld(0);}
    /** Inside the normal overworld spawn area; the scenario chooses and places its own fixture. */
    public BlockPos origin(){BlockPos spawn=world().getSpawnPoint();return new BlockPos(spawn.getX()+2,100,spawn.getZ()+2);}
    public JsonObject parameters(){return parameters;}
    public String string(String key,String fallback){return parameters.has(key)?parameters.get(key).getAsString():fallback;}
    public int integer(String key,int fallback){return parameters.has(key)?parameters.get(key).getAsInt():fallback;}
    public long ticks(){return ticks;}
    public Block block(String id) {
        ResourceLocation key=new ResourceLocation(id);
        if(!Block.REGISTRY.containsKey(key))throw new IllegalArgumentException("Unregistered block: "+id);
        return Block.REGISTRY.getObject(key);
    }
    public Item item(String id) {
        ResourceLocation key=new ResourceLocation(id);
        if(!Item.REGISTRY.containsKey(key))throw new IllegalArgumentException("Unregistered item: "+id);
        return Item.REGISTRY.getObject(key);
    }
    public void place(BlockPos pos,String id){place(pos,block(id).getDefaultState());}
    public void place(BlockPos pos,IBlockState state) {
        if(!world().setBlockState(pos,state,3) && world().getBlockState(pos)!=state)
            throw new IllegalStateException("Could not place block at "+pos);
    }
    public <T> T tile(BlockPos pos,Class<T> type) {
        TileEntity tile=world().getTileEntity(pos);
        if(!type.isInstance(tile))throw new IllegalStateException("Expected "+type.getSimpleName()+" at "+pos+", got "+tile);
        return type.cast(tile);
    }
    public IInventory inventory(BlockPos pos){return tile(pos,IInventory.class);}
    private void add(Step step) {
        if(running)throw new IllegalStateException("Declare steps in plan(), not during execution");
        if(steps.size()>=256)throw new IllegalStateException("At most 256 steps per scenario");
        steps.add(step);
    }
    private void name(String name) {
        if(name==null || !name.matches("[a-z][a-z0-9.-]{0,95}") || !names.add(name))
            throw new IllegalArgumentException("Invalid/duplicate scenario assertion: "+name);
    }
    public void then(String label,Action action) {
        if(label==null || label.isEmpty() || label.length()>96)throw new IllegalArgumentException("Action label must be 1..96 characters");
        add(()->{phase=label;action.run();return true;});
    }
    public void check(String name,BooleanSupplier condition,Supplier<String> detail) {
        name(name);add(()->{phase=name;record(name,condition.getAsBoolean(),detail.get());return true;});
    }
    public void waitTicks(int count) {
        if(count<1 || count>maxTicks)throw new IllegalArgumentException("waitTicks outside scenario budget");
        add(new Step(){long start=-1;public boolean poll(){phase="wait-ticks";if(start<0)start=ticks;return ticks-start>=count;}});
    }
    public void waitUntil(String name,int timeoutTicks,BooleanSupplier condition,Supplier<String> detail) {
        name(name);
        if(timeoutTicks<1 || timeoutTicks>maxTicks)throw new IllegalArgumentException("waitUntil outside scenario budget");
        add(new Step(){long start=-1;public boolean poll(){
            phase=name;if(start<0)start=ticks;
            if(condition.getAsBoolean()){record(name,true,detail.get());return true;}
            if(ticks-start>=timeoutTicks)record(name,false,"Condition timed out after "+timeoutTicks+" ticks: "+detail.get());
            return false;
        }});
    }
    /** Activates at this sequence position; checks every subsequent observed END tick, recorded once. */
    public void invariant(String name,BooleanSupplier condition,Supplier<String> detail) {
        name(name);if(names.size()>256)throw new IllegalStateException("Too many assertions");
        add(()->{if(invariants.size()>=32)throw new IllegalStateException("At most 32 active invariants");invariants.add(new Invariant(name,condition,detail));return true;});
    }
    private void record(String name,boolean passed,String detail) {
        driver.record("scenario."+name,passed,detail);assertionCount++;
        if(!passed)throw new AssertionError(name+": "+detail);
    }
    private final class Invariant {
        final String name;final BooleanSupplier condition;final Supplier<String> detail;long samples;
        Invariant(String name,BooleanSupplier condition,Supplier<String> detail){this.name=name;this.condition=condition;this.detail=detail;}
        void sample(){samples++;if(!condition.getAsBoolean()){phase=name;record(name,false,"Invariant failed at tick "+ticks+": "+detail.get());}}
    }
    boolean tick() throws Exception {
        running=true;ticks++;
        if(ticks>maxTicks)throw new AssertionError("Scenario exceeded "+maxTicks+" ticks at "+phase);
        int budget=64;
        while(cursor<steps.size() && budget-->0){if(!steps.get(cursor).poll())break;cursor++;}
        for(Invariant invariant:invariants)invariant.sample();
        if(cursor<steps.size())return false;
        for(Invariant invariant:invariants)record(invariant.name,true,"Held for "+invariant.samples+" observed ticks; "+invariant.detail.get());
        phase="complete";
        if(assertionCount==0)throw new AssertionError("Scenario completed without any behavior assertions");
        return true;
    }
    String phase(){return phase;}
}
