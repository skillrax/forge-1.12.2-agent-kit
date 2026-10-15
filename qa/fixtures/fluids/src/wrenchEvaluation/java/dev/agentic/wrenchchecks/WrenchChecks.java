package dev.agentic.wrenchchecks;

import java.util.List;
import dev.agentic.fluids.*;
import net.minecraft.block.Block;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLServerStartedEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

// Focused server-world item use/removal/placement; previous fluid campaign is not repeated.
@Mod(modid="wrenchchecks",name="Wrench Checks",version="1",dependencies="required-after:fluidproof")
public final class WrenchChecks {
    private int checks;
    private boolean announced;
    private void require(boolean ok,String message) {checks++;if(!ok)throw new IllegalStateException("WRENCH_ASSERTIONS_FAIL: "+message);}
    private List<EntityItem> drops(WorldServer world,BlockPos pos) {
        return world.getEntitiesWithinAABB(EntityItem.class,new AxisAlignedBB(pos).grow(2),entity->!entity.isDead);
    }
    private void exercise(WorldServer world,EntityPlayerMP player,Block block,int kind) {
        BlockPos from=new BlockPos(8,80,8),to=new BlockPos(12,80,8);
        world.setBlockToAir(from);world.setBlockToAir(to);world.setBlockState(to.down(),Blocks.STONE.getDefaultState(),3);
        // The diagnostic workspace may be reused after a failed assertion; isolate this fixture's drops.
        for(EntityItem entity:drops(world,from))entity.setDead();
        for(EntityItem entity:drops(world,to))entity.setDead();
        world.setBlockState(from,block.getDefaultState(),3);
        MachineTile tile=(MachineTile)world.getTileEntity(from);
        tile.tank.fill(new FluidStack(FluidProof.BRINE,1234),true);
        if(kind!=1)tile.input=new ItemStack(tile.ingredient(),2);
        if(kind==2)tile.output=new ItemStack(Items.CLAY_BALL,3);
        tile.progress=kind==1?0:7;tile.completed=9;tile.generated=3000;tile.consumed=1000;tile.transferred=123;
        EnumHand hand=kind==1?EnumHand.OFF_HAND:EnumHand.MAIN_HAND;
        player.setHeldItem(hand,new ItemStack(FluidProof.WRENCH));player.setSneaking(false);
        require(FluidProof.WRENCH.onItemUseFirst(player,world,from,EnumFacing.UP,0.5F,0.5F,0.5F,hand)==EnumActionResult.PASS
            && world.getTileEntity(from)==tile,"Non-sneaking wrench changed machine");
        player.setSneaking(true);
        require(FluidProof.WRENCH.onItemUseFirst(player,world,from,EnumFacing.UP,0.5F,0.5F,0.5F,hand)==EnumActionResult.SUCCESS
            && world.isAirBlock(from) && world.getTileEntity(from)==null,"Wrench did not remove machine");
        List<EntityItem> items=drops(world,from);
        require(items.size()==1 && items.get(0).getItem().getItem()==Item.getItemFromBlock(block),"Packed drop duplicated inventory or machine");
        ItemStack packed=items.get(0).getItem().copy();items.get(0).setDead();
        NBTTagCompound data=packed.getSubCompound("BlockEntityTag");
        require(data!=null && !data.hasKey("x") && !data.hasKey("y") && !data.hasKey("z") && !data.hasKey("id"),"Old tile identity/coordinates packed");
        require(packed.getMaxStackSize()==1 && new ItemStack(block).getMaxStackSize()==64,"Packed stack limit");
        // Mimic the dropped/carried item's normal saved NBT before placing via the actual ItemBlock hook.
        packed=new ItemStack(packed.writeToNBT(new NBTTagCompound()));
        player.setHeldItem(hand,packed);player.setSneaking(false);
        require(packed.getItem().onItemUse(player,world,to.down(),hand,EnumFacing.UP,0.5F,1,0.5F)==EnumActionResult.SUCCESS,"Packed item placement failed");
        MachineTile restored=(MachineTile)world.getTileEntity(to);
        require(restored!=null && restored.getPos().equals(to) && restored.kind==kind && restored.tank.getFluidAmount()==1234,"Fluid/identity/location not restored");
        require(restored.progress==(kind==1?0:7) && restored.completed==9 && restored.generated==3000
            && restored.consumed==1000 && restored.transferred==123,"Progress/counters not restored");
        require(restored.input.getCount()==(kind==1?0:2) && restored.output.getCount()==(kind==2?3:0),"Stored inventory not restored");
        require(packed.isEmpty(),"Survival placement did not consume packed item");
        require(world.destroyBlock(to,true),"Ordinary break failed");
        List<EntityItem> ordinary=drops(world,to);
        int machineCount=0,inputCount=0,outputCount=0;
        ItemStack plain=ItemStack.EMPTY;
        for(EntityItem entity:ordinary) {
            ItemStack stack=entity.getItem();
            if(stack.getItem()==Item.getItemFromBlock(block)) {machineCount+=stack.getCount();plain=stack.copy();require(stack.getSubCompound("BlockEntityTag")==null,"Ordinary drop retained machine data");}
            else if(kind!=1 && stack.getItem()==restored.ingredient())inputCount+=stack.getCount();
            else if(stack.getItem()==Items.CLAY_BALL)outputCount+=stack.getCount();
            else require(false,"Unexpected ordinary drop");
            entity.setDead();
        }
        require(machineCount==1 && inputCount==(kind==1?0:2) && outputCount==(kind==2?3:0),"Ordinary inventory drops/accounting kind="+kind+" machines="+machineCount+" input="+inputCount+" output="+outputCount);
        player.setHeldItem(hand,plain);
        require(plain.getItem().onItemUse(player,world,to.down(),hand,EnumFacing.UP,0.5F,1,0.5F)==EnumActionResult.SUCCESS,"Plain item placement failed");
        MachineTile empty=(MachineTile)world.getTileEntity(to);
        require(empty.tank.getFluidAmount()==0 && empty.input.isEmpty() && empty.output.isEmpty() && empty.progress==0
            && empty.completed==0 && empty.generated==0 && empty.consumed==0 && empty.transferred==0,"Ordinary break restored old state");
        world.setBlockToAir(to);world.setBlockToAir(to.down());
    }
    @Mod.EventHandler public void started(FMLServerStartedEvent event) {
        WorldServer world=net.minecraftforge.common.DimensionManager.getWorld(0);world.getChunk(0,0);
        EntityPlayerMP player=FakePlayerFactory.getMinecraft(world);player.capabilities.isCreativeMode=false;
        exercise(world,player,FluidProof.PRODUCER,0);exercise(world,player,FluidProof.PIPE,1);exercise(world,player,FluidProof.CONSUMER,2);
        System.out.println("WRENCH_PROOF_RESULT machineTypes=3 retainedMB=1234 checks="+checks+" normalBreak=empty noDuplicateDrops=true");
        MinecraftForge.EVENT_BUS.register(this);
    }
    @SubscribeEvent public void tick(TickEvent.ServerTickEvent event) {
        if(!announced && event.phase==TickEvent.Phase.END) {System.out.println("WRENCH_LIVE_PASS");announced=true;}
    }
}
