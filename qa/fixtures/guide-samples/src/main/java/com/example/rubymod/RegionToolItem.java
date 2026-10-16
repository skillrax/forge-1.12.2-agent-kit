package com.example.rubymod;

import java.io.IOException;
import java.util.UUID;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;

/** Right-click two corners to capture the cuboid; right-click a block face to place it next to that face. */
public class RegionToolItem extends Item {
    public RegionToolItem() { setMaxStackSize(1); setCreativeTab(CreativeTabs.TOOLS); }

    @Override   // runs before the clicked block's onBlockActivated, so clicking a chest does not open it
    public EnumActionResult onItemUseFirst(EntityPlayer player, World world, BlockPos pos, EnumFacing side, float hitX, float hitY, float hitZ, EnumHand hand) {
        if (world.isRemote) return EnumActionResult.SUCCESS;             // client: SUCCESS still sends the click to the server
        WorldServer server = (WorldServer) world;
        ItemStack tool = player.getHeldItem(hand);
        NBTTagCompound tag = tool.hasTagCompound() ? tool.getTagCompound() : new NBTTagCompound();
        tool.setTagCompound(tag);
        ModSavedData store = ModSavedData.get(server);
        try {
            if (tag.hasKey("Capture")) {
                NBTTagCompound data = store.find(tag.getString("Capture"));
                RegionMover.require(data != null, "This capture was already placed.");
                RegionMover.place(server, player, data, pos.offset(side));
                store.remove(tag.getString("Capture"));                    // a copied tool cannot place it again
                tag.removeTag("Capture"); tag.removeTag("Size"); tag.removeTag("Preview");
                say(player, "Placed.");
            } else if (!tag.hasKey("First") || tag.getInteger("Dim") != world.provider.getDimension()) {
                tag.setLong("First", pos.toLong());
                tag.setInteger("Dim", world.provider.getDimension());
                say(player, "First corner set.");
            } else {
                BlockPos a = BlockPos.fromLong(tag.getLong("First"));
                tag.removeTag("First");
                BlockPos min = new BlockPos(Math.min(a.getX(), pos.getX()), Math.min(a.getY(), pos.getY()), Math.min(a.getZ(), pos.getZ()));
                BlockPos size = new BlockPos(Math.abs(a.getX() - pos.getX()) + 1, Math.abs(a.getY() - pos.getY()) + 1, Math.abs(a.getZ() - pos.getZ()) + 1);
                int max = RegionMover.MAX_SIDE;
                RegionMover.require(size.getX() <= max && size.getY() <= max && size.getZ() <= max, "At most " + max + " blocks per side.");
                NBTTagCompound data = RegionMover.capture(server, player, min, size);
                String id = UUID.randomUUID().toString();
                store.put(id, data);
                tag.setString("Capture", id);
                tag.setLong("Size", size.toLong());
                tag.setIntArray("Preview", data.getIntArray("Preview"));   // small, for the client ghost
                say(player, "Captured " + size.getX() + "x" + size.getY() + "x" + size.getZ() + ".");
            }
        } catch (RegionMover.Refused | IOException e) {
            say(player, e.getMessage());
        }
        return EnumActionResult.SUCCESS;
    }

    private static void say(EntityPlayer player, String text) { player.sendStatusMessage(new TextComponentString(text), true); }
}
