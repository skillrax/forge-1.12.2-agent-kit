package dev.agentic.smelter;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.InventoryHelper;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.world.World;

public final class ProofBlock extends Block {
    private final int kind;
    public ProofBlock(String name,int kind){super(Material.IRON);this.kind=kind;setRegistryName(SmelterProof.ID,name);setTranslationKey(SmelterProof.ID+"."+name);setHardness(2);setCreativeTab(CreativeTabs.REDSTONE);}
    @Override public boolean hasTileEntity(IBlockState state){return true;}
    @Override public TileEntity createTileEntity(World world,IBlockState state){return kind==0?new GeneratorTile():kind==1?new PipeTile():new SmelterTile();}
    @Override public boolean onBlockActivated(World world,BlockPos pos,IBlockState state,EntityPlayer player,EnumHand hand,EnumFacing side,float x,float y,float z){
        if(!world.isRemote){
            if(kind==2)player.openGui(SmelterProof.instance,0,world,pos.getX(),pos.getY(),pos.getZ());
            else player.sendMessage(new TextComponentString(kind==0?"Demo RF generator: free 40 RF/t, output EAST. Connect RF pipe on its east side.":"RF pipe: WEST input -> EAST output, 80 RF/t."));
        }
        return true;
    }
    @Override public void breakBlock(World world,BlockPos pos,IBlockState state){
        TileEntity tile=world.getTileEntity(pos);
        if(!world.isRemote && tile instanceof InventoryTile){InventoryHelper.dropInventoryItems(world,pos,(InventoryTile)tile);((InventoryTile)tile).clear();}
        super.breakBlock(world,pos,state);
    }
}
