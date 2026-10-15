package dev.agentic.itemnetwork;
import net.minecraft.block.Block;
import net.minecraft.item.*;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.registry.GameRegistry;
@Mod.EventBusSubscriber(modid=ItemNetwork.ID)
public final class Content {
 public static final DuctBlock DUCT=new DuctBlock();
 @SubscribeEvent public static void blocks(RegistryEvent.Register<Block> e){e.getRegistry().register(DUCT);GameRegistry.registerTileEntity(DuctTile.class,new ResourceLocation(ItemNetwork.ID,"duct"));}
 @SubscribeEvent public static void items(RegistryEvent.Register<Item> e){e.getRegistry().register(new ItemBlock(DUCT).setRegistryName(DUCT.getRegistryName()));}
}
