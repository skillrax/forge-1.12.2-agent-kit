package dev.agentic.animation;

import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import org.lwjgl.opengl.GL11;
import org.lwjgl.BufferUtils;
import java.nio.FloatBuffer;

public final class FixtureRenderer extends TileEntitySpecialRenderer<AnimationTile> {
    // LWJGL 2 glGetFloat requires 16 slots even for a four-component color.
    private final FloatBuffer savedColor=BufferUtils.createFloatBuffer(16);
    @Override public void render(AnimationTile tile,double x,double y,double z,float partial,int destroy,float alpha){
        boolean texture=GL11.glIsEnabled(GL11.GL_TEXTURE_2D),light=GL11.glIsEnabled(GL11.GL_LIGHTING),cull=GL11.glIsEnabled(GL11.GL_CULL_FACE);
        savedColor.clear();GL11.glGetFloat(GL11.GL_CURRENT_COLOR,savedColor);
        GlStateManager.pushMatrix();
        try{
            GlStateManager.translate(x+.5,y,z+.5);GlStateManager.disableTexture2D();GlStateManager.disableLighting();GlStateManager.disableCull();
            box(-.06,.25,-.06,.06,.66,.06,.35F,.4F,.45F);
            double time=tile.motionTime(partial);
            GlStateManager.pushMatrix();
            try{
                GlStateManager.translate(0,.64,0);GlStateManager.rotate((float)((time*4)%360),0,1,0);
                box(-.44,-.035,-.07,.44,.035,.07,.95F,.65F,.15F);
                box(-.07,-.035,-.44,.07,.035,.44,.95F,.65F,.15F);
                box(-.10,-.07,-.10,.10,.07,.10,.5F,.35F,.15F);
            }finally{GlStateManager.popMatrix();}
            GlStateManager.pushMatrix();
            try{
                GlStateManager.translate(0,1.22+Math.sin(time*.08)*.10,0);GlStateManager.rotate((float)((time*1.7)%360),0,1,0);
                BufferBuilder b=Tessellator.getInstance().getBuffer();b.begin(GL11.GL_TRIANGLES,DefaultVertexFormats.POSITION_COLOR);
                double[][] rim={{.19,0,0},{0,0,.19},{-.19,0,0},{0,0,-.19}};
                for(int i=0;i<4;i++){double[] a=rim[i],v=rim[(i+1)%4];
                    vertex(b,0,.30,0,.35F,.9F,1F);vertex(b,a[0],0,a[2],.1F,.65F,.8F);vertex(b,v[0],0,v[2],.25F,.8F,.95F);
                    vertex(b,0,-.30,0,.15F,.45F,.7F);vertex(b,v[0],0,v[2],.25F,.8F,.95F);vertex(b,a[0],0,a[2],.1F,.65F,.8F);
                }Tessellator.getInstance().draw();
            }finally{GlStateManager.popMatrix();}
        }finally{
            if(texture)GlStateManager.enableTexture2D();else GlStateManager.disableTexture2D();
            if(light)GlStateManager.enableLighting();else GlStateManager.disableLighting();
            if(cull)GlStateManager.enableCull();else GlStateManager.disableCull();
            // Vanilla POSITION_COLOR upload invalidates the color cache; restore through its manager.
            GlStateManager.color(savedColor.get(0),savedColor.get(1),savedColor.get(2),savedColor.get(3));
            GlStateManager.popMatrix();
        }
    }
    private static void vertex(BufferBuilder b,double x,double y,double z,float r,float g,float blue){b.pos(x,y,z).color(r,g,blue,1F).endVertex();}
    private static void box(double x,double y,double z,double X,double Y,double Z,float r,float g,float blue){
        double[][] v={{x,y,z},{X,y,z},{X,Y,z},{x,Y,z},{x,y,Z},{X,y,Z},{X,Y,Z},{x,Y,Z}};
        int[][] faces={{0,1,2,3},{4,7,6,5},{0,4,5,1},{3,2,6,7},{0,3,7,4},{1,5,6,2}};
        BufferBuilder b=Tessellator.getInstance().getBuffer();b.begin(GL11.GL_QUADS,DefaultVertexFormats.POSITION_COLOR);
        for(int[] f:faces)for(int i:f)vertex(b,v[i][0],v[i][1],v[i][2],r,g,blue);
        Tessellator.getInstance().draw();
    }
}
