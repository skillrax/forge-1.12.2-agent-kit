package dev.agentic.workshopchecks;
import java.util.*;
import com.mojang.authlib.GameProfile;
import dev.agentic.workshop.*;
import net.minecraft.advancements.Advancement;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.block.BlockCrops;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Biomes;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.*;
import net.minecraft.server.management.PlayerInteractionManager;
import net.minecraft.tileentity.TileEntityChest;
import net.minecraft.util.*;
import net.minecraft.util.math.*;
import net.minecraft.world.WorldServer;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.chunk.Chunk;

/** Real Forge population in fresh prepared flat chunks, separate from the user default-world trial. */
public final class WorldContentChecks {
    private final WorldServer world;
    private int assertions,cx;
    private BlockPos chestPos,hole;
    private final BlockPos crop=new BlockPos(12,85,-12);
    public WorldContentChecks(WorldServer world) {this.world=world;}
    private void require(boolean ok,String message) {
        assertions++;if(!ok)throw new IllegalStateException("WORKSHOP_ASSERTIONS_FAIL: World content "+message);
    }
    private EntityPlayerMP player(String name) {
        EntityPlayerMP p=new EntityPlayerMP(world.getMinecraftServer(),world,
            new GameProfile(UUID.nameUUIDFromBytes(name.getBytes(java.nio.charset.StandardCharsets.UTF_8)),name),new PlayerInteractionManager(world)) {
                @Override public boolean canUseCommand(int level,String command) {return level<=2;}
            };
        p.connection=new NetHandlerPlayServer(world.getMinecraftServer(),new NetworkManager(EnumPacketDirection.SERVERBOUND),p);
        p.setPosition(12,85,-11);return p;
    }
    private int count(IInventory inventory,Item item) {
        int n=0;for(int i=0;i<inventory.getSizeInventory();i++)if(inventory.getStackInSlot(i).getItem()==item)n+=inventory.getStackInSlot(i).getCount();return n;
    }
    private int drops(Item item) {
        int n=0;for(EntityItem entity:world.getEntitiesWithinAABB(EntityItem.class,new AxisAlignedBB(crop).grow(2)))if(entity.getItem().getItem()==item)n+=entity.getItem().getCount();return n;
    }
    private Chunk prepare(int x,int z,Biome biome) {return prepare(x,z,biome,0);}
    private Chunk prepare(int x,int z,Biome biome,int terrain) {
        Chunk chunk=world.getChunk(x,z);require(!chunk.isTerrainPopulated(),"test chunk already populated");
        Arrays.fill(chunk.getBiomeArray(),(byte)Biome.getIdForBiome(biome));
        for(int dx=0;dx<16;dx++)for(int dz=0;dz<16;dz++)for(int y=4;y<=36;y++)
            world.setBlockState(new BlockPos(x*16+dx,y,z*16+dz),Blocks.STONE.getDefaultState(),2);
        BlockPos center=new BlockPos(x*16+8,37,z*16+8);
        if(terrain==1) {
            for(int y=0;y<5;y++)world.setBlockState(center.up(y),Blocks.LOG.getDefaultState(),2);
            for(int dx=-1;dx<=1;dx++)for(int dz=-1;dz<=1;dz++)
                world.setBlockState(center.add(dx,5,dz),Blocks.LEAVES.getDefaultState(),2);
        }else if(terrain==2)world.setBlockToAir(center.add(-1,-1,-1));
        else if(terrain==3)world.setBlockState(center.add(1,0,1),Blocks.LOG.getDefaultState(),2);
        // Loading the other3 members of the2x2 square invokes Chunk.populate -> registered Forge generators.
        world.getChunk(x+1,z);world.getChunk(x,z+1);world.getChunk(x+1,z+1);
        require(chunk.isTerrainPopulated(),"actual population did not complete");return chunk;
    }
    private int ores(int x,int z) {
        int n=0;for(int dx=0;dx<16;dx++)for(int dz=0;dz<16;dz++)for(int y=4;y<=36;y++) {
            BlockPos pos=new BlockPos(x*16+dx,y,z*16+dz);
            if(world.getBlockState(pos).getBlock()==Content.ORE) {n++;if(hole==null)hole=pos;}
        }return n;
    }
    private Advancement advancement() {
        Advancement result=world.getMinecraftServer().getAdvancementManager().getAdvancement(TrialAdvancement.ID);
        require(result!=null,"advancement resource missing");return result;
    }
    private void savePlayer(EntityPlayerMP p) {
        world.getSaveHandler().getPlayerNBTManager().writePlayerData(p);p.getAdvancements().save();
    }
    public int live() {
        for(cx=64;cx<512;cx+=8)if(!world.getChunkProvider().isChunkGeneratedAt(cx,64)
            && !world.getChunkProvider().isChunkGeneratedAt(cx+8,72)
            && !world.getChunkProvider().isChunkGeneratedAt(cx+16,80)
            && !world.getChunkProvider().isChunkGeneratedAt(cx+24,88)
            && !world.getChunkProvider().isChunkGeneratedAt(cx+32,96))break;
        require(cx<512,"fresh QA chunk budget exhausted");prepare(cx,64,Biomes.PLAINS);
        int generated=ores(cx,64);require(generated>0,"registered ore generation absent");
        BlockPos base=new BlockPos(cx*16+8,37,64*16+8);chestPos=base.east();
        require(world.getBlockState(base.up(2)).getBlock()==Blocks.GOLD_BLOCK && world.getTileEntity(chestPos) instanceof TileEntityChest,"registered marker generation absent");
        require(world.getBlockState(base.west()).getBlock()==Content.CROP && world.getBlockState(base.west().down()).getBlock()==Blocks.FARMLAND,"generated crop/soil missing");
        TileEntityChest chest=(TileEntityChest)world.getTileEntity(chestPos);
        require(count(chest,Items.EMERALD)==2 && count(chest,Items.APPLE)==3,"chest loot accounting");
        prepare(cx+8,72,Biomes.DESERT);
        require(ores(cx+8,72)==0 && world.getTileEntity(new BlockPos((cx+8)*16+9,37,72*16+8))==null,"biome restriction bypassed");
        prepare(cx+16,80,Biomes.PLAINS,1);
        BlockPos tree=new BlockPos((cx+16)*16+8,37,80*16+8);
        boolean canopyIntact=world.getBlockState(tree).getBlock()==Blocks.LOG;
        for(int dx=-1;dx<=1;dx++)for(int dz=-1;dz<=1;dz++)
            canopyIntact &= world.getBlockState(tree.add(dx,5,dz)).getBlock()==Blocks.LEAVES;
        require(canopyIntact && world.isAirBlock(tree.up(6)) && world.getTileEntity(tree.up(6).east())==null,"tree canopy marker rejection");
        require(ores(cx+16,80)>0,"tree rejection suppressed independent ore generation");
        prepare(cx+24,88,Biomes.PLAINS,2);
        BlockPos uneven=new BlockPos((cx+24)*16+8,37,88*16+8);
        require(world.isAirBlock(uneven.add(-1,-1,-1)) && world.isAirBlock(uneven)
            && world.getTileEntity(uneven.east())==null,"unsupported corner marker rejection");
        prepare(cx+32,96,Biomes.PLAINS,3);
        BlockPos obstructed=new BlockPos((cx+32)*16+8,37,96*16+8);
        require(world.getBlockState(obstructed.add(1,0,1)).getBlock()==Blocks.LOG && world.isAirBlock(obstructed)
            && world.getTileEntity(obstructed.east())==null,"obstructed footprint marker rejection");
        WorldServer nether=world.getMinecraftServer().getWorld(-1);
        require(nether!=null,"Nether test world absent");
        boolean generatedNether=nether.getChunkProvider().isChunkGeneratedAt(cx,64);
        new TrialWorldGen().generate(new Random(1),cx,64,nether,null,null);
        require(nether.getChunkProvider().isChunkGeneratedAt(cx,64)==generatedNether,"non-Overworld invocation loaded a chunk");
        // Mine one ore and partially loot the chest; restart must preserve both edits without regeneration.
        require(Content.ORE.getItemDropped(Content.ORE.getDefaultState(),new Random(1),0)==Items.EMERALD,"ore drop type");
        world.setBlockToAir(hole);
        require(ores(cx,64)==generated-1,"ore edit accounting");
        for(int i=0;i<chest.getSizeInventory();i++)if(chest.getStackInSlot(i).getItem()==Items.EMERALD) {chest.decrStackSize(i,1);break;}
        chest.markDirty();require(count(chest,Items.EMERALD)==1 && count(chest,Items.APPLE)==3,"looted chest accounting");
        EntityPlayerMP p=player("WorldContentQA");
        require(world.getMinecraftServer().getCommandManager().getCommands().get("workshopsite") instanceof WorldSiteCommand,"site command missing");
        p.setPosition(cx*16+8,85,64*16+8);
        int located=world.getMinecraftServer().getCommandManager().executeCommand(p,"workshopsite");
        require(located==1 && WorldSiteCommand.findOre(world,cx,64)!=null,"site/ore locator failed: result="+located+", permission="+new WorldSiteCommand().getRequiredPermissionLevel());
        p.setPosition(12,85,-11);
        Advancement trial=advancement();world.getMinecraftServer().getCommandManager().executeCommand(p,"workshopreset");
        require(!p.getAdvancements().getProgress(trial).isDone(),"initial advancement reset failed");
        world.setBlockToAir(crop);world.setBlockState(crop.down(),Blocks.FARMLAND.getDefaultState(),2);
        p.inventory.setInventorySlotContents(0,new ItemStack(Content.SEEDS,3));
        require(Content.SEEDS.onItemUse(p,world,crop.down(),EnumHand.MAIN_HAND,EnumFacing.UP,.5F,1F,.5F)==EnumActionResult.SUCCESS
            && world.getBlockState(crop).getBlock()==Content.CROP && p.getHeldItemMainhand().getCount()==2,"seed planting accounting");
        BlockPos invalid=crop.east(3);world.setBlockState(invalid.down(),Blocks.STONE.getDefaultState(),2);world.setBlockToAir(invalid);
        require(Content.SEEDS.onItemUse(p,world,invalid.down(),EnumHand.MAIN_HAND,EnumFacing.UP,.5F,1F,.5F)==EnumActionResult.FAIL
            && p.getHeldItemMainhand().getCount()==2,"invalid soil accepted");
        Random growth=new Random(1) {@Override public int nextInt(int bound) {return 0;}};
        for(int i=0;i<7;i++)Content.CROP.updateTick(world,crop,world.getBlockState(crop),growth);
        require(world.getBlockState(crop).getBlock()==Content.CROP && world.getBlockState(crop).getValue(BlockCrops.AGE)==7,"crop random-tick growth hook");
        int harvestBefore=drops(Content.HARVEST),seedsBefore=drops(Content.SEEDS);world.destroyBlock(crop,true);
        int matureSeeds=drops(Content.SEEDS)-seedsBefore,matureHarvest=drops(Content.HARVEST)-harvestBefore;
        require(matureHarvest==1 && matureSeeds>=0 && matureSeeds<=3,"mature harvest drops: harvest="+matureHarvest+", seeds="+matureSeeds);
        world.setBlockState(crop,Content.CROP.getDefaultState(),2);int immature=drops(Content.HARVEST);world.destroyBlock(crop,true);
        require(drops(Content.HARVEST)==immature,"immature crop yielded harvest");
        world.setBlockState(crop,Content.CROP.getDefaultState(),2);
        for(int i=0;i<4;i++)Content.CROP.grow(world,new Random(2),crop,world.getBlockState(crop));
        require(world.getBlockState(crop).getValue(BlockCrops.AGE)==7,"bonemeal growth/cap");
        // Exercise the actual farmland fall handler and its neighbor notification, not a manual crop removal.
        EntityPlayerMP trampler=player("CropTrampler");trampler.setPosition(crop.getX()+.5,crop.getY()+1,crop.getZ()+.5);
        world.setBlockState(crop,Content.CROP.getDefaultState(),2);
        int trampleSeeds=drops(Content.SEEDS),trampleHarvest=drops(Content.HARVEST);
        Blocks.FARMLAND.onFallenUpon(world,crop.down(),trampler,2F);
        require(world.isAirBlock(crop) && world.getBlockState(crop.down()).getBlock()==Blocks.DIRT
            && drops(Content.SEEDS)-trampleSeeds==1 && drops(Content.HARVEST)==trampleHarvest,"trampled immature crop/soil/drop");
        p.inventory.setInventorySlotContents(0,new ItemStack(Items.DYE,2,15));
        require(Items.DYE.onItemUse(p,world,crop,EnumHand.MAIN_HAND,EnumFacing.UP,.5F,1F,.5F)!=EnumActionResult.SUCCESS
            && p.getHeldItemMainhand().getCount()==2 && world.isAirBlock(crop)
            && world.getBlockState(crop.down()).getBlock()==Blocks.DIRT,"bonemeal regrew crop on dirt");
        world.setBlockState(crop.down(),Blocks.FARMLAND.getDefaultState(),2);world.setBlockState(crop,Content.CROP.withAge(7),2);
        trampleSeeds=drops(Content.SEEDS);trampleHarvest=drops(Content.HARVEST);
        Blocks.FARMLAND.onFallenUpon(world,crop.down(),trampler,2F);
        int matureTrampleSeeds=drops(Content.SEEDS)-trampleSeeds;
        require(world.isAirBlock(crop) && world.getBlockState(crop.down()).getBlock()==Blocks.DIRT
            && matureTrampleSeeds>=0 && matureTrampleSeeds<=3 && drops(Content.HARVEST)-trampleHarvest==1,"trampled mature crop/soil/drop");
        world.setBlockState(crop.down(),Blocks.FARMLAND.getDefaultState(),2);world.setBlockState(crop,Content.CROP.withAge(7),2);
        // Keep bonemeal out of the later simulated inventory event: vanilla recipe rewards need a real Forge handshake.
        p.inventory.setInventorySlotContents(0,ItemStack.EMPTY);
        p.inventory.setInventorySlotContents(1,new ItemStack(Content.HARVEST));CriteriaTriggers.INVENTORY_CHANGED.trigger(p,p.inventory);
        require(p.getAdvancements().getProgress(trial).isDone(),"harvest advancement trigger");
        EntityPlayerMP other=player("WorldAdvOther");TrialAdvancement.reset(other);
        require(!other.getAdvancements().getProgress(trial).isDone(),"advancement player isolation");
        p.getEntityData().setString("UnrelatedWorldTrial","retained");int harvested=count(p.inventory,Content.HARVEST);
        require(world.getMinecraftServer().getCommandManager().executeCommand(p,"workshopreset")==1
            && !p.getAdvancements().getProgress(trial).isDone() && count(p.inventory,Content.HARVEST)==harvested
            && "retained".equals(p.getEntityData().getString("UnrelatedWorldTrial")),"advancement reset/isolation/inventory");
        // Inventory predicates can retrigger if the item remains: remove it before repeating the pickup scenario.
        p.inventory.setInventorySlotContents(1,ItemStack.EMPTY);CriteriaTriggers.INVENTORY_CHANGED.trigger(p,p.inventory);savePlayer(p);
        EntityPlayerMP completed=player("WorldAdvDone");TrialAdvancement.reset(completed);
        completed.inventory.setInventorySlotContents(0,new ItemStack(Content.HARVEST));CriteriaTriggers.INVENTORY_CHANGED.trigger(completed,completed.inventory);
        require(completed.getAdvancements().getProgress(trial).isDone(),"second advancement completion");savePlayer(completed);
        System.out.println("WORLD_CONTENT_LIVE_PASS assertions="+assertions+" oreCount="+generated+" chunk="+cx+",64");return assertions;
    }
    public NBTTagCompound snapshot() {
        NBTTagCompound tag=new NBTTagCompound();tag.setInteger("ChunkX",cx);tag.setLong("ChestPos",chestPos.toLong());tag.setLong("Hole",hole.toLong());
        tag.setTag("Chest",((TileEntityChest)world.getTileEntity(chestPos)).writeToNBT(new NBTTagCompound()));tag.setInteger("OreCount",ores(cx,64));
        tag.setInteger("CropAge",world.getBlockState(crop).getValue(BlockCrops.AGE));return tag;
    }
    public int restore(NBTTagCompound tag) {
        cx=tag.getInteger("ChunkX");chestPos=BlockPos.fromLong(tag.getLong("ChestPos"));hole=BlockPos.fromLong(tag.getLong("Hole"));world.getChunk(cx,64);
        require(world.getChunk(cx,64).isTerrainPopulated(),"saved population flag lost");
        require(world.isAirBlock(hole) && ores(cx,64)==tag.getInteger("OreCount"),"ore regenerated on reload");
        require(world.getTileEntity(chestPos) instanceof TileEntityChest,"saved marker chest absent");TileEntityChest chest=(TileEntityChest)world.getTileEntity(chestPos);
        require(chest.writeToNBT(new NBTTagCompound()).equals(tag.getCompoundTag("Chest")) && count(chest,Items.EMERALD)==1 && count(chest,Items.APPLE)==3,"chest regenerated/changed on reload");
        require(world.getBlockState(crop).getBlock()==Content.CROP && world.getBlockState(crop).getValue(BlockCrops.AGE)==tag.getInteger("CropAge"),"crop disk age differs");
        Advancement trial=advancement();EntityPlayerMP reset=player("WorldContentQA"),completed=player("WorldAdvDone");
        world.getSaveHandler().getPlayerNBTManager().readPlayerData(reset);
        require(!reset.getAdvancements().getProgress(trial).isDone() && completed.getAdvancements().getProgress(trial).isDone(),"advancement reset/completion disk state");
        require("retained".equals(reset.getEntityData().getString("UnrelatedWorldTrial")),"reset unrelated player disk data");
        reset.inventory.setInventorySlotContents(1,new ItemStack(Content.HARVEST));CriteriaTriggers.INVENTORY_CHANGED.trigger(reset,reset.inventory);
        require(reset.getAdvancements().getProgress(trial).isDone(),"reset advancement disk replay");
        System.out.println("WORLD_CONTENT_RESTART_PASS assertions="+assertions);return assertions;
    }
}
