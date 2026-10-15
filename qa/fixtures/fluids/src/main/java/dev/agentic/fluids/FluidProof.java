package dev.agentic.fluids;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.item.Item;
import net.minecraft.item.ItemBlock;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.registry.GameRegistry;

@Mod(modid=FluidProof.ID,name="Fluid Mechanism Proof",version="1.1.0",acceptedMinecraftVersions="[1.12.2]")
@Mod.EventBusSubscriber(modid=FluidProof.ID)
public final class FluidProof {
    public static final String ID="fluidproof";
    static { FluidRegistry.enableUniversalBucket(); }
    public static final Fluid BRINE=new Fluid("biting_brine",new ResourceLocation("minecraft:blocks/water_still"),
        new ResourceLocation("minecraft:blocks/water_flow")).setColor(0xFFB04DE0).setDensity(1200).setViscosity(4000).setTemperature(300);
    public static final Block PRODUCER=new MachineBlock(0,"brine_maker");
    public static final Block PIPE=new MachineBlock(1,"straight_pipe");
    public static final Block CONSUMER=new MachineBlock(2,"clay_maker");
    public static final Item WRENCH=new WrenchItem();
    public static BrineBlock brineBlock;
    @SubscribeEvent public static void blocks(RegistryEvent.Register<Block> event) {
        if(!FluidRegistry.registerFluid(BRINE))throw new IllegalStateException("biting_brine name already registered");
        FluidRegistry.addBucketForFluid(BRINE);
        brineBlock=new BrineBlock(BRINE);
        event.getRegistry().registerAll(PRODUCER,PIPE,CONSUMER,brineBlock);
        GameRegistry.registerTileEntity(MachineTile.class,new ResourceLocation(ID,"machine"));
    }
    @SubscribeEvent public static void items(RegistryEvent.Register<Item> event) {
        for(Block block:new Block[]{PRODUCER,PIPE,CONSUMER})
            event.getRegistry().register(new MachineItemBlock(block).setRegistryName(block.getRegistryName()));
        event.getRegistry().register(WRENCH);
    }
}
