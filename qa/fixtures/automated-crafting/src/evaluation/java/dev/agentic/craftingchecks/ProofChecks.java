package dev.agentic.craftingchecks;
import dev.agentic.crafting.*;
import net.minecraft.init.*;
import net.minecraft.item.*;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLServerStartedEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

@Mod(modid="craftingchecks",name="Crafting Checks",version="1",dependencies="required-after:craftingproof")
public final class ProofChecks {
    public static final BlockPos POS=new BlockPos(2,4,0);
    private WorldServer world;private ProcessorTile live;private int ticks;private boolean done;
    public static int checks;
    public static void require(boolean value,String message) {
        checks++;if(!value)throw new IllegalStateException("CRAFTING_ASSERTIONS_FAIL: "+message);
    }
    public static void ordinary(ProcessorTile tile,int n) {
        tile.select(0,new ItemStack(Blocks.LOG));tile.setInventorySlotContents(0,new ItemStack(Blocks.LOG,n));
    }
    public static void cake(ProcessorTile tile) {
        Item[] items={Items.MILK_BUCKET,Items.MILK_BUCKET,Items.MILK_BUCKET,Items.SUGAR,Items.EGG,Items.SUGAR,Items.WHEAT,Items.WHEAT,Items.WHEAT};
        tile.control(1);for(int i=0;i<9;i++){tile.select(i,new ItemStack(items[i]));tile.setInventorySlotContents(i,new ItemStack(items[i]));}
    }
    public static int count(ProcessorTile tile,Item item) {
        int n=0;for(int i=9;i<19;i++)if(tile.getStackInSlot(i).getItem()==item)n+=tile.getStackInSlot(i).getCount();return n;
    }
    private void boundaries() {
        BlockPos pos=new BlockPos(8,4,0);world.setBlockState(pos,Content.PROCESSOR.getDefaultState());
        ProcessorTile t=(ProcessorTile)world.getTileEntity(pos);ordinary(t,2);
        require(t.preview().getCount()==4 && t.selectedRecipe().getRegistryName()!=null,"registered ordinary recipe");
        t.control(0);for(int i=0;i<30;i++)t.update();require(t.spent==0 && t.completed==0,"no power");
        t.energy.restore(4000);for(int i=9;i<19;i++)t.setInventorySlotContents(i,new ItemStack(Blocks.STONE,64));
        for(int i=0;i<25;i++)t.update();require(t.spent==0 && t.getStackInSlot(0).getCount()==2 && t.getField(0)==0,"full outputs pause");
        t.setInventorySlotContents(9,ItemStack.EMPTY);for(int i=0;i<5;i++)t.update();
        require(t.getField(0)==5 && t.spent==20,"starts after room");
        t.setInventorySlotContents(9,new ItemStack(Blocks.STONE,64));for(int i=0;i<25;i++)t.update();
        require(t.getField(0)==5 && t.spent==20 && t.completed==0,"mid-work blocking pauses RF and progress");
        t.setInventorySlotContents(9,ItemStack.EMPTY);
        NBTTagCompound saved=t.writeToNBT(new NBTTagCompound());ProcessorTile restored=new ProcessorTile();restored.readFromNBT(saved);
        require(restored.getField(0)==5 && restored.getField(2)==1 && restored.energy.getEnergyStored()==3980
            && restored.pattern.getStackInSlot(0).getItem()==Item.getItemFromBlock(Blocks.LOG),"NBT pattern/control/progress/RF");
        world.setTileEntity(pos,restored);t=restored;
        for(int i=0;i<15;i++)t.update();require(t.completed==1 && t.spent==80 && t.getStackInSlot(0).getCount()==1
            && count(t,Item.getItemFromBlock(Blocks.PLANKS))==4,"restored work exact accounting");
        for(int i=0;i<5;i++)t.update();t.getStackInSlot(0).grow(1);t.update();
        require(t.getField(0)==1,"input count mutation resets work");
        t.select(0,new ItemStack(Blocks.LOG));require(t.getField(0)==0,"pattern edit resets immediately");
        t.control(0);long before=t.spent;for(int i=0;i<30;i++)t.update();require(t.spent==before,"pause control");
        for(int i=0;i<19;i++)t.setInventorySlotContents(i,ItemStack.EMPTY);cake(t);
        require(t.preview().getItem()==Items.CAKE,"registered cake recipe");
        for(int i=9;i<19;i++)t.setInventorySlotContents(i,new ItemStack(Blocks.STONE,64));
        t.setInventorySlotContents(9,ItemStack.EMPTY);t.control(0);before=t.spent;
        for(int i=0;i<25;i++)t.update();require(t.spent==before && t.getStackInSlot(0).getItem()==Items.MILK_BUCKET
            && count(t,Items.CAKE)==0,"result fits but remainders block: no partial commit");
        t.setInventorySlotContents(10,new ItemStack(Items.BUCKET,15));
        for(int i=0;i<25;i++)t.update();require(t.spent==before,"partial remainder room insufficient (bucket limit 16)");
        t.setInventorySlotContents(11,ItemStack.EMPTY);for(int i=0;i<20;i++)t.update();
        require(count(t,Items.CAKE)==1 && count(t,Items.BUCKET)==18 && t.spent==before+80,"cake + 3 bucket returns exactly once");
        for(int i=0;i<9;i++)require(t.getStackInSlot(i).isEmpty(),"cake input consumed once slot "+i);
        before=t.spent;for(int i=0;i<25;i++)t.update();require(t.spent==before && count(t,Items.CAKE)==1,"no duplicate completion");
        t.clear();world.setBlockToAir(pos);
        System.out.println("CRAFTING_BOUNDARIES_PASS checks="+checks+" ordinary=4 cake=1 returnedBuckets=3 costEach=80 blockedRF=0 nbtResume=true");
    }
    private void fuzzy() {
        BlockPos pos=new BlockPos(8,4,0);world.setBlockState(pos,Content.PROCESSOR.getDefaultState());
        ProcessorTile t=(ProcessorTile)world.getTileEntity(pos);ordinary(t,1);t.energy.restore(4000);t.control(0);
        NBTTagCompound playerTag=new NBTTagCompound();playerTag.setBoolean("PlayerTouched",true);
        ItemStack ghost=new ItemStack(Blocks.LOG);ghost.setTagCompound(playerTag);t.select(0,ghost);
        for(int i=0;i<25;i++)t.update();require(t.completed==0 && t.spent==0,"strict NBT blocks player-tagged ghost/plain input");
        t.control(2);for(int i=0;i<20;i++)t.update();
        require(t.completed==1 && count(t,Item.getItemFromBlock(Blocks.PLANKS))==4 && !t.getStackInSlot(9).hasTagCompound(),"ignore NBT crafts plain recipe output without ghost tags");
        t.clear();t.select(0,new ItemStack(Blocks.LOG));t.setInventorySlotContents(0,new ItemStack(Blocks.LOG,1,1));
        long before=t.spent;for(int i=0;i<25;i++)t.update();require(t.spent==before,"NBT ignore does not imply metadata ignore");
        t.control(3);for(int i=0;i<5;i++)t.update();require(t.getField(0)==5,"metadata fuzzy starts actual spruce variant");
        NBTTagCompound saved=t.writeToNBT(new NBTTagCompound());ProcessorTile restored=new ProcessorTile();restored.readFromNBT(saved);
        require(restored.getField(4)==1 && restored.getField(5)==1 && restored.getField(0)==5,"fuzzy flags and progress persist");
        world.setTileEntity(pos,restored);t=restored;for(int i=0;i<15;i++)t.update();
        require(t.getStackInSlot(9).getMetadata()==1 && t.getStackInSlot(9).getCount()==4 && t.spent==before+80,"registered actual spruce result, not oak preview");
        t.clear();ItemStack token=new ItemStack(Items.DIAMOND);NBTTagCompound data=new NBTTagCompound();data.setBoolean("Token",true);data.setString("Payload","real input");token.setTagCompound(data);
        ItemStack sample=token.copy();sample.getTagCompound().setString("Payload","player ghost");t.select(0,sample);t.setInventorySlotContents(0,token);
        for(int i=9;i<19;i++)t.setInventorySlotContents(i,new ItemStack(Blocks.STONE,64));
        ItemStack other=new ItemStack(Items.PAPER);other.setTagCompound(playerTag.copy());t.setInventorySlotContents(9,other);
        before=t.spent;for(int i=0;i<25;i++)t.update();require(t.spent==before && t.getStackInSlot(0).getTagCompound().equals(data),"fuzzy keeps output stacking strict and real input tags intact while blocked");
        t.setInventorySlotContents(9,ItemStack.EMPTY);t.setInventorySlotContents(10,ItemStack.EMPTY);for(int i=0;i<20;i++)t.update();
        require(t.getStackInSlot(9).getTagCompound().equals(data) && t.getStackInSlot(10).getTagCompound().equals(data)
            && t.getStackInSlot(0).isEmpty() && t.spent==before+80,"registered result/remainder preserve actual NBT");
        t.clear();t.setInventorySlotContents(0,new ItemStack(Items.DIAMOND));before=t.spent;
        for(int i=0;i<25;i++)t.update();require(t.getField(3)==6 && t.spent==before && t.getStackInSlot(0).getCount()==1,"NBT-sensitive registered recipe rejects missing token despite fuzzy");
        t.setInventorySlotContents(0,new ItemStack(Items.EMERALD));t.update();require(t.getField(3)==2 && t.spent==before,"fuzzy never ignores item identity");
        t.setInventorySlotContents(0,token.copy());for(int i=0;i<5;i++)t.update();t.control(2);require(t.getField(0)==0,"matching policy change resets progress");
        t.clear();world.setBlockToAir(pos);
        System.out.println("CRAFTING_FUZZY_PASS strictThenIgnoreNBT=true metadataVariant=true persisted=true actualDataPreserved=true recipeGate=true checks="+checks);
    }
    @Mod.EventHandler public void started(FMLServerStartedEvent event) {
        world=net.minecraftforge.common.DimensionManager.getWorld(0);world.getChunk(0,0);
        for(int x=0;x<3;x++)world.setBlockToAir(new BlockPos(x,4,0));
        world.setBlockState(new BlockPos(0,4,0),Content.GENERATOR.getDefaultState());
        world.setBlockState(new BlockPos(1,4,0),Content.PIPE.getDefaultState());world.setBlockState(POS,Content.PROCESSOR.getDefaultState());
        live=(ProcessorTile)world.getTileEntity(POS);
        boundaries();
        fuzzy();
        // Integrated graphical QA starts paused and uses actual container packets.
        if(!world.getMinecraftServer().isDedicatedServer()){ordinary(live,2);MinecraftForge.EVENT_BUS.register(this);return;}
        ordinary(live,2);live.control(0);MinecraftForge.EVENT_BUS.register(this);
    }
    @SubscribeEvent public void tick(TickEvent.ServerTickEvent event) {
        if(event.phase!=TickEvent.Phase.END || done || !world.getMinecraftServer().isDedicatedServer())return;
        ticks++;
        if(ticks==60){require(live.completed==2 && count(live,Item.getItemFromBlock(Blocks.PLANKS))==8 && live.spent==160,"ordinary real world ticks");cake(live);}
        if(ticks==100){require(live.completed==3 && count(live,Items.CAKE)==1 && count(live,Items.BUCKET)==3 && live.spent==240,"cake real world ticks");
            System.out.println("CRAFTING_LIVE_PASS ticks="+ticks+" crafts=3 planks=8 cake=1 buckets=3 spent=240 checks="+checks);done=true;}
    }
}
