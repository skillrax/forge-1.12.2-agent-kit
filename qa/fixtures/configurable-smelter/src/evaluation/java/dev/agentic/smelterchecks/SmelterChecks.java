package dev.agentic.smelterchecks;
import dev.agentic.smelter.*;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.FurnaceRecipes;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntityChest;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.DimensionManager;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLServerStartedEvent;
import net.minecraftforge.items.CapabilityItemHandler;
import net.minecraftforge.items.IItemHandler;

@Mod(modid="smelterchecks",name="Isolated Smelter Checks",version="1",dependencies="required-after:smelterproof")
public final class SmelterChecks {
    private int checks;
    private void require(boolean ok,String reason){checks++;if(!ok)throw new IllegalStateException("SMELTER_ASSERTIONS_FAIL: "+reason);}
    private void role(SmelterTile tile,EnumFacing f,int target){while(tile.role(f)!=target)tile.control(f.getIndex());}
    private void run(SmelterTile tile,int ticks){for(int i=0;i<ticks;i++)tile.update();}
    private SmelterTile fresh(WorldServer world,BlockPos pos){
        world.setBlockToAir(pos);world.setBlockState(pos,Content.SMELTER.getDefaultState(),3);
        SmelterTile tile=(SmelterTile)world.getTileEntity(pos);tile.energy.restore(16000);return tile;
    }
    @Mod.EventHandler public void start(FMLServerStartedEvent event){
        if(!net.minecraftforge.fml.common.FMLCommonHandler.instance().getMinecraftServerInstance().isDedicatedServer())return;
        WorldServer world=DimensionManager.getWorld(0);world.getChunk(0,0);
        world.getGameRules().setOrCreateGameRule("doMobSpawning","false");
        BlockPos pos=new BlockPos(8,5,8);
        SmelterTile tile=fresh(world,pos);
        tile.setInventorySlotContents(0,new ItemStack(Blocks.COBBLESTONE));run(tile,100);
        require(tile.getStackInSlot(0).isEmpty() && tile.getStackInSlot(2).getItem()==net.minecraft.item.Item.getItemFromBlock(Blocks.STONE),"Ordinary vanilla furnace recipe");
        require(tile.spent==2000 && tile.getStackInSlot(3).isEmpty(),"Ordinary operation RF/secondary accounting");
        // Registry-added QA recipe stands in for a mod registering a basic furnace recipe.
        FurnaceRecipes.instance().addSmeltingRecipe(new ItemStack(Items.PRISMARINE_SHARD),new ItemStack(Items.DIAMOND,2),0);
        tile=fresh(world,pos);tile.setInventorySlotContents(0,new ItemStack(Items.PRISMARINE_SHARD));run(tile,100);
        require(tile.getStackInSlot(2).getItem()==Items.DIAMOND && tile.getStackInSlot(2).getCount()==2,"Dynamically registered furnace recipe/result count");
        tile=fresh(world,pos);tile.setInventorySlotContents(4,new ItemStack(Content.UPGRADE));tile.setInventorySlotContents(0,new ItemStack(Blocks.IRON_ORE));tile.setInventorySlotContents(1,new ItemStack(Blocks.SAND));run(tile,100);
        require(tile.getStackInSlot(2).getItem()==Items.IRON_INGOT && tile.getStackInSlot(2).getCount()==2 && tile.getStackInSlot(3).getItem()==Items.BRICK,"Upgraded iron outputs");
        require(tile.getStackInSlot(0).isEmpty() && tile.getStackInSlot(1).isEmpty() && tile.spent==2000,"Both inputs consumed once, exact RF");
        tile.setInventorySlotContents(2,ItemStack.EMPTY);tile.setInventorySlotContents(3,ItemStack.EMPTY);tile.setInventorySlotContents(0,new ItemStack(Blocks.GOLD_ORE));tile.setInventorySlotContents(1,new ItemStack(Blocks.SAND));run(tile,100);
        require(tile.getStackInSlot(2).getItem()==Items.GOLD_INGOT && tile.getStackInSlot(2).getCount()==2 && tile.getStackInSlot(3).getCount()==1,"Upgraded gold in same machine");
        tile=fresh(world,pos);tile.setInventorySlotContents(0,new ItemStack(Blocks.IRON_ORE));tile.setInventorySlotContents(1,new ItemStack(Blocks.SAND));run(tile,100);
        require(tile.getStackInSlot(2).getCount()==1 && tile.getStackInSlot(1).getCount()==1 && tile.getStackInSlot(3).isEmpty(),"Special recipes inaccessible without upgrade");
        tile=fresh(world,pos);tile.setInventorySlotContents(4,new ItemStack(Content.UPGRADE));tile.setInventorySlotContents(0,new ItemStack(Blocks.COBBLESTONE));run(tile,100);
        require(tile.getStackInSlot(2).getItem()==net.minecraft.item.Item.getItemFromBlock(Blocks.STONE),"Upgrade keeps ordinary recipes available");
        tile=fresh(world,pos);tile.setInventorySlotContents(0,new ItemStack(Blocks.IRON_ORE));tile.setInventorySlotContents(1,new ItemStack(Blocks.SAND));tile.setInventorySlotContents(4,new ItemStack(Content.UPGRADE));tile.setInventorySlotContents(3,new ItemStack(Items.BRICK,64));run(tile,20);
        require(tile.spent==0 && tile.getField(0)==0 && tile.getStackInSlot(0).getCount()==1 && tile.getField(12)==3,"Full secondary output pauses without spending/consuming");
        tile.setInventorySlotContents(3,ItemStack.EMPTY);run(tile,30);
        NBTTagCompound saved=tile.writeToNBT(new NBTTagCompound());SmelterTile restored=new SmelterTile();restored.readFromNBT(saved);
        require(restored.getField(0)==30 && restored.energy.getEnergyStored()==15400 && restored.upgraded(),"Inventory/energy/in-flight progress NBT roundtrip");
        run(tile,70);require(tile.completed==1 && tile.getStackInSlot(3).getCount()==1,"Blocked work resumes once");
        tile=fresh(world,pos);tile.setInventorySlotContents(0,new ItemStack(Blocks.COBBLESTONE));world.setBlockState(pos.south(),Blocks.REDSTONE_BLOCK.getDefaultState(),3);run(tile,10);
        require(tile.spent==0 && tile.getField(12)==1,"Default powered-off behavior");tile.control(8);run(tile,10);
        require(tile.spent==200 && tile.getField(0)==10,"Flip enables powered operation");world.setBlockToAir(pos.south());run(tile,10);
        require(tile.spent==200 && tile.getField(12)==1,"Flipped mode pauses without signal");tile.control(8);run(tile,90);
        require(tile.completed==1 && tile.spent==2000,"Unpowered mode resumes preserved progress");
        // Every configurable face and role, including permission/simulation behavior.
        tile=fresh(world,pos);
        for(EnumFacing face:EnumFacing.values())for(int r=0;r<=4;r++){
            role(tile,face,r);tile.clear();
            IItemHandler port=tile.getCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY,face);
            require(tile.hasCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY,face)==(r!=0),"Capability availability "+face+":"+r);
            if(r==0){require(port==null && tile.getSlotsForFace(face).length==0,"Disabled face closed");continue;}
            int slot=r-1;require(tile.getSlotsForFace(face)[0]==slot,"ISidedInventory maps assigned slot");
            ItemStack input=new ItemStack(slot==1?Blocks.SAND:Blocks.COBBLESTONE,4);
            ItemStack remainder=port.insertItem(0,input,true);
            require(remainder.getCount()==(slot<2?0:4) && tile.isEmpty() && input.getCount()==4,"Input simulation and output insertion denial");
            if(slot<2){port.insertItem(0,input,false);require(tile.getStackInSlot(slot).getCount()==4 && port.extractItem(0,4,false).isEmpty(),"Input insertion/extraction restriction");}
            else{tile.setInventorySlotContents(slot,new ItemStack(Items.BRICK,4));require(port.extractItem(0,2,true).getCount()==2 && tile.getStackInSlot(slot).getCount()==4,"Output extraction simulation");port.extractItem(0,2,false);require(tile.getStackInSlot(slot).getCount()==2,"Output committed extraction");}
        }
        require(!tile.control(9) && !tile.control(-1),"Invalid GUI action rejected");
        // Actual adjacent vanilla chests plus automatic pull/push and FE line.
        tile=fresh(world,pos);role(tile,EnumFacing.UP,1);role(tile,EnumFacing.NORTH,2);role(tile,EnumFacing.DOWN,3);role(tile,EnumFacing.EAST,4);role(tile,EnumFacing.WEST,0);role(tile,EnumFacing.SOUTH,0);
        for(EnumFacing face:new EnumFacing[]{EnumFacing.UP,EnumFacing.NORTH,EnumFacing.DOWN,EnumFacing.EAST})world.setBlockState(pos.offset(face),Blocks.CHEST.getDefaultState(),3);
        TileEntityChest ore=(TileEntityChest)world.getTileEntity(pos.up()),sand=(TileEntityChest)world.getTileEntity(pos.north()),main=(TileEntityChest)world.getTileEntity(pos.down()),second=(TileEntityChest)world.getTileEntity(pos.east());
        ore.setInventorySlotContents(0,new ItemStack(Blocks.IRON_ORE,2));sand.setInventorySlotContents(0,new ItemStack(Blocks.SAND,2));tile.setInventorySlotContents(4,new ItemStack(Content.UPGRADE));
        run(tile,10);require(ore.getStackInSlot(0).getCount()==2 && tile.getStackInSlot(0).isEmpty(),"Auto input defaults OFF");
        tile.control(6);run(tile,210);
        require(ore.getStackInSlot(0).isEmpty() && sand.getStackInSlot(0).isEmpty() && tile.getStackInSlot(2).getCount()==4 && tile.getStackInSlot(3).getCount()==2,"Auto pull two chests; exact upgraded accounting: main="+tile.getStackInSlot(2)+" secondary="+tile.getStackInSlot(3)+" progress="+tile.getField(0));
        require(main.isEmpty() && second.isEmpty(),"Auto output defaults OFF");
        for(int i=0;i<main.getSizeInventory();i++)main.setInventorySlotContents(i,new ItemStack(Blocks.DIRT,64));
        tile.control(7);run(tile,10);
        require(tile.getStackInSlot(2).getCount()==4 && second.getStackInSlot(0).getItem()==Items.BRICK && second.getStackInSlot(0).getCount()==2,"Full adjacent inventory retains main output; separate secondary drains");
        main.clear();run(tile,10);require(main.getStackInSlot(0).getItem()==Items.IRON_INGOT && main.getStackInSlot(0).getCount()==4 && tile.getStackInSlot(2).isEmpty(),"Automatic output resumes with no loss");
        tile.control(8);ore.setInventorySlotContents(0,new ItemStack(Blocks.GOLD_ORE));run(tile,10);require(ore.getStackInSlot(0).getCount()==1 && tile.getStackInSlot(0).isEmpty(),"Redstone-disabled machine also stops auto input");
        saved=tile.writeToNBT(new NBTTagCompound());restored=new SmelterTile();restored.readFromNBT(saved);
        require(restored.getField(3)==1 && restored.getField(4)==1 && restored.getField(5)==1 && restored.role(EnumFacing.NORTH)==2 && restored.role(EnumFacing.WEST)==0,"Saved controls and face settings");
        tile.control(8);world.setBlockState(pos.west(),Content.PIPE.getDefaultState(),3);world.setBlockState(pos.west(2),Content.GENERATOR.getDefaultState(),3);tile.energy.restore(0);
        GeneratorTile generator=(GeneratorTile)world.getTileEntity(pos.west(2));PipeTile pipe=(PipeTile)world.getTileEntity(pos.west());
        for(int i=0;i<10;i++){generator.update();pipe.update();}
        require(tile.energy.getEnergyStored()==400,"Reused FE power line works independently of item face assignment");
        System.out.println("SMELTER_SERVER_PASS checks="+checks+" ordinary=true registeredRecipe=true ironGold=true allFaces=true autoChests=true redstone=true blockedOutput=true nbt=true energy=true");
    }
}
