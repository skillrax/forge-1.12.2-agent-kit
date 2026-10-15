package dev.agentic.rfnetwork;
import net.minecraft.block.Block;
import net.minecraft.item.Item;
import net.minecraft.item.ItemBlock;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.registry.GameRegistry;
@Mod.EventBusSubscriber(modid=RfProof.ID)
public final class Content {
    public static final MachineBlock GENERATOR=new MachineBlock("generator",0), STORAGE=new MachineBlock("storage",1), SINK=new MachineBlock("sink",2);
    public static final CableBlock CABLE=new CableBlock();
    public static final Item UPGRADE=new Item().setRegistryName(RfProof.ID,"output_upgrade").setTranslationKey(RfProof.ID+".output_upgrade").setMaxStackSize(1).setCreativeTab(CreativeTabs.REDSTONE);
    @SubscribeEvent public static void blocks(RegistryEvent.Register<Block> e){
        e.getRegistry().registerAll(GENERATOR,STORAGE,SINK,CABLE);
        GameRegistry.registerTileEntity(GeneratorTile.class,new ResourceLocation(RfProof.ID,"generator"));
        GameRegistry.registerTileEntity(StorageTile.class,new ResourceLocation(RfProof.ID,"storage"));
        GameRegistry.registerTileEntity(SinkTile.class,new ResourceLocation(RfProof.ID,"sink"));
        GameRegistry.registerTileEntity(CableTile.class,new ResourceLocation(RfProof.ID,"cable"));
    }
    @SubscribeEvent public static void items(RegistryEvent.Register<Item> e){
        for(Block b:new Block[]{GENERATOR,STORAGE,SINK,CABLE})e.getRegistry().register(new ItemBlock(b).setRegistryName(b.getRegistryName()));
        e.getRegistry().register(UPGRADE);
    }
}
