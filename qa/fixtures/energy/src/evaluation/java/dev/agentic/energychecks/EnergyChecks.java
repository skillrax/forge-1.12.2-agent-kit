package dev.agentic.energychecks;

import dev.agentic.energy.*;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.energy.CapabilityEnergy;
import net.minecraftforge.energy.IEnergyStorage;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLServerStartedEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

// QA-only source set: one small live line and focused boundary checks.
@Mod(modid="energychecks",name="Energy Proof Checks",version="1",dependencies="required-after:energyproof")
public final class EnergyChecks {
    private GeneratorTile generator;
    private PipeTile pipe;
    private ProcessorTile processor;
    private WorldServer world;
    private int ticks,checks;
    private boolean done;
    private int gravelFlint;
    private void require(boolean value,String message) {
        checks++;if(!value)throw new IllegalStateException("ENERGY_ASSERTIONS_FAIL: "+message);
    }
    private void boundaries() {
        require(generator.hasCapability(CapabilityEnergy.ENERGY,EnumFacing.EAST),"Generator capability missing");
        require(!generator.hasCapability(CapabilityEnergy.ENERGY,EnumFacing.WEST),"Generator wrong side exposed");
        IEnergyStorage source=generator.getCapability(CapabilityEnergy.ENERGY,EnumFacing.EAST);
        generator.energy.generate(200);
        require(source.extractEnergy(500,true)==128 && source.getEnergyStored()==200,"Extract simulation/rate");
        require(source.extractEnergy(500,false)==128 && source.getEnergyStored()==72,"Committed extraction");
        require(source.receiveEnergy(10,false)==0,"Generator accepted input");
        generator.energy.restore(0);
        IEnergyStorage input=pipe.getCapability(CapabilityEnergy.ENERGY,EnumFacing.WEST);
        IEnergyStorage output=pipe.getCapability(CapabilityEnergy.ENERGY,EnumFacing.EAST);
        require(input.canReceive() && !input.canExtract() && output.canExtract() && !output.canReceive(),"Pipe sided ports");
        require(pipe.getCapability(CapabilityEnergy.ENERGY,EnumFacing.UP)==null,"Pipe wrong side exposed");
        require(input.receiveEnergy(500,true)==80 && input.getEnergyStored()==0,"Receive simulation/rate");
        require(input.receiveEnergy(500,false)==80 && input.getEnergyStored()==80,"Committed receive");
        require(output.receiveEnergy(10,false)==0 && input.extractEnergy(10,false)==0,"Pipe port direction");
        pipe.energy.restore(250);
        require(input.receiveEnergy(80,false)==6 && input.getEnergyStored()==256,"Capacity bound");
        pipe.energy.restore(0);
        require(processor.getCapability(CapabilityEnergy.ENERGY,EnumFacing.WEST).canReceive()
            && !processor.getCapability(CapabilityEnergy.ENERGY,EnumFacing.WEST).canExtract(),"Machine input capability");

        BlockPos test=new BlockPos(8,80,0);
        world.setBlockState(test,Content.PROCESSOR.getDefaultState(),3);
        ProcessorTile blocked=(ProcessorTile)world.getTileEntity(test);
        blocked.setInventorySlotContents(0,new ItemStack(Blocks.COBBLESTONE,2));
        for(int i=0;i<40;i++)blocked.update();
        require(blocked.completed==0 && blocked.getField(0)==0 && blocked.spent==0,"No-energy work");
        blocked.energy.receiveEnergy(100,false);
        blocked.setInventorySlotContents(1,new ItemStack(Blocks.GRAVEL,64));
        for(int i=0;i<40;i++)blocked.update();
        require(blocked.spent==0 && blocked.energy.getEnergyStored()==100 && blocked.getStackInSlot(0).getCount()==2,"Primary blocked consumption");
        blocked.setInventorySlotContents(1,ItemStack.EMPTY);
        blocked.setInventorySlotContents(2,new ItemStack(Items.FLINT,64));
        for(int i=0;i<40;i++)blocked.update();
        require(blocked.spent==0 && blocked.getField(0)==0 && blocked.getStackInSlot(0).getCount()==2,"Secondary blocked consumption");
        blocked.setInventorySlotContents(2,ItemStack.EMPTY);
        for(int i=0;i<20;i++)blocked.update();
        require(blocked.completed==1 && blocked.spent==80 && blocked.energy.getEnergyStored()==20
            && blocked.getStackInSlot(1).getCount()==1 && blocked.getStackInSlot(0).getCount()==1,"Unblocked resume/accounting");
        NBTTagCompound saved=blocked.writeToNBT(new NBTTagCompound());
        ProcessorTile restored=new ProcessorTile();restored.readFromNBT(saved);
        require(restored.energy.getEnergyStored()==20 && restored.spent==80 && restored.completed==1
            && restored.getStackInSlot(1).getCount()==1,"Machine NBT roundtrip");
        ProcessingRecipe recipe=ProcessorTile.recipe(new ItemStack(Blocks.COBBLESTONE));
        require(recipe.producesSecondary(.199F) && !recipe.producesSecondary(.20F),"Chance boundary");
        ProcessingRecipe stoneRecipe=ProcessorTile.recipe(new ItemStack(Blocks.STONE));
        require(stoneRecipe!=null && stoneRecipe.producesSecondary(.499F) && !stoneRecipe.producesSecondary(.50F),"Second recipe chance boundary");
        world.setBlockToAir(test);
    }
    @Mod.EventHandler public void started(FMLServerStartedEvent event) {
        world=net.minecraftforge.common.DimensionManager.getWorld(0);
        world.getChunk(0,0);
        for(int x=0;x<3;x++)world.setBlockToAir(new BlockPos(x,80,0));
        world.setBlockState(new BlockPos(0,80,0),Content.GENERATOR.getDefaultState(),3);
        world.setBlockState(new BlockPos(1,80,0),Content.PIPE.getDefaultState(),3);
        world.setBlockState(new BlockPos(2,80,0),Content.PROCESSOR.getDefaultState(),3);
        generator=(GeneratorTile)world.getTileEntity(new BlockPos(0,80,0));
        pipe=(PipeTile)world.getTileEntity(new BlockPos(1,80,0));
        processor=(ProcessorTile)world.getTileEntity(new BlockPos(2,80,0));
        boundaries();processor.setInventorySlotContents(0,new ItemStack(Blocks.COBBLESTONE,4));
        MinecraftForge.EVENT_BUS.register(this);
    }
    @SubscribeEvent public void tick(TickEvent.ServerTickEvent event) {
        // Empty dedicated worlds stop updating tiles after 300 ticks. Stay below that boundary.
        if(event.phase!=TickEvent.Phase.END || done)return;
        ++ticks;
        if(ticks==100) {
            require(processor.completed==4 && processor.getStackInSlot(0).isEmpty()
                && processor.getStackInSlot(1).getItem()==net.minecraft.item.Item.getItemFromBlock(Blocks.GRAVEL)
                && processor.getStackInSlot(1).getCount()==4 && processor.spent==320,"First live recipe");
            gravelFlint=processor.getStackInSlot(2).getCount();
            require(gravelFlint==processor.secondaryProduced && gravelFlint<=4,"First secondary accounting");
            processor.setInventorySlotContents(1,ItemStack.EMPTY);
            processor.setInventorySlotContents(2,ItemStack.EMPTY);
            processor.setInventorySlotContents(0,new ItemStack(Blocks.STONE,4));
        }
        if(ticks<200)return;
        System.out.println("ENERGY_PROOF_STATE recipes="+processor.completed+" input="+processor.getStackInSlot(0).getCount()
            +" primary="+processor.getStackInSlot(1).getCount()+" generated="+generator.generated
            +" generatorRF="+generator.energy.getEnergyStored()+" pipeRF="+pipe.energy.getEnergyStored()
            +" processorRF="+processor.energy.getEnergyStored()+" loaded="+world.isBlockLoaded(generator.getPos()));
        require(processor.completed==8 && processor.getStackInSlot(0).isEmpty()
            && processor.getStackInSlot(1).getItem()==net.minecraft.item.Item.getItemFromBlock(Blocks.COBBLESTONE)
            && processor.getStackInSlot(1).getCount()==4,"Live recipe switching in same machine");
        require(processor.spent==640,"Live recipe energy cost");
        long stored=generator.energy.getEnergyStored()+pipe.energy.getEnergyStored()+processor.energy.getEnergyStored();
        require(generator.generated==stored+processor.spent,"Line energy conservation");
        int extraCobble=processor.getStackInSlot(2).getCount();
        require(extraCobble+gravelFlint==processor.secondaryProduced && extraCobble<=4
            && (extraCobble==0 || processor.getStackInSlot(2).getItem()==net.minecraft.item.Item.getItemFromBlock(Blocks.COBBLESTONE)),"Second secondary accounting");
        System.out.println("ENERGY_PROOF_RESULT ticks="+ticks+" recipes="+processor.completed+" gravel=4 flint="+gravelFlint+" cobble=4 extraCobble="+extraCobble
            +" generated="+generator.generated+" stored="+stored+" spent="+processor.spent+" checks="+checks);
        System.out.println("ENERGY_LIVE_PASS");done=true;
    }
}
