package com.example.rubymod;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.WorldServer;
import net.minecraft.world.storage.MapStorage;
import net.minecraft.world.storage.WorldSavedData;

/** Captured regions, saved with the world (data/rubymod_bags.dat). The bag stack only holds an ID. */
public final class BagStore extends WorldSavedData {
    private static final String NAME = RubymodMod.MODID + "_bags";
    private NBTTagCompound contents = new NBTTagCompound();
    public BagStore() { super(NAME); }
    public BagStore(String name) { super(name); }      // used by MapStorage when loading

    public static BagStore get(WorldServer world) {
        MapStorage storage = world.getMinecraftServer().getWorld(0).getMapStorage();   // one store for all dimensions
        BagStore store = (BagStore) storage.getOrLoadData(BagStore.class, NAME);
        if (store == null) { store = new BagStore(); storage.setData(NAME, store); }
        return store;
    }
    public NBTTagCompound find(String id) { return contents.hasKey(id, 10) ? contents.getCompoundTag(id) : null; }
    public void put(String id, NBTTagCompound data) { contents.setTag(id, data); markDirty(); }
    public void remove(String id) { contents.removeTag(id); markDirty(); }
    @Override public void readFromNBT(NBTTagCompound nbt) { contents = nbt.getCompoundTag("Contents"); }
    @Override public NBTTagCompound writeToNBT(NBTTagCompound nbt) { nbt.setTag("Contents", contents); return nbt; }
}
