package dev.agentic.configmachinechecks;
import com.example.configmachine.*;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.FurnaceRecipes;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntityChest;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldServer;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLServerStartedEvent;
import net.minecraftforge.items.CapabilityItemHandler;
import net.minecraftforge.items.IItemHandler;

@Mod(modid="configmachinechecks",name="Isolated Starter Checks",version="1",dependencies="required-after:configmachine")
public final class StarterChecks {
    private int checks;
    private void require(boolean ok,String reason){checks++;if(!ok)throw new IllegalStateException("SMELTER_ASSERTIONS_FAIL: "+reason);}
    private void run(MachineTile tile,int count){for(int i=0;i<count;i++)tile.update();}
    private MachineTile fresh(WorldServer world,BlockPos pos){world.setBlockToAir(pos);world.setBlockState(pos,Content.MACHINE.getDefaultState(),3);MachineTile t=(MachineTile)world.getTileEntity(pos);t.energy.restore(MachineSettings.CAPACITY);return t;}
    @Mod.EventHandler public void start(FMLServerStartedEvent e){
        if(!net.minecraftforge.fml.common.FMLCommonHandler.instance().getMinecraftServerInstance().isDedicatedServer())return;
        WorldServer world=net.minecraftforge.common.DimensionManager.getWorld(0);world.getChunk(0,0);BlockPos pos=new BlockPos(8,5,8);
        MachineTile tile=fresh(world,pos);tile.setInventorySlotContents(0,new ItemStack(Blocks.IRON_ORE));tile.setInventorySlotContents(1,new ItemStack(Blocks.SAND));run(tile,100);
        require(tile.getStackInSlot(2).getCount()==1 && tile.getStackInSlot(1).getCount()==1 && tile.getStackInSlot(3).isEmpty(),"Ordinary fallback without upgrade");
        tile=fresh(world,pos);tile.setInventorySlotContents(4,new ItemStack(Content.UPGRADE));tile.setInventorySlotContents(0,new ItemStack(Blocks.GOLD_ORE));tile.setInventorySlotContents(1,new ItemStack(Blocks.SAND));run(tile,100);
        require(tile.getStackInSlot(2).getItem()==Items.GOLD_INGOT && tile.getStackInSlot(2).getCount()==2 && tile.getStackInSlot(3).getCount()==1,"Promoted example custom entry");
        // Non-smelter materials/counts exercise the new data-entry boundary, QA-only.
        MachineRecipes.CUSTOM.add(new MachineRecipes.Entry("counts",new ItemStack(Blocks.COBBLESTONE,3),new ItemStack(Blocks.SAND,2),new ItemStack(Blocks.STONE,4),new ItemStack(Items.FLINT),false));
        tile=fresh(world,pos);tile.setInventorySlotContents(0,new ItemStack(Blocks.COBBLESTONE,3));tile.setInventorySlotContents(1,new ItemStack(Blocks.SAND,2));run(tile,30);
        NBTTagCompound saved=tile.writeToNBT(new NBTTagCompound());MachineTile restored=new MachineTile();restored.readFromNBT(saved);
        require(restored.getField(0)==30 && restored.energy.getEnergyStored()==15400,"Configured operation identity/progress saved");
        run(tile,70);require(tile.getStackInSlot(0).isEmpty() && tile.getStackInSlot(1).isEmpty() && tile.getStackInSlot(2).getCount()==4 && tile.getStackInSlot(3).getItem()==Items.FLINT && tile.spent==2000,"Generic count accounting independent of materials");
        tile=fresh(world,pos);tile.setInventorySlotContents(0,new ItemStack(Blocks.COBBLESTONE,3));tile.setInventorySlotContents(1,new ItemStack(Blocks.SAND,2));tile.setInventorySlotContents(3,new ItemStack(Items.FLINT,64));run(tile,10);
        require(tile.spent==0 && tile.getStackInSlot(0).getCount()==3,"Configured secondary output blocks without loss");tile.setInventorySlotContents(3,ItemStack.EMPTY);run(tile,100);require(tile.completed==1,"Configured recipe resumes");
        FurnaceRecipes.instance().addSmeltingRecipe(new ItemStack(Items.PRISMARINE_SHARD),new ItemStack(Items.DIAMOND,2),0);
        tile=fresh(world,pos);tile.setInventorySlotContents(0,new ItemStack(Items.PRISMARINE_SHARD));run(tile,100);require(tile.getStackInSlot(2).getCount()==2 && tile.getStackInSlot(2).getItem()==Items.DIAMOND,"Live furnace registry retained");
        tile=fresh(world,pos);tile.control(7);while(tile.role(EnumFacing.EAST)!=3)tile.control(EnumFacing.EAST.getIndex());world.setBlockState(pos.east(),Blocks.CHEST.getDefaultState(),3);TileEntityChest chest=(TileEntityChest)world.getTileEntity(pos.east());chest.clear();tile.setInventorySlotContents(2,new ItemStack(Items.IRON_INGOT,8));run(tile,10);
        require(chest.getStackInSlot(0).getCount()==8 && tile.getStackInSlot(2).isEmpty() && tile.getField(19)==9,"Renamed active output and diagnostics");
        IItemHandler port=tile.getCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY,EnumFacing.EAST);tile.control(EnumFacing.EAST.getIndex());tile.control(EnumFacing.EAST.getIndex());tile.control(EnumFacing.EAST.getIndex());
        require(tile.role(EnumFacing.EAST)==1 && port.insertItem(0,new ItemStack(Blocks.COBBLESTONE),true).isEmpty() && tile.getStackInSlot(0).isEmpty(),"Held capability observes live role and preserves simulation");
        saved=tile.writeToNBT(new NBTTagCompound());restored=new MachineTile();restored.readFromNBT(saved);require(restored.role(EnumFacing.EAST)==1 && restored.getField(4)==1,"Renamed controls persist");
        require(!tile.control(9),"Action range preserved");
        System.out.println("SMELTER_SERVER_PASS starter=true checks="+checks+" configuredCounts=true furnaceRegistry=true blockedResume=true savedKey=true livePorts=true autoOutput=true diagnostics=true");
    }
}
