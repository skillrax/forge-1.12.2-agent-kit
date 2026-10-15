package com.example.rubymod;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.*;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityList;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.nbt.*;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.storage.ExtendedBlockStorage;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.world.BlockEvent;

/** Moves a cuboid (block states, tile entities, entities) without drops or block updates. Server only. */
public final class RegionMover {
    public static final class Refused extends RuntimeException { public Refused(String reason) { super(reason); } }
    public static void require(boolean ok, String reason) { if (!ok) throw new Refused(reason); }
    public static final int MAX_SIDE = 10, MAX_BYTES = 8 * 1024 * 1024;

    /** Cell i of the region; the same order is used by capture, place and the ghost preview. */
    public static BlockPos at(BlockPos min, BlockPos size, int i) {
        return min.add(i % size.getX(), i / size.getX() % size.getY(), i / (size.getX() * size.getY()));
    }
    public static int volume(BlockPos size) { return size.getX() * size.getY() * size.getZ(); }
    public static AxisAlignedBB box(BlockPos min, BlockPos size) { return new AxisAlignedBB(min, min.add(size)); }

    /** Saves the region, then removes it. Refused leaves the world unchanged. */
    public static NBTTagCompound capture(WorldServer w, EntityPlayer player, BlockPos min, BlockPos size) throws IOException {
        requireLoaded(w, min, size);
        NBTTagCompound data = new NBTTagCompound();
        data.setLong("Origin", min.toLong());
        data.setLong("Size", size.toLong());
        NBTTagList cells = new NBTTagList();
        int[] preview = new int[volume(size)];
        for (int i = 0; i < volume(size); i++) {
            BlockPos p = at(min, size, i);
            IBlockState state = w.getBlockState(p);
            require(state.getBlockHardness(w, p) >= 0 && state.getBlock() != Blocks.PISTON_EXTENSION && w.isBlockModifiable(player, p),
                    "Cannot take " + state.getBlock().getRegistryName() + " at " + p);
            require(!MinecraftForge.EVENT_BUS.post(new BlockEvent.BreakEvent(w, p, state, player)), "Protected at " + p);
            NBTTagCompound cell = NBTUtil.writeBlockState(new NBTTagCompound(), state);
            TileEntity te = w.getTileEntity(p);
            if (te != null) cell.setTag("Tile", te.writeToNBT(new NBTTagCompound()));
            cells.appendTag(cell);
            preview[i] = Block.getStateId(state);
        }
        data.setTag("Blocks", cells);
        data.setIntArray("Preview", preview);
        AxisAlignedBB box = box(min, size);
        List<Entity> entities = w.getEntitiesWithinAABB(Entity.class, box);
        NBTTagList saved = new NBTTagList();
        for (Entity e : entities) {
            AxisAlignedBB b = e.getEntityBoundingBox();
            require(!(e instanceof EntityPlayer) && e.getParts() == null, "Players and multipart mobs cannot be captured.");
            require(b.minX >= box.minX && b.minY >= box.minY && b.minZ >= box.minZ && b.maxX <= box.maxX && b.maxY <= box.maxY && b.maxZ <= box.maxZ,
                    e.getName() + " crosses the edge of the selection.");
            require(!e.isRiding() && !e.isBeingRidden(), "Mounted entities are not supported.");
            NBTTagCompound n = new NBTTagCompound();
            require(e.writeToNBTOptional(n), e.getName() + " cannot be saved.");   // includes "id" and UUID
            saved.appendTag(n);
        }
        data.setTag("Entities", saved);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        CompressedStreamTools.write(data, new DataOutputStream(bytes));
        require(bytes.size() <= MAX_BYTES, "Too much data in this area.");
        // Everything is saved and checked: now remove.
        for (Entity e : entities) detach(w, e);
        clear(w, min, size);
        refresh(w, min, size);
        return data;
    }

    /** Restores a payload with its minimum corner at min, in any loaded world (also another dimension). */
    public static void place(WorldServer w, EntityPlayer player, NBTTagCompound data, BlockPos min) {
        BlockPos size = BlockPos.fromLong(data.getLong("Size"));
        require(min.getY() >= 0 && min.getY() + size.getY() <= 256, "Outside the build height.");
        requireLoaded(w, min, size);
        require(w.getEntitiesWithinAABB(Entity.class, box(min, size)).isEmpty(), "An entity is in the way.");
        NBTTagList cells = data.getTagList("Blocks", 10);
        BlockPos delta = min.subtract(BlockPos.fromLong(data.getLong("Origin")));
        // Check and build everything first; the world changes only when nothing can fail.
        Map<BlockPos, TileEntity> tiles = new LinkedHashMap<>();
        for (int i = 0; i < cells.tagCount(); i++) {
            BlockPos p = at(min, size, i);
            NBTTagCompound cell = cells.getCompoundTagAt(i);
            require(w.isAirBlock(p) && w.getTileEntity(p) == null && w.isBlockModifiable(player, p), "Blocked at " + p);
            require(Block.REGISTRY.containsKey(new ResourceLocation(cell.getString("Name"))), "Missing block " + cell.getString("Name"));
            if (!cell.hasKey("Tile")) continue;
            NBTTagCompound tag = cell.getCompoundTag("Tile").copy();
            tag.setInteger("x", p.getX()); tag.setInteger("y", p.getY()); tag.setInteger("z", p.getZ());   // TE position lives in its NBT
            TileEntity te = TileEntity.create(w, tag);
            require(te != null, "Missing tile entity " + tag.getString("id"));
            tiles.put(p, te);
        }
        NBTTagList saved = data.getTagList("Entities", 10);
        List<Entity> entities = new ArrayList<>();
        for (int i = 0; i < saved.tagCount(); i++) {
            NBTTagCompound n = saved.getCompoundTagAt(i).copy();
            translate(n, delta);
            Entity e = EntityList.createEntityFromNBT(n, w);                   // keeps the saved UUID
            require(e != null, "Missing entity " + n.getString("id"));
            for (WorldServer any : w.getMinecraftServer().worlds)
                require(any.getEntityFromUuid(e.getUniqueID()) == null, e.getName() + " already exists (copied capture?)");
            entities.add(e);
        }
        for (int i = 0; i < cells.tagCount(); i++) rawSet(w, at(min, size, i), NBTUtil.readBlockState(cells.getCompoundTagAt(i)));
        for (Map.Entry<BlockPos, TileEntity> t : tiles.entrySet()) w.getChunk(t.getKey()).addTileEntity(t.getKey(), t.getValue());
        for (TileEntity te : tiles.values()) w.addTileEntity(te);             // tick lists + onLoad, after all neighbors exist
        for (Entity e : entities) { e.dimension = w.provider.getDimension(); require(w.spawnEntity(e), "Spawn refused: " + e.getName()); }
        refresh(w, min, size);
    }

    /** All chunks the region and its neighbors touch are loaded and not queued for unload (never load or generate). */
    static void requireLoaded(WorldServer w, BlockPos min, BlockPos size) {
        Set<Long> loaded = new HashSet<>();
        for (Chunk c : w.getChunkProvider().getLoadedChunks()) if (!c.unloadQueued) loaded.add(ChunkPos.asLong(c.x, c.z));
        for (int x = (min.getX() - 1) >> 4; x <= (min.getX() + size.getX()) >> 4; x++)
            for (int z = (min.getZ() - 1) >> 4; z <= (min.getZ() + size.getZ()) >> 4; z++)
                require(loaded.contains(ChunkPos.asLong(x, z)), "Part of the area is not loaded.");
    }

    /** Removes without breakBlock (chests would spill) or neighbor updates (torches would pop off). */
    static void clear(WorldServer w, BlockPos min, BlockPos size) {
        List<TileEntity> tiles = new ArrayList<>();
        for (int i = 0; i < volume(size); i++) {
            TileEntity te = w.getChunk(at(min, size, i)).getTileEntityMap().get(at(min, size, i));   // never creates one
            if (te != null) tiles.add(te);
        }
        for (TileEntity te : tiles) te.onChunkUnload();                        // 1: networks disconnect while blocks still exist
        for (int i = 0; i < volume(size); i++) rawSet(w, at(min, size, i), Blocks.AIR.getDefaultState());   // 2: all air
        for (int i = 0; i < volume(size); i++) w.removeTileEntity(at(min, size, i));                       // 3: drop tiles
    }

    /** Writes a state straight into chunk storage: no breakBlock, onBlockAdded, drops or neighbor updates. */
    static void rawSet(World w, BlockPos p, IBlockState state) {
        Chunk c = w.getChunk(p);
        ExtendedBlockStorage[] sections = c.getBlockStorageArray();
        int y = p.getY() >> 4;
        if (sections[y] == Chunk.NULL_BLOCK_STORAGE) sections[y] = new ExtendedBlockStorage(y << 4, w.provider.hasSkyLight());
        sections[y].set(p.getX() & 15, p.getY() & 15, p.getZ() & 15, state);
        c.markDirty();
    }

    /** Raw writes skip lighting and client sync: redo both. */
    static void refresh(WorldServer w, BlockPos min, BlockPos size) {
        Set<Chunk> chunks = new HashSet<>();
        for (int i = 0; i < volume(size); i++) chunks.add(w.getChunk(at(min, size, i)));
        for (Chunk c : chunks) { Arrays.fill(c.getHeightMap(), 0); c.generateSkylightMap(); }
        for (int i = 0; i < volume(size); i++) {
            BlockPos p = at(min, size, i);
            IBlockState s = w.getBlockState(p);
            w.checkLight(p);
            w.notifyBlockUpdate(p, s, s, 2);                                   // sends the block (and tile) to clients
        }
    }

    /** Not setDead(): that runs death logic and item drops. onEntityRemoved frees the UUID for respawning. */
    static void detach(WorldServer w, Entity e) {
        w.getChunk(e.chunkCoordX, e.chunkCoordZ).removeEntity(e);
        w.loadedEntityList.remove(e);
        w.onEntityRemoved(e);
    }

    static void translate(NBTTagCompound n, BlockPos d) {
        NBTTagList pos = n.getTagList("Pos", 6), moved = new NBTTagList();
        moved.appendTag(new NBTTagDouble(pos.getDoubleAt(0) + d.getX()));
        moved.appendTag(new NBTTagDouble(pos.getDoubleAt(1) + d.getY()));
        moved.appendTag(new NBTTagDouble(pos.getDoubleAt(2) + d.getZ()));
        n.setTag("Pos", moved);
        shift(n, "TileX", "TileY", "TileZ", d);                                // paintings, item frames, leash knots
        shift(n, "xTile", "yTile", "zTile", d);                                // arrows stuck in blocks
    }
    static void shift(NBTTagCompound n, String x, String y, String z, BlockPos d) {
        if (!n.hasKey(x, 99)) return;
        n.setInteger(x, n.getInteger(x) + d.getX()); n.setInteger(y, n.getInteger(y) + d.getY()); n.setInteger(z, n.getInteger(z) + d.getZ());
    }

    private RegionMover() {}
}
