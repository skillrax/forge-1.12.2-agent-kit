package dev.agentic.smelter;
import net.minecraft.block.Block;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.item.Item;
import net.minecraft.item.ItemBlock;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.registry.GameRegistry;

@Mod.EventBusSubscriber(modid=SmelterProof.ID)
public final class Content {
    public static final ProofBlock GENERATOR=new ProofBlock("generator",0),PIPE=new ProofBlock("pipe",1),SMELTER=new ProofBlock("smelter",2);
    public static final Item UPGRADE=new Item().setRegistryName(SmelterProof.ID,"secondary_output_upgrade").setTranslationKey(SmelterProof.ID+".secondary_output_upgrade").setMaxStackSize(1).setCreativeTab(CreativeTabs.REDSTONE);
    @SubscribeEvent public static void blocks(RegistryEvent.Register<Block> e){
        e.getRegistry().registerAll(GENERATOR,PIPE,SMELTER);
        GameRegistry.registerTileEntity(GeneratorTile.class,new ResourceLocation(SmelterProof.ID,"generator"));
        GameRegistry.registerTileEntity(PipeTile.class,new ResourceLocation(SmelterProof.ID,"pipe"));
        GameRegistry.registerTileEntity(SmelterTile.class,new ResourceLocation(SmelterProof.ID,"smelter"));
    }
    @SubscribeEvent public static void items(RegistryEvent.Register<Item> e){
        for(ProofBlock b:new ProofBlock[]{GENERATOR,PIPE,SMELTER})e.getRegistry().register(new ItemBlock(b).setRegistryName(b.getRegistryName()));
        e.getRegistry().register(UPGRADE);
    }
}
