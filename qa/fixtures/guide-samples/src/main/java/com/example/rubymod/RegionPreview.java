package com.example.rubymod;

import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderGlobal;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.entity.Entity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumBlockRenderType;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL14;

/** Client only: translucent captured blocks where the tool would place them; green outline = free, red = blocked. */
@Mod.EventBusSubscriber(modid = RubymodMod.MODID, value = Side.CLIENT)
public final class RegionPreview {
    @SubscribeEvent
    public static void render(RenderWorldLastEvent event) {
        Minecraft mc = Minecraft.getMinecraft();
        RayTraceResult hit = mc.objectMouseOver;
        Entity camera = mc.getRenderViewEntity();
        if (mc.player == null || camera == null || hit == null || hit.typeOfHit != RayTraceResult.Type.BLOCK) return;
        ItemStack tool = mc.player.getHeldItemMainhand();
        NBTTagCompound tag = tool.getTagCompound();
        if (tool.getItem() != ModItems.REGION_TOOL || tag == null || !tag.hasKey("Capture")) return;
        BlockPos size = BlockPos.fromLong(tag.getLong("Size")), min = hit.getBlockPos().offset(hit.sideHit);
        int[] states = tag.getIntArray("Preview");
        if (states.length != RegionMover.volume(size)) return;
        AxisAlignedBB box = RegionMover.box(min, size);
        boolean free = mc.world.getEntitiesWithinAABB(Entity.class, box).isEmpty();
        for (int i = 0; i < states.length; i++) free &= mc.world.isAirBlock(RegionMover.at(min, size, i));

        float t = event.getPartialTicks();   // camera position interpolated like the world renderer
        double cx = camera.lastTickPosX + (camera.posX - camera.lastTickPosX) * t;
        double cy = camera.lastTickPosY + (camera.posY - camera.lastTickPosY) * t;
        double cz = camera.lastTickPosZ + (camera.posZ - camera.lastTickPosZ) * t;
        GlStateManager.pushMatrix();
        try {
            GlStateManager.translate(-cx, -cy, -cz);
            GlStateManager.enableBlend();
            GlStateManager.depthMask(false);
            mc.getTextureManager().bindTexture(TextureMap.LOCATION_BLOCKS_TEXTURE);
            GL14.glBlendColor(1, 1, 1, 0.4F);                                  // 40% opacity for every block model
            GlStateManager.blendFunc(GL11.GL_CONSTANT_ALPHA, GL11.GL_ONE_MINUS_CONSTANT_ALPHA);
            BufferBuilder buffer = Tessellator.getInstance().getBuffer();
            buffer.begin(GL11.GL_QUADS, DefaultVertexFormats.BLOCK);
            for (int i = 0; i < states.length; i++) {
                IBlockState state = Block.getStateById(states[i]);
                if (state.getRenderType() == EnumBlockRenderType.MODEL)           // chests etc. (TESR) have no model: outline only
                    mc.getBlockRendererDispatcher().renderBlock(state, RegionMover.at(min, size, i), mc.world, buffer);
            }
            Tessellator.getInstance().draw();
            GlStateManager.disableTexture2D();
            GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            RenderGlobal.drawSelectionBoundingBox(box.grow(0.002), free ? 0.2F : 1F, free ? 1F : 0.2F, 0.2F, 0.9F);
        } finally {                                                            // restore every GL change
            GL14.glBlendColor(1, 1, 1, 1);
            GlStateManager.enableTexture2D();
            GlStateManager.depthMask(true);
            GlStateManager.disableBlend();
            GlStateManager.popMatrix();
        }
    }

    private RegionPreview() {}
}
