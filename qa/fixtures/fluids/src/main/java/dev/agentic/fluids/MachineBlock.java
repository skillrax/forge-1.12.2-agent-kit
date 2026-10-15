package dev.agentic.fluids;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.world.World;
import net.minecraftforge.fluids.FluidUtil;

public final class MachineBlock extends Block {
    private final int kind;
    public MachineBlock(int kind,String name) {
        super(Material.IRON);this.kind=kind;setRegistryName(FluidProof.ID,name);setTranslationKey("fluidproof."+name);
        setCreativeTab(CreativeTabs.MISC);setHardness(2.0F);
    }
    @Override public boolean hasTileEntity(IBlockState state) {return true;}
    @Override public TileEntity createTileEntity(World world,IBlockState state) {return new MachineTile(kind);}
    public boolean dismantle(World world,BlockPos pos) {
        if(world.isRemote || !(world.getTileEntity(pos) instanceof MachineTile))return false;
        MachineTile tile=(MachineTile)world.getTileEntity(pos);
        ItemStack packed=new ItemStack(this);
        NBTTagCompound data=tile.writeToNBT(new NBTTagCompound());
        // Placement supplies tile identity and new coordinates. Carry only machine state.
        for(String key:new String[]{"id","x","y","z"})data.removeTag(key);
        packed.setTagInfo("BlockEntityTag",data);
        tile.wrenchRemoval=true;
        if(!world.setBlockToAir(pos)) {tile.wrenchRemoval=false;return false;}
        spawnAsEntity(world,pos,packed);
        return true;
    }
    @Override public boolean onBlockActivated(World world,BlockPos pos,IBlockState state,EntityPlayer player,EnumHand hand,
        EnumFacing side,float x,float y,float z) {
        if(world.isRemote)return true;
        MachineTile tile=(MachineTile)world.getTileEntity(pos);
        if(tile==null)return true;
        ItemStack held=player.getHeldItem(hand);
        if(!held.isEmpty() && FluidUtil.getFluidHandler(held)!=null) {
            FluidUtil.interactWithFluidHandler(player,hand,world,pos,side);
        } else if(held.isEmpty() && player.isSneaking()) {
            ItemStack take=!tile.output.isEmpty()?tile.output:tile.input;
            if(!take.isEmpty()) {player.setHeldItem(hand,take);if(take==tile.output)tile.output=ItemStack.EMPTY;else tile.input=ItemStack.EMPTY;tile.markDirty();}
        } else if(kind!=1 && !held.isEmpty() && held.getItem()==tile.ingredient()) {
            int count=Math.min(held.getCount(),64-tile.input.getCount());
            if(count>0) {
                if(tile.input.isEmpty())tile.input=new ItemStack(held.getItem(),count);else tile.input.grow(count);
                held.shrink(count);tile.markDirty();
            }
        }
        player.sendMessage(new TextComponentString(getLocalizedName()+": "+tile.tank.getFluidAmount()+"/4000 mB; input="+tile.input.getCount()
            +"; output="+tile.output.getCount()+"; progress="+tile.progress+"/"+(kind==0?40:kind==2?20:0)
            +"; completed="+tile.completed+". "+(kind==2?"Cobblestone + 250 mB -> clay. Sneak + empty hand takes items.":"Flow EAST (+X), 100 mB/t. Sneak + empty hand takes unused input.")));
        return true;
    }
    @Override public void breakBlock(World world,BlockPos pos,IBlockState state) {
        TileEntity raw=world.getTileEntity(pos);
        if(!world.isRemote && raw instanceof MachineTile && !((MachineTile)raw).wrenchRemoval) {
            MachineTile tile=(MachineTile)raw;
            if(!tile.input.isEmpty())spawnAsEntity(world,pos,tile.input);
            if(!tile.output.isEmpty())spawnAsEntity(world,pos,tile.output);
        }
        super.breakBlock(world,pos,state);
    }
}
