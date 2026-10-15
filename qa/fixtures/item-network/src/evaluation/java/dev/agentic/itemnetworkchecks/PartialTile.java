package dev.agentic.itemnetworkchecks;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.items.*;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.world.World;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
/** Isolated contract-risk check: a receiver that accepts less at commit than simulation. */
@Mod.EventBusSubscriber(modid="itemnetworkchecks")
public final class PartialTile extends TileEntity {
    public static final Block RECEIVER=new Block(Material.IRON){
        @Override public boolean hasTileEntity(IBlockState s){return true;}
        @Override public TileEntity createTileEntity(World w,IBlockState s){return new PartialTile();}
    }.setRegistryName("itemnetworkchecks","partial");
    @SubscribeEvent public static void blocks(RegistryEvent.Register<Block> e){e.getRegistry().register(RECEIVER);}
    public final ItemStackHandler items=new ItemStackHandler(1){
        @Override public ItemStack insertItem(int slot,ItemStack offered,boolean simulate){
            if(simulate)return super.insertItem(slot,offered.copy(),true);
            ItemStack part=offered.copy();part.setCount(Math.min(3,part.getCount()));
            ItemStack rem=super.insertItem(slot,part,false);ItemStack remaining=offered.copy();remaining.shrink(part.getCount()-rem.getCount());return remaining;
        }
    };
    @Override public boolean hasCapability(Capability<?> c,EnumFacing f){return c==CapabilityItemHandler.ITEM_HANDLER_CAPABILITY || super.hasCapability(c,f);}
    @Override public <T>T getCapability(Capability<T> c,EnumFacing f){return c==CapabilityItemHandler.ITEM_HANDLER_CAPABILITY?CapabilityItemHandler.ITEM_HANDLER_CAPABILITY.cast(items):super.getCapability(c,f);}
    @Override public NBTTagCompound writeToNBT(NBTTagCompound n){super.writeToNBT(n);n.setTag("Items",items.serializeNBT());return n;}
    @Override public void readFromNBT(NBTTagCompound n){super.readFromNBT(n);items.deserializeNBT(n.getCompoundTag("Items"));}
}
