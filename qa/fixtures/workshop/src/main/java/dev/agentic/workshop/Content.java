package dev.agentic.workshop;

import net.minecraft.block.Block;
import net.minecraft.item.Item;
import net.minecraft.item.ItemBlock;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.registry.GameRegistry;

@Mod.EventBusSubscriber(modid=Workshop.ID)
public final class Content {
    public static final WorkshopBlock BOX=new WorkshopBlock("storage_box",false);
    public static final WorkshopBlock PROCESSOR=new WorkshopBlock("processor",true);
    public static final PouchItem POUCH=new PouchItem();
    public static final LauncherItem LAUNCHER=new LauncherItem();
    public static final TrialPortal PORTAL=new TrialPortal();
    public static final TrialOre ORE=new TrialOre();
    public static final TrialCrop CROP=new TrialCrop();
    public static final Item SEEDS=new net.minecraft.item.ItemSeeds(CROP,net.minecraft.init.Blocks.FARMLAND)
        .setRegistryName(Workshop.ID,"test_seeds").setTranslationKey(Workshop.ID+".test_seeds");
    public static final Item HARVEST=new Item().setRegistryName(Workshop.ID,"test_harvest")
        .setTranslationKey(Workshop.ID+".test_harvest").setCreativeTab(net.minecraft.creativetab.CreativeTabs.MATERIALS);
    @SubscribeEvent public static void blocks(RegistryEvent.Register<Block> event) {
        event.getRegistry().registerAll(BOX,PROCESSOR,ORE,CROP,PORTAL);
        GameRegistry.registerTileEntity(StorageTile.class,new ResourceLocation(Workshop.ID,"storage"));
        GameRegistry.registerTileEntity(ProcessorTile.class,new ResourceLocation(Workshop.ID,"processor"));
    }
    @SubscribeEvent public static void items(RegistryEvent.Register<Item> event) {
        event.getRegistry().registerAll(new ItemBlock(BOX).setRegistryName(BOX.getRegistryName()),
            new ItemBlock(PROCESSOR).setRegistryName(PROCESSOR.getRegistryName()),new ItemBlock(ORE).setRegistryName(ORE.getRegistryName()),new ItemBlock(PORTAL).setRegistryName(PORTAL.getRegistryName()),POUCH,LAUNCHER,SEEDS,HARVEST);
    }
}
