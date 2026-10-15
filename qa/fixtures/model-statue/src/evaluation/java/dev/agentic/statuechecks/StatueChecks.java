package dev.agentic.statuechecks;

import dev.agentic.statue.*;
import java.io.*;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import net.minecraft.init.Blocks;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldServer;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLServerStartedEvent;

@Mod(modid="statuechecks",name="Statue Checks",version="1",dependencies="required-after:modelstatue")
public final class StatueChecks {
    private int checks;
    private void require(boolean ok,String why){checks++;if(!ok)throw new IllegalStateException("STATUE_ASSERTIONS_FAIL: "+why);}
    @Mod.EventHandler public void started(FMLServerStartedEvent e) throws Exception {
        try(InputStream in=ModelStatue.class.getResourceAsStream("/assets/modelstatue/models/statue/bunny.mesh")) {
            MeshData mesh=MeshData.read(in);require(mesh.triangles==188542 && mesh.parts.size()==12,"Mesh geometry/material count");
            double height=0;int cutouts=0;
            for(MeshData.Part part:mesh.parts){
                try(InputStream tex=ModelStatue.class.getResourceAsStream("/assets/modelstatue/"+part.texture.split(":")[1])){
                    BufferedImage image=ImageIO.read(tex);require(image!=null && image.getWidth()<=1024 && image.getHeight()<=1024,"Texture decode/size: "+part.texture);
                }
                for(int i=0;i<part.vertices.length;i+=8){float[] v=part.vertices;require(Math.abs(v[i])<2 && v[i+1]>=-.001 && v[i+1]<=3.001 && Math.abs(v[i+2])<2,"Mesh position bounds");height=Math.max(height,v[i+1]);}
                if(part.cutout)cutouts++;
            }
            require(height>2.99 && cutouts==2,"Height/cutout materials");
        }
        WorldServer world=net.minecraftforge.common.DimensionManager.getWorld(0);world.getChunk(0,0);
        world.getGameRules().setOrCreateGameRule("doDaylightCycle","false");world.getGameRules().setOrCreateGameRule("doMobSpawning","false");world.setWorldTime(6000);
        BlockPos pos=new BlockPos(0,4,0);
        world.setBlockState(pos.down(),Blocks.STONEBRICK.getDefaultState(),3);
        world.setBlockState(pos,ModelStatue.STATUE.getDefaultState().withProperty(StatueBlock.FACING,EnumFacing.SOUTH),3);
        require(world.getTileEntity(pos) instanceof StatueTile,"Actual statue tile");
        require(ModelStatue.STATUE.getStateFromMeta(ModelStatue.STATUE.getMetaFromState(world.getBlockState(pos))).getValue(StatueBlock.FACING)==EnumFacing.SOUTH,"Facing metadata");
        require(((StatueTile)world.getTileEntity(pos)).getRenderBoundingBox().maxY==8,"Render bounds include full statue");
        System.out.println("STATUE_SERVER_PASS triangles=188542 textures=12 checks="+checks+" tile=true");
    }
}
