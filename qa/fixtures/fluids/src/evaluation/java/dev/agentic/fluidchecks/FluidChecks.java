package dev.agentic.fluidchecks;

import dev.agentic.fluids.*;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.CapabilityFluidHandler;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLServerStartedEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

// Isolated evaluation source set; no QA in the delivered production JAR.
@Mod(modid="fluidchecks",name="Fluid Checks",version="1",dependencies="required-after:fluidproof")
public final class FluidChecks {
    private WorldServer world;
    private MachineTile maker,pipe,consumer;
    private int checks,ticks;
    private boolean done;
    private void require(boolean ok,String message) {checks++;if(!ok)throw new IllegalStateException("FLUID_ASSERTIONS_FAIL: "+message);}
    private IFluidHandler port(MachineTile tile,EnumFacing side) {return tile.getCapability(CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY,side);}
    private void boundaries() {
        require(FluidProof.BRINE.getViscosity()==4000 && FluidProof.brineBlock.tickRate(world)==20,"Fluid configuration");
        require(port(pipe,EnumFacing.WEST)!=null && port(pipe,EnumFacing.EAST)!=null && port(pipe,EnumFacing.UP)==null,"Pipe sided capabilities");
        IFluidHandler inlet=port(pipe,EnumFacing.WEST),outlet=port(pipe,EnumFacing.EAST);
        FluidStack amount=new FluidStack(FluidProof.BRINE,1000);
        require(inlet.fill(amount,false)==1000 && pipe.tank.getFluidAmount()==0,"Fill simulation mutated tank");
        require(inlet.fill(new FluidStack(FluidRegistry.WATER,1000),true)==0,"Wrong fluid accepted");
        require(inlet.fill(amount,true)==1000 && pipe.tank.getFluidAmount()==1000,"Fill commit");
        require(inlet.drain(100,true)==null && outlet.fill(amount,true)==0,"Pipe permissions");
        require(outlet.drain(100,false).amount==100 && pipe.tank.getFluidAmount()==1000,"Drain simulation");
        require(outlet.drain(100,true).amount==100 && pipe.tank.getFluidAmount()==900,"Drain commit");
        pipe.tank.drain(4000,true);
        require(port(maker,EnumFacing.UP).fill(amount,true)==0 && port(consumer,EnumFacing.UP).drain(100,true)==null,"Machine permissions");
        BlockPos test=new BlockPos(8,80,0);
        world.setBlockState(test,FluidProof.CONSUMER.getDefaultState(),3);
        MachineTile blocked=(MachineTile)world.getTileEntity(test);
        blocked.input=new ItemStack(Blocks.COBBLESTONE);blocked.output=new ItemStack(Items.CLAY_BALL,64);
        blocked.tank.fill(amount,true);
        for(int i=0;i<40;i++)blocked.update();
        require(blocked.input.getCount()==1 && blocked.tank.getFluidAmount()==1000 && blocked.consumed==0 && blocked.progress==0,"Full output spent inputs/fluid");
        blocked.output=ItemStack.EMPTY;
        for(int i=0;i<20;i++)blocked.update();
        require(blocked.output.getCount()==1 && blocked.input.isEmpty() && blocked.tank.getFluidAmount()==750 && blocked.consumed==250,"Unblocked consumer accounting");
        blocked.input=new ItemStack(Blocks.COBBLESTONE,2);blocked.update();
        NBTTagCompound nbt=blocked.writeToNBT(new NBTTagCompound());
        MachineTile restored=new MachineTile();restored.readFromNBT(nbt);
        require(restored.tank.getFluidAmount()==750 && restored.output.getCount()==1 && restored.input.getCount()==2
            && restored.progress==1 && restored.consumed==250,"Tank/inventory/progress NBT roundtrip");
        world.setBlockToAir(test);
        world.setBlockState(test,FluidProof.PRODUCER.getDefaultState(),3);
        blocked=(MachineTile)world.getTileEntity(test);blocked.input=new ItemStack(Items.REDSTONE);
        blocked.tank.fill(new FluidStack(FluidProof.BRINE,4000),true);
        require(port(blocked,EnumFacing.UP).getTankProperties()[0].getContents().amount==4000,"Tank properties");
        for(int i=0;i<40;i++)blocked.update();
        require(blocked.generated==0 && blocked.progress==0 && blocked.input.getCount()==1 && blocked.tank.getFluidAmount()==4000,"Full producer spent input");
        blocked.tank.drain(1000,true);
        for(int i=0;i<40;i++)blocked.update();
        require(blocked.generated==1000 && blocked.input.isEmpty() && blocked.tank.getFluidAmount()==4000,"Unblocked producer accounting");
        world.setBlockToAir(test);
        consumer.tank.fill(new FluidStack(FluidProof.BRINE,4000),true);
        pipe.tank.fill(amount,true);pipe.update();
        require(pipe.tank.getFluidAmount()==1000 && consumer.tank.getFluidAmount()==4000 && pipe.transferred==0,"Full receiver lost fluid");
        pipe.tank.drain(4000,true);consumer.tank.drain(4000,true);
    }
    @Mod.EventHandler public void started(FMLServerStartedEvent event) {
        world=net.minecraftforge.common.DimensionManager.getWorld(0);world.getChunk(0,0);
        for(int x=0;x<3;x++)world.setBlockToAir(new BlockPos(x,80,0));
        world.setBlockState(new BlockPos(0,80,0),FluidProof.PRODUCER.getDefaultState(),3);
        world.setBlockState(new BlockPos(1,80,0),FluidProof.PIPE.getDefaultState(),3);
        world.setBlockState(new BlockPos(2,80,0),FluidProof.CONSUMER.getDefaultState(),3);
        maker=(MachineTile)world.getTileEntity(new BlockPos(0,80,0));pipe=(MachineTile)world.getTileEntity(new BlockPos(1,80,0));
        consumer=(MachineTile)world.getTileEntity(new BlockPos(2,80,0));boundaries();
        maker.input=new ItemStack(Items.REDSTONE,2);consumer.input=new ItemStack(Blocks.COBBLESTONE,4);
        MinecraftForge.EVENT_BUS.register(this);
    }
    @SubscribeEvent public void tick(TickEvent.ServerTickEvent event) {
        if(event.phase!=TickEvent.Phase.END || done || ++ticks<180)return;
        int stored=maker.tank.getFluidAmount()+pipe.tank.getFluidAmount()+consumer.tank.getFluidAmount();
        System.out.println("FLUID_PROOF_STATE generated="+maker.generated+" consumed="+consumer.consumed+" maker="+maker.tank.getFluidAmount()
            +" pipe="+pipe.tank.getFluidAmount()+" consumer="+consumer.tank.getFluidAmount()+" output="+consumer.output.getCount());
        require(maker.generated==2000 && maker.input.isEmpty() && maker.completed==2,"Live producer");
        require(consumer.completed==4 && consumer.input.isEmpty() && consumer.output.getCount()==4 && consumer.consumed==1000,"Live consumer recipe");
        require(maker.generated==stored+consumer.consumed && stored==1000,"Line conservation");
        require(maker.transferred==2000 && pipe.transferred==2000 && maker.tank.getFluidAmount()==0 && pipe.tank.getFluidAmount()==0,"Live straight transfer");
        System.out.println("FLUID_PROOF_RESULT ticks="+ticks+" generated=2000 consumed=1000 stored="+stored+" clay=4 checks="+checks);
        System.out.println("FLUID_LIVE_PASS");done=true;
    }
}
