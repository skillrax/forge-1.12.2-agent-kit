package dev.agentic.statue;

import java.io.*;
import java.util.*;
import java.nio.FloatBuffer;
import org.lwjgl.BufferUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.client.resources.*;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.EnumFacing;
import org.lwjgl.opengl.GL11;
import org.apache.logging.log4j.LogManager;

// Static geometry is compiled once per resource reload, shared by every statue.
public final class StatueRenderer extends TileEntitySpecialRenderer<StatueTile> implements IResourceManagerReloadListener {
    private static final ResourceLocation MODEL=new ResourceLocation(ModelStatue.ID,"models/statue/bunny.mesh");
    private static final class DrawPart {
        final ResourceLocation texture;final boolean cutout;final int list;
        DrawPart(ResourceLocation texture,boolean cutout,int list){this.texture=texture;this.cutout=cutout;this.list=list;}
    }
    private final List<DrawPart> parts=new ArrayList<>();
    private boolean attempted;
    @Override public void onResourceManagerReload(IResourceManager manager){
        for(DrawPart p:parts)GLAllocation.deleteDisplayLists(p.list);
        parts.clear();attempted=false;
    }
    private void prepare() {
        if(attempted)return;attempted=true;
        try(InputStream stream=Minecraft.getMinecraft().getResourceManager().getResource(MODEL).getInputStream()) {
            MeshData mesh=MeshData.read(stream);
            for(MeshData.Part part:mesh.parts) {
                int list=GLAllocation.generateDisplayLists(1);
                GL11.glNewList(list,GL11.GL_COMPILE);
                try {
                    BufferBuilder buffer=Tessellator.getInstance().getBuffer();
                    buffer.begin(GL11.GL_TRIANGLES,DefaultVertexFormats.OLDMODEL_POSITION_TEX_NORMAL);
                    for(int i=0;i<part.vertices.length;i+=8){float[] v=part.vertices;buffer.pos(v[i],v[i+1],v[i+2]).tex(v[i+3],v[i+4]).normal(v[i+5],v[i+6],v[i+7]).endVertex();}
                    Tessellator.getInstance().draw();
                } finally {GL11.glEndList();}
                parts.add(new DrawPart(new ResourceLocation(part.texture),part.cutout,list));
            }
            LogManager.getLogger(ModelStatue.ID).info("STATUE_MESH_READY triangles={} materials={}",mesh.triangles,parts.size());
        } catch(IOException | RuntimeException ex) {
            for(DrawPart p:parts)GLAllocation.deleteDisplayLists(p.list);parts.clear();
            LogManager.getLogger(ModelStatue.ID).error("Statue mesh could not be prepared",ex);
        }
    }
    private static void light(int light,int parameter,float x,float y,float z,float w){FloatBuffer values=BufferUtils.createFloatBuffer(4);values.put(new float[]{x,y,z,w}).flip();GL11.glLight(light,parameter,values);}
    @Override public void render(StatueTile tile,double x,double y,double z,float partialTicks,int destroyStage,float alpha) {
        prepare();if(parts.isEmpty())return;
        EnumFacing facing=tile.getWorld().getBlockState(tile.getPos()).getValue(StatueBlock.FACING);
        GlStateManager.pushMatrix();
        // Preserve actual GL attributes as well as restoring the states changed below.
        GL11.glPushAttrib(GL11.GL_ENABLE_BIT|GL11.GL_LIGHTING_BIT|GL11.GL_CURRENT_BIT|GL11.GL_COLOR_BUFFER_BIT);
        try {
            GlStateManager.translate(x+.5,y+.15,z+.5);GlStateManager.rotate(-facing.getHorizontalAngle(),0,1,0);
            // Raw GL changes stay inside push/pop; GlStateManager's cached state is untouched.
            GL11.glEnable(GL11.GL_TEXTURE_2D);GL11.glDisable(GL11.GL_CULL_FACE);GL11.glEnable(GL11.GL_ALPHA_TEST);GL11.glDisable(GL11.GL_BLEND);
            GL11.glColor4f(1,1,1,1);GL11.glEnable(GL11.GL_LIGHTING);GL11.glEnable(GL11.GL_LIGHT0);GL11.glEnable(GL11.GL_LIGHT1);
            GL11.glEnable(GL11.GL_COLOR_MATERIAL);GL11.glColorMaterial(GL11.GL_FRONT_AND_BACK,GL11.GL_AMBIENT_AND_DIFFUSE);
            light(GL11.GL_LIGHT0,GL11.GL_POSITION,.2F,1F,-.7F,0);light(GL11.GL_LIGHT1,GL11.GL_POSITION,-.2F,1F,.7F,0);
            light(GL11.GL_LIGHT0,GL11.GL_DIFFUSE,.6F,.6F,.6F,1);light(GL11.GL_LIGHT1,GL11.GL_DIFFUSE,.4F,.4F,.4F,1);
            light(GL11.GL_LIGHT0,GL11.GL_AMBIENT,0,0,0,1);light(GL11.GL_LIGHT1,GL11.GL_AMBIENT,0,0,0,1);
            GL11.glEnable(GL11.GL_NORMALIZE);
            for(DrawPart p:parts){bindTexture(p.texture);GL11.glAlphaFunc(GL11.GL_GREATER,p.cutout?.1F:0F);GlStateManager.callList(p.list);}
        } finally {
            GL11.glPopAttrib();GlStateManager.popMatrix();
        }
    }
}
