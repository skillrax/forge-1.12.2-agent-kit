package dev.agentic.smelterchecks;
import dev.agentic.smelter.*;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntityChest;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.DimensionManager;
import net.minecraftforge.common.ForgeChunkManager;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLServerStartedEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

// Targeted real-tick reproduction of the user's EAST-output chest setup.
@Mod(modid="smelterchecks",name="Isolated Output Checks",version="1",dependencies="required-after:smelterproof")
@Mod.EventBusSubscriber(modid="smelterchecks")
public final class OutputChecks {
    @Mod.EventHandler public void preInit(FMLPreInitializationEvent event){ForgeChunkManager.setForcedChunkLoadingCallback(this,(saved,loadedWorld)->{});}
    private static WorldServer world;
    private static SmelterTile tile;
    private static TileEntityChest chest;
    private static ForgeChunkManager.Ticket ticket;
    private static final BlockPos POS=new BlockPos(8,5,8);
    private static int ticks,checks,collected;
    private static void require(boolean ok,String message){checks++;if(!ok)throw new IllegalStateException("SMELTER_ASSERTIONS_FAIL: "+message+" tick="+ticks+" main="+tile.getStackInSlot(2)+" eastStatus="+tile.getField(19));}
    private static void role(EnumFacing face,int value){while(tile.role(face)!=value)tile.control(face.getIndex());}
    private static int count(TileEntityChest inventory){int total=0;for(int i=0;i<inventory.getSizeInventory();i++)if(inventory.getStackInSlot(i).getItem()==Items.IRON_INGOT)total+=inventory.getStackInSlot(i).getCount();return total;}
    @Mod.EventHandler public void start(FMLServerStartedEvent event){
        if(!net.minecraftforge.fml.common.FMLCommonHandler.instance().getMinecraftServerInstance().isDedicatedServer())return;
        world=DimensionManager.getWorld(0);world.getChunk(0,0);
        ticket=ForgeChunkManager.requestTicket(this,world,ForgeChunkManager.Type.NORMAL);
        if(ticket==null)throw new IllegalStateException("SMELTER_ASSERTIONS_FAIL: no ticking fixture ticket");
        ForgeChunkManager.forceChunk(ticket,new net.minecraft.util.math.ChunkPos(POS));
        world.setBlockToAir(POS);world.setBlockState(POS,Content.SMELTER.getDefaultState(),3);tile=(SmelterTile)world.getTileEntity(POS);
        for(EnumFacing face:new EnumFacing[]{EnumFacing.UP,EnumFacing.NORTH,EnumFacing.EAST})world.setBlockState(POS.offset(face),Blocks.CHEST.getDefaultState(),3);
        ((TileEntityChest)world.getTileEntity(POS.up())).setInventorySlotContents(0,new ItemStack(Blocks.IRON_ORE,64));
        ((TileEntityChest)world.getTileEntity(POS.north())).setInventorySlotContents(0,new ItemStack(Blocks.SAND,64));
        chest=(TileEntityChest)world.getTileEntity(POS.east());chest.clear();
        world.setBlockToAir(POS.south());role(EnumFacing.NORTH,2);role(EnumFacing.EAST,3);role(EnumFacing.DOWN,0);role(EnumFacing.WEST,0);
        tile.energy.restore(16000);tile.setInventorySlotContents(4,new ItemStack(Content.UPGRADE));tile.control(6);tile.control(7);
    }
    @SubscribeEvent public static void tick(TickEvent.ServerTickEvent event){
        if(world==null || event.phase!=TickEvent.Phase.END)return;
        ticks++;
        if(ticks==120){
            require(tile.completed==1 && count(chest)==2 && tile.getStackInSlot(2).isEmpty(),"First EAST output drains on normal world ticks");
            collected+=count(chest);world.setBlockToAir(POS.east());world.setBlockState(POS.east(),Blocks.CHEST.getDefaultState(),3);chest=(TileEntityChest)world.getTileEntity(POS.east());
        }
        if(ticks==220){
            require(tile.completed==2 && count(chest)==2 && tile.getStackInSlot(2).isEmpty(),"Replaced adjacent chest reacquired without role cycling");
            tile.setInventorySlotContents(2,new ItemStack(Items.IRON_INGOT,8));role(EnumFacing.EAST,1);
        }
        if(ticks==240){require(tile.getStackInSlot(2).getCount()==8,"Blue EAST main input cannot auto-export");role(EnumFacing.EAST,3);}
        if(ticks==260){
            require(tile.getStackInSlot(2).isEmpty() && count(chest)==10,"EAST restored to orange main output resumes");
            collected+=count(chest);for(int i=0;i<chest.getSizeInventory();i++)chest.setInventorySlotContents(i,new ItemStack(Blocks.DIRT,64));
        }
        if(ticks==320){
            require(tile.getStackInSlot(2).getCount()==2 && tile.getField(19)==8,"Full chest reports rejection and retains output");chest.clear();
        }
        if(ticks==340){
            require(tile.getStackInSlot(2).isEmpty() && count(chest)==2,"Cleared chest resumes automatically without cycling");
            NBTTagCompound saved=tile.writeToNBT(new NBTTagCompound());SmelterTile restored=new SmelterTile();restored.readFromNBT(saved);world.setTileEntity(POS,restored);tile=restored;
        }
        if(ticks==420){
            require(tile.completed==4 && count(chest)+collected==16 && tile.getStackInSlot(2).isEmpty(),"Restored tile keeps EAST output active; total iron conserved including eight injected test ingots");
            require(tile.getStackInSlot(3).getCount()==4 && tile.role(EnumFacing.EAST)==3,"Secondary count and saved EAST role");
            require(tile.getField(19)==3 || tile.getField(19)==9,"Output status synchronized as empty/moved rather than stalled");
            System.out.println("SMELTER_SERVER_PASS outputFocus=true checks="+checks+" normalTicks=420 operations=4 eastOutput=true chestReplacement=true roleCycle=true blockedResume=true tileRestore=true accounting=true stallReproduced=false");
            ForgeChunkManager.releaseTicket(ticket);world=null;
        }
    }
}
