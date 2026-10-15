package dev.agentic.routing;

import net.minecraft.block.Block;
import net.minecraft.item.Item;
import net.minecraft.item.ItemBlock;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.registry.GameRegistry;

@Mod(modid=RoutingProof.ID,name="Item Routing Proof",version="1.0.1",acceptedMinecraftVersions="[1.12.2]")
@Mod.EventBusSubscriber(modid=RoutingProof.ID)
public final class RoutingProof {
    public static final String ID="routingproof";
    public static final Block EXTRACTOR=new RoutingBlock(false,"extractor");
    public static final Block SPLITTER=new RoutingBlock(true,"splitter");
    @SubscribeEvent public static void blocks(RegistryEvent.Register<Block> e) {
        e.getRegistry().registerAll(EXTRACTOR,SPLITTER);
        GameRegistry.registerTileEntity(RoutingTile.class,new ResourceLocation(ID,"router"));
    }
    @SubscribeEvent public static void items(RegistryEvent.Register<Item> e) {
        for(Block b:new Block[]{EXTRACTOR,SPLITTER})e.getRegistry().register(new ItemBlock(b).setRegistryName(b.getRegistryName()));
    }
}
