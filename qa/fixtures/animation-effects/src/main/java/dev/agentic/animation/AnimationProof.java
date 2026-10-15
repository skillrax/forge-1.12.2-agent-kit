package dev.agentic.animation;

import net.minecraft.block.*;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.*;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.*;
import net.minecraft.util.math.*;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.world.World;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.registry.GameRegistry;

@Mod(modid=AnimationProof.ID,name="Rotor and Crystal",version="1.0.1",acceptedMinecraftVersions="[1.12.2]")
@Mod.EventBusSubscriber(modid=AnimationProof.ID)
public final class AnimationProof {
    public static final String ID="animationproof";
    public static final Block FIXTURE=new Fixture();
    @SubscribeEvent public static void blocks(RegistryEvent.Register<Block> e){
        e.getRegistry().register(FIXTURE);
        GameRegistry.registerTileEntity(AnimationTile.class,new ResourceLocation(ID,"fixture"));
    }
    @SubscribeEvent public static void items(RegistryEvent.Register<Item> e){e.getRegistry().register(new ItemBlock(FIXTURE).setRegistryName(FIXTURE.getRegistryName()));}
    public static final class Fixture extends Block {
        private static final AxisAlignedBB BOX=new AxisAlignedBB(0,0,0,1,.25,1);
        Fixture(){super(Material.ROCK);setRegistryName(ID,"rotor_crystal");setTranslationKey(ID+".rotor_crystal");setCreativeTab(CreativeTabs.DECORATIONS);setHardness(1.5F);}
        @Override public boolean hasTileEntity(IBlockState state){return true;}
        @Override public TileEntity createTileEntity(World world,IBlockState state){return new AnimationTile();}
        @Override public boolean isOpaqueCube(IBlockState s){return false;}
        @Override public boolean isFullCube(IBlockState s){return false;}
        @Override public AxisAlignedBB getBoundingBox(IBlockState s,net.minecraft.world.IBlockAccess w,BlockPos p){return BOX;}
        @Override public boolean onBlockActivated(World w,BlockPos p,IBlockState s,EntityPlayer player,EnumHand hand,EnumFacing side,float x,float y,float z){
            if(!w.isRemote && w.getTileEntity(p) instanceof AnimationTile){
                AnimationTile tile=(AnimationTile)w.getTileEntity(p);tile.toggle();
                player.sendStatusMessage(new TextComponentString("Rotor + Crystal: "+(tile.active?"ON":"OFF")+" (right-click to toggle)"),true);
            }
            return true;
        }
    }
}
