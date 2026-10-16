package com.example.rubymod;

import javax.annotation.Nullable;
import net.minecraft.block.state.IBlockState;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.play.server.SPacketUpdateTileEntity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ITickable;
import net.minecraft.util.EnumFacing;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.energy.CapabilityEnergy;
import net.minecraftforge.energy.EnergyStorage;
import net.minecraftforge.items.CapabilityItemHandler;
import net.minecraftforge.items.ItemStackHandler;

/** Block entity for ruby_box. Saved state: writeToNBT/readFromNBT. Client copy: getUpdateTag/onDataPacket + sync(). */
public class RubyBoxTile extends TileEntity implements ITickable {
    public int ticks;

    /** 9 slots, exposed to hoppers/pipes on every side. */
    public final ItemStackHandler inventory = new ItemStackHandler(9) {
        @Override protected void onContentsChanged(int slot) { markDirty(); }
    };
    /** Capacity, max input and max output per tick (not saved here: a real machine saves it). */
    public final EnergyStorage energy = new EnergyStorage(10000, 100, 100);

    /** No comparator output, so save without TileEntity.markDirty's comparator update (guides/machines.md). */
    @Override public void markDirty() {
        if (world != null && !world.isRemote) world.markChunkDirty(pos, this);
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound tag) {
        super.writeToNBT(tag);
        tag.setInteger("ticks", ticks);
        tag.setTag("inventory", inventory.serializeNBT());
        return tag;
    }

    @Override
    public void readFromNBT(NBTTagCompound tag) {
        super.readFromNBT(tag);
        ticks = tag.getInteger("ticks");
        inventory.deserializeNBT(tag.getCompoundTag("inventory"));
    }

    /** Runs every tick on both sides; game logic belongs on the server. */
    @Override
    public void update() {
        if (world.isRemote) return;
        ticks++;
        // TODO behaviour; call markDirty() after changing saved state, sync() if clients must see it.
    }
    @Override
    public boolean hasCapability(Capability<?> cap, EnumFacing side) {
        return cap == CapabilityItemHandler.ITEM_HANDLER_CAPABILITY || cap == CapabilityEnergy.ENERGY || super.hasCapability(cap, side);
    }

    @Override
    public <T> T getCapability(Capability<T> cap, EnumFacing side) {
        if (cap == CapabilityItemHandler.ITEM_HANDLER_CAPABILITY) return CapabilityItemHandler.ITEM_HANDLER_CAPABILITY.cast(inventory);
        if (cap == CapabilityEnergy.ENERGY) return CapabilityEnergy.ENERGY.cast(energy);
        return super.getCapability(cap, side);
    }
    /** Sends this tile's NBT to clients watching it. */
    public void sync() {
        if (world != null && !world.isRemote) {
            IBlockState state = world.getBlockState(pos);
            world.notifyBlockUpdate(pos, state, state, 3);
        }
    }

    @Override
    public NBTTagCompound getUpdateTag() { return writeToNBT(new NBTTagCompound()); }

    @Nullable
    @Override
    public SPacketUpdateTileEntity getUpdatePacket() { return new SPacketUpdateTileEntity(pos, 0, getUpdateTag()); }

    @Override
    public void onDataPacket(NetworkManager net, SPacketUpdateTileEntity packet) { readFromNBT(packet.getNbtCompound()); }
}