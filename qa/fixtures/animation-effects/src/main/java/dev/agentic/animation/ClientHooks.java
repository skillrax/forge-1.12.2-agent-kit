package dev.agentic.animation;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.model.ModelResourceLocation;
import net.minecraft.item.Item;
import net.minecraft.tileentity.TileEntity;
import net.minecraftforge.client.event.ModelRegistryEvent;
import net.minecraftforge.client.model.ModelLoader;
import net.minecraftforge.fml.client.registry.ClientRegistry;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.Side;

@Mod.EventBusSubscriber(modid=AnimationProof.ID,value=Side.CLIENT)
public final class ClientHooks {
    @SubscribeEvent public static void models(ModelRegistryEvent event){
        ModelLoader.setCustomModelResourceLocation(Item.getItemFromBlock(AnimationProof.FIXTURE),0,new ModelResourceLocation(AnimationProof.FIXTURE.getRegistryName(),"inventory"));
        ClientRegistry.bindTileEntitySpecialRenderer(AnimationTile.class,new FixtureRenderer());
    }
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event){
        Minecraft mc=Minecraft.getMinecraft();
        if(event.phase!=TickEvent.Phase.END || mc.world==null || mc.player==null || mc.isGamePaused() || mc.gameSettings.particleSetting==2)return;
        long tick=mc.world.getTotalWorldTime();if(tick%4!=0)return;
        // One sparkle / 4 ticks / nearby active fixture; 24-tick life, at most 6 live per fixture.
        for(TileEntity raw:mc.world.loadedTileEntityList){
            if(!(raw instanceof AnimationTile) || mc.player.getDistanceSqToCenter(raw.getPos())>24*24)continue;
            AnimationTile tile=(AnimationTile)raw;if(!tile.active)continue;
            double time=tile.motionTime(0),angle=time*.12;
            mc.effectRenderer.addEffect(new OrbitSparkle(mc.world,tile.getPos().getX()+.5,tile.getPos().getY()+1.22+Math.sin(time*.08)*.10,tile.getPos().getZ()+.5,angle));
        }
    }
}
