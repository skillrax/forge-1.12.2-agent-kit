package dev.agentic.animation;

import net.minecraft.client.particle.Particle;
import net.minecraft.world.World;

public final class OrbitSparkle extends Particle {
    private final double cx,cy,cz,angle;
    OrbitSparkle(World w,double x,double y,double z,double angle){
        super(w,x+Math.cos(angle)*.36,y,z+Math.sin(angle)*.36);
        cx=x;cy=y;cz=z;this.angle=angle;particleMaxAge=24;particleScale=.65F;
        setParticleTextureIndex(65);setRBGColorF(.3F,.9F,1F);setAlphaF(1F);
    }
    @Override public void onUpdate(){
        prevPosX=posX;prevPosY=posY;prevPosZ=posZ;
        if(++particleAge>=particleMaxAge){setExpired();return;}
        double a=angle+particleAge*.10;
        setPosition(cx+Math.cos(a)*.36,cy+particleAge*.006,cz+Math.sin(a)*.36);
        particleScale=.65F*(1F-(float)particleAge/particleMaxAge);
    }
    @Override public int getBrightnessForRender(float partial){return 0xF000F0;}
}
