package com.example.rubymod;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;

/** guides/players.md: small per-player values under the persisted NBT (kept across death and logout). */
public final class PlayerData {
    static NBTTagCompound data(EntityPlayer player) {                        // read
        return player.getEntityData().getCompoundTag(EntityPlayer.PERSISTED_NBT_TAG).getCompoundTag("MyModData");
    }

    static void save(EntityPlayer player, NBTTagCompound state) {             // write: attach both levels
        NBTTagCompound root = player.getEntityData();
        NBTTagCompound persisted = root.getCompoundTag(EntityPlayer.PERSISTED_NBT_TAG);
        persisted.setTag("MyModData", state);
        root.setTag(EntityPlayer.PERSISTED_NBT_TAG, persisted);
    }

    private PlayerData() {}
}
