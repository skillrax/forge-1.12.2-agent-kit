package dev.agentic.clockworkbottle;

import java.nio.FloatBuffer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.entity.Entity;
import net.minecraft.init.Blocks;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.event.world.WorldEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL14;

@Mod.EventBusSubscriber(modid = ClockworkBottle.MOD_ID, value = Side.CLIENT)
public final class BoostLabels {
    private static World world;
    private static BoostSnapshot snapshot = new BoostSnapshot();
    private BoostLabels() {}
    // Invoked on the client main thread; never mutate/render from the Netty thread.
    public static void accept(BoostSnapshot message) {
        World current = Minecraft.getMinecraft().world;
        if (current == null || current.provider.getDimension() != message.dimension) return;
        world = current; snapshot = message;
    }
    @SubscribeEvent public static void unload(WorldEvent.Unload event) {
        if (event.getWorld().isRemote && event.getWorld() == world) {
            world = null; snapshot = new BoostSnapshot();
        }
    }
    @SubscribeEvent public static void render(RenderWorldLastEvent event) {
        Minecraft mc = Minecraft.getMinecraft(); Entity camera = mc.getRenderViewEntity();
        if (mc.world == null || mc.world != world || camera == null || snapshot.positions.length == 0) return;
        double partial = event.getPartialTicks();
        double x = camera.lastTickPosX + (camera.posX - camera.lastTickPosX) * partial;
        double y = camera.lastTickPosY + (camera.posY - camera.lastTickPosY) * partial;
        double z = camera.lastTickPosZ + (camera.posZ - camera.lastTickPosZ) * partial;
        RenderState saved = new RenderState();
        try {
            GlStateManager.disableLighting(); GlStateManager.disableCull();
            GlStateManager.enableDepth(); GlStateManager.depthMask(false);
            GlStateManager.enableTexture2D(); GlStateManager.enableBlend();
            GlStateManager.tryBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ZERO);
            for (int i = 0; i < snapshot.positions.length; i++) {
                BlockPos pos = BlockPos.fromLong(snapshot.positions[i]);
                if (pos.distanceSq(x, y, z) > 32 * 32 || !world.isBlockLoaded(pos, false)) continue;
                if (world.getBlockState(pos).getBlock() != Blocks.FURNACE
                        && world.getBlockState(pos).getBlock() != Blocks.LIT_FURNACE) continue;
                draw(mc.fontRenderer, pos.getX() + .5 - x, pos.getY() + .5 - y, pos.getZ() + .5 - z,
                    snapshot.multipliers[i] + "x", BoostSnapshot.seconds(snapshot.ticks[i]) + "s");
            }
        } finally {saved.restore();}
    }
    private static void draw(FontRenderer font, double x, double y, double z, String speed, String time) {
        float scale = Math.min(.025F, .8F / Math.max(font.getStringWidth(speed), font.getStringWidth(time)));
        for (EnumFacing face : EnumFacing.values()) {
            GlStateManager.pushMatrix();
            try {
                GlStateManager.translate(x, y, z);
                switch (face) {
                    case NORTH: GlStateManager.rotate(180, 0, 1, 0); break;
                    case EAST: GlStateManager.rotate(90, 0, 1, 0); break;
                    case WEST: GlStateManager.rotate(-90, 0, 1, 0); break;
                    case UP: GlStateManager.rotate(-90, 1, 0, 0); break;
                    case DOWN: GlStateManager.rotate(90, 1, 0, 0); break;
                    default: break;
                }
                // Each label lies just outside its face. Depth testing keeps walls opaque.
                GlStateManager.translate(0, 0, .502);
                GlStateManager.scale(scale, -scale, scale);
                font.drawString(speed, -font.getStringWidth(speed) / 2F, -font.FONT_HEIGHT - 1, 0xFFFFFFFF, false);
                font.drawString(time, -font.getStringWidth(time) / 2F, 1, 0xFFFFFFFF, false);
            } finally {GlStateManager.popMatrix();}
        }
    }
    static FloatBuffer createColorBuffer() {
        // LWJGL 2 glGetFloat requires 16 remaining floats even for a four-component color.
        return BufferUtils.createFloatBuffer(16);
    }
    /** Restore actual incoming GL state, including the lightmap and font texture binding. */
    private static final class RenderState {
        final boolean lighting = GL11.glIsEnabled(GL11.GL_LIGHTING), cull = GL11.glIsEnabled(GL11.GL_CULL_FACE);
        final boolean depth = GL11.glIsEnabled(GL11.GL_DEPTH_TEST), blend = GL11.glIsEnabled(GL11.GL_BLEND);
        final boolean alpha = GL11.glIsEnabled(GL11.GL_ALPHA_TEST), mask = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
        final int src = GL11.glGetInteger(GL14.GL_BLEND_SRC_RGB), dst = GL11.glGetInteger(GL14.GL_BLEND_DST_RGB);
        final int srcAlpha = GL11.glGetInteger(GL14.GL_BLEND_SRC_ALPHA), dstAlpha = GL11.glGetInteger(GL14.GL_BLEND_DST_ALPHA);
        final int active = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
        final FloatBuffer color = createColorBuffer();
        final boolean lightmap, texture;
        final int binding;
        RenderState() {
            GL11.glGetFloat(GL11.GL_CURRENT_COLOR, color);
            GlStateManager.setActiveTexture(OpenGlHelper.lightmapTexUnit);
            lightmap = GL11.glIsEnabled(GL11.GL_TEXTURE_2D); GlStateManager.disableTexture2D();
            GlStateManager.setActiveTexture(OpenGlHelper.defaultTexUnit);
            texture = GL11.glIsEnabled(GL11.GL_TEXTURE_2D); binding = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        }
        void restore() {
            GlStateManager.setActiveTexture(OpenGlHelper.defaultTexUnit); GlStateManager.bindTexture(binding);
            if (texture) GlStateManager.enableTexture2D(); else GlStateManager.disableTexture2D();
            GlStateManager.setActiveTexture(OpenGlHelper.lightmapTexUnit);
            if (lightmap) GlStateManager.enableTexture2D(); else GlStateManager.disableTexture2D();
            GlStateManager.setActiveTexture(active);
            if (lighting) GlStateManager.enableLighting(); else GlStateManager.disableLighting();
            if (cull) GlStateManager.enableCull(); else GlStateManager.disableCull();
            if (depth) GlStateManager.enableDepth(); else GlStateManager.disableDepth();
            GlStateManager.depthMask(mask);
            GlStateManager.tryBlendFuncSeparate(src, dst, srcAlpha, dstAlpha);
            if (blend) GlStateManager.enableBlend(); else GlStateManager.disableBlend();
            if (alpha) GlStateManager.enableAlpha(); else GlStateManager.disableAlpha();
            GlStateManager.color(color.get(0), color.get(1), color.get(2), color.get(3));
        }
    }
}
