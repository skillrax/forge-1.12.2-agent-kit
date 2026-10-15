package dev.agentic.fluids;

import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.block.model.ModelResourceLocation;
import net.minecraft.client.renderer.block.statemap.StateMapperBase;
import net.minecraft.item.Item;
import net.minecraftforge.client.event.ModelRegistryEvent;
import net.minecraftforge.client.model.ModelLoader;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;

@Mod.EventBusSubscriber(modid=FluidProof.ID,value=Side.CLIENT)
public final class ClientModels {
    @SubscribeEvent public static void models(ModelRegistryEvent event) {
        ModelLoader.setCustomModelResourceLocation(FluidProof.WRENCH,0,new ModelResourceLocation(FluidProof.WRENCH.getRegistryName(),"inventory"));
        for(Block block:new Block[]{FluidProof.PRODUCER,FluidProof.PIPE,FluidProof.CONSUMER})
            ModelLoader.setCustomModelResourceLocation(Item.getItemFromBlock(block),0,new ModelResourceLocation(block.getRegistryName(),"inventory"));
        ModelLoader.setCustomStateMapper(FluidProof.brineBlock,new StateMapperBase() {
            @Override protected ModelResourceLocation getModelResourceLocation(IBlockState state) {
                return new ModelResourceLocation(FluidProof.ID+":biting_brine","fluid");
            }
        });
    }
}
