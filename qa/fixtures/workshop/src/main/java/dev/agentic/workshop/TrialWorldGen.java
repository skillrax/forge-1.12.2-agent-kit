package dev.agentic.workshop;
import java.util.Random;
import net.minecraft.block.Block;
import net.minecraft.init.Biomes;
import net.minecraft.init.Blocks;
import net.minecraft.tileentity.TileEntityChest;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.chunk.IChunkProvider;
import net.minecraft.world.gen.IChunkGenerator;
import net.minecraft.world.gen.feature.WorldGenMinable;
import net.minecraftforge.fml.common.IWorldGenerator;
public final class TrialWorldGen implements IWorldGenerator {
    public static final ResourceLocation LOOT=new ResourceLocation(Workshop.ID,"chests/test_marker");
    private boolean canPlaceMarker(World world,BlockPos base) {
        if(base.getY()<4 || base.getY()>220)return false;
        // Height maps include foliage. Require level natural ground across the entire footprint.
        for(int x=-1;x<=1;x++)for(int z=-1;z<=1;z++) {
            BlockPos ground=base.add(x,-1,z);Block block=world.getBlockState(ground).getBlock();
            if(block!=Blocks.GRASS && block!=Blocks.DIRT && block!=Blocks.STONE)return false;
            for(int y=0;y<3;y++) {
                BlockPos space=base.add(x,y,z);Block above=world.getBlockState(space).getBlock();
                if(!world.isAirBlock(space) && above!=Blocks.TALLGRASS && above!=Blocks.DOUBLE_PLANT
                    && above!=Blocks.YELLOW_FLOWER && above!=Blocks.RED_FLOWER && above!=Blocks.SNOW_LAYER)return false;
            }
        }
        return true;
    }
    @Override public void generate(Random random,int chunkX,int chunkZ,World world,IChunkGenerator generator,IChunkProvider provider) {
        if(world.provider.getDimension()!=0)return;
        BlockPos center=new BlockPos(chunkX*16+8,0,chunkZ*16+8);
        if(world.getBiome(center)!=Biomes.PLAINS)return;
        WorldGenMinable ore=new WorldGenMinable(Content.ORE.getDefaultState(),6);
        // Minable adds an8-block offset internally. Keep these small veins inside this chunk.
        for(int i=0;i<3;i++)ore.generate(world,random,new BlockPos(chunkX*16+random.nextInt(4),8+random.nextInt(24),chunkZ*16+random.nextInt(4)));
        if(Math.floorMod(chunkX,8)!=0 || Math.floorMod(chunkZ,8)!=0)return;
        BlockPos base=world.getHeight(center);
        if(!canPlaceMarker(world,base))return;
        for(int x=-1;x<=1;x++)for(int z=-1;z<=1;z++) {
            world.setBlockState(base.add(x,-1,z),Blocks.STONEBRICK.getDefaultState(),2);
            for(int y=0;y<3;y++)world.setBlockToAir(base.add(x,y,z));
        }
        world.setBlockState(base,Blocks.STONEBRICK.getDefaultState(),2);
        world.setBlockState(base.up(),Blocks.STONEBRICK.getDefaultState(),2);
        world.setBlockState(base.up(2),Blocks.GOLD_BLOCK.getDefaultState(),2);
        world.setBlockState(base.east(),Blocks.CHEST.getDefaultState(),2);
        TileEntityChest chest=(TileEntityChest)world.getTileEntity(base.east());
        if(chest!=null) {chest.setLootTable(LOOT,random.nextLong());chest.markDirty();}
        world.setBlockState(base.west().down(),Blocks.FARMLAND.getDefaultState(),2);
        world.setBlockState(base.west().south().down(),Blocks.WATER.getDefaultState(),2);
        world.setBlockState(base.west(),Content.CROP.getDefaultState(),2);
    }
}
