package dev.agentic.clockworkbottle;

import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.tileentity.TileEntityFurnace;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.world.World;
import net.minecraftforge.event.world.ChunkEvent;
import net.minecraftforge.event.world.WorldEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.PlayerEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

@Mod.EventBusSubscriber(modid = ClockworkBottle.MOD_ID)
public final class FurnaceBoosts {
    private static final Map<World, LinkedHashMap<BlockPos, Boost>> WORLDS = new IdentityHashMap<>();
    private static int reservedExtraTicks;
    private FurnaceBoosts() {}

    private static final class Boost {
        final TileEntityFurnace furnace;
        final UUID owner;
        final int multiplier;
        int remainingTicks = BoostMath.DURATION_TICKS;
        Boost(TileEntityFurnace furnace, UUID owner, int multiplier) {
            this.furnace = furnace; this.owner = owner; this.multiplier = multiplier;
        }
    }

    private static boolean valid(World world, BlockPos pos, Boost boost) {
        // The loaded check precedes getTileEntity: no chunk loads for an active effect.
        return !world.isRemote && world.isBlockLoaded(pos, false) && !boost.furnace.isInvalid()
            && world.getTileEntity(pos) == boost.furnace && boost.remainingTicks > 0;
    }

    private static void discard(Boost boost) { reservedExtraTicks -= boost.multiplier - 1; }

    public static BoostSnapshot snapshot(World world) {
        LinkedHashMap<BlockPos, Boost> boosts = WORLDS.get(world);
        int count = 0;
        if (boosts != null) for (Map.Entry<BlockPos, Boost> entry : boosts.entrySet())
            if (valid(world, entry.getKey(), entry.getValue())) count++;
        long[] positions = new long[count]; int[] multipliers = new int[count], ticks = new int[count];
        int index = 0;
        if (boosts != null) for (Map.Entry<BlockPos, Boost> entry : boosts.entrySet()) {
            if (!valid(world, entry.getKey(), entry.getValue())) continue;
            positions[index] = entry.getKey().toLong(); multipliers[index] = entry.getValue().multiplier;
            ticks[index++] = entry.getValue().remainingTicks;
        }
        return new BoostSnapshot(world.provider.getDimension(), positions, multipliers, ticks);
    }

    private static void publish(World world) {
        if (!world.isRemote) BoostNetwork.CHANNEL.sendToDimension(snapshot(world), world.provider.getDimension());
    }

    private static void prune() {
        Iterator<Map.Entry<World, LinkedHashMap<BlockPos, Boost>>> worlds = WORLDS.entrySet().iterator();
        while (worlds.hasNext()) {
            Map.Entry<World, LinkedHashMap<BlockPos, Boost>> world = worlds.next();
            boolean changed = false;
            Iterator<Map.Entry<BlockPos, Boost>> boosts = world.getValue().entrySet().iterator();
            while (boosts.hasNext()) {
                Map.Entry<BlockPos, Boost> entry = boosts.next();
                if (!valid(world.getKey(), entry.getKey(), entry.getValue())) {
                    discard(entry.getValue()); boosts.remove(); changed = true;
                }
            }
            if (changed) publish(world.getKey());
            if (world.getValue().isEmpty()) worlds.remove();
        }
    }

    public static EnumActionResult activate(World world, BlockPos pos, EntityPlayer player, ItemStack bottle) {
        if (world.isRemote || !world.isBlockLoaded(pos, false)) return EnumActionResult.FAIL;
        TileEntity target = world.getTileEntity(pos);
        if (target == null || target.getClass() != TileEntityFurnace.class || target.isInvalid())
            return reject(player, "clockworkbottle.furnace.unsupported");
        prune();
        LinkedHashMap<BlockPos, Boost> boosts = WORLDS.get(world);
        Boost old = boosts == null ? null : boosts.get(pos);
        int multiplier = BoostMath.nextMultiplier(old == null ? 0 : old.multiplier);
        long cost = BoostMath.costSeconds(multiplier);
        if (ChargeMath.spend(BottleItem.chargeTicks(bottle), cost) < 0) {
            player.sendStatusMessage(new TextComponentTranslation("clockworkbottle.insufficient", cost), true);
            return EnumActionResult.FAIL;
        }
        int replaced = old == null ? 0 : old.multiplier - 1;
        if (!BoostMath.fitsBudget(reservedExtraTicks, replaced, multiplier))
            return reject(player, "clockworkbottle.furnace.busy");
        // Validation and reservation happen before the sole charge mutation.
        if (!BottleItem.spendSeconds(bottle, cost)) return EnumActionResult.FAIL;
        if (boosts == null) { boosts = new LinkedHashMap<>(); WORLDS.put(world, boosts); }
        boosts.put(pos.toImmutable(), new Boost((TileEntityFurnace) target, player.getUniqueID(), multiplier));
        reservedExtraTicks = reservedExtraTicks - replaced + multiplier - 1;
        publish(world);
        return EnumActionResult.SUCCESS;
    }

    private static EnumActionResult reject(EntityPlayer player, String key) {
        player.sendStatusMessage(new TextComponentTranslation(key), true);
        return EnumActionResult.FAIL;
    }

    public static int multiplier(World world, BlockPos pos) {
        LinkedHashMap<BlockPos, Boost> boosts = WORLDS.get(world);
        Boost boost = boosts == null ? null : boosts.get(pos);
        return boost != null && valid(world, pos, boost) ? boost.multiplier : 0;
    }

    public static int remainingTicks(World world, BlockPos pos) {
        LinkedHashMap<BlockPos, Boost> boosts = WORLDS.get(world);
        Boost boost = boosts == null ? null : boosts.get(pos);
        return boost != null && valid(world, pos, boost) ? boost.remainingTicks : 0;
    }

    @SubscribeEvent public static void tick(TickEvent.WorldTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.world.isRemote) return;
        LinkedHashMap<BlockPos, Boost> boosts = WORLDS.get(event.world);
        if (boosts == null) return;
        boolean changed = false;
        Iterator<Map.Entry<BlockPos, Boost>> iterator = boosts.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<BlockPos, Boost> entry = iterator.next(); Boost boost = entry.getValue();
            if (!valid(event.world, entry.getKey(), boost)) {
                discard(boost); iterator.remove(); changed = true; continue;
            }
            // Vanilla has already supplied one update. Fuel and cooking use the real furnace hook.
            for (int extra = 1; extra < boost.multiplier; extra++) boost.furnace.update();
            if (--boost.remainingTicks == 0) { discard(boost); iterator.remove(); changed = true; }
        }
        if (changed || event.world.getTotalWorldTime() % 20 == 0) publish(event.world);
        if (boosts.isEmpty()) WORLDS.remove(event.world);
    }

    @SubscribeEvent public static void chunkUnloaded(ChunkEvent.Unload event) {
        if (event.getWorld().isRemote) return;
        LinkedHashMap<BlockPos, Boost> boosts = WORLDS.get(event.getWorld());
        if (boosts == null) return;
        Iterator<Map.Entry<BlockPos, Boost>> iterator = boosts.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<BlockPos, Boost> entry = iterator.next();
            if ((entry.getKey().getX() >> 4) == event.getChunk().x
                    && (entry.getKey().getZ() >> 4) == event.getChunk().z) {
                discard(entry.getValue()); iterator.remove();
            }
        }
        publish(event.getWorld());
        if (boosts.isEmpty()) WORLDS.remove(event.getWorld());
    }

    @SubscribeEvent public static void worldUnloaded(WorldEvent.Unload event) {
        LinkedHashMap<BlockPos, Boost> boosts = WORLDS.remove(event.getWorld());
        if (boosts != null) for (Boost boost : boosts.values()) discard(boost);
    }

    @SubscribeEvent public static void playerLeft(PlayerEvent.PlayerLoggedOutEvent event) {
        UUID owner = event.player.getUniqueID();
        Iterator<Map.Entry<World, LinkedHashMap<BlockPos, Boost>>> worlds = WORLDS.entrySet().iterator();
        while (worlds.hasNext()) {
            Map.Entry<World, LinkedHashMap<BlockPos, Boost>> world = worlds.next();
            LinkedHashMap<BlockPos, Boost> boosts = world.getValue(); boolean changed = false;
            Iterator<Boost> iterator = boosts.values().iterator();
            while (iterator.hasNext()) { Boost boost = iterator.next();
                if (boost.owner.equals(owner)) { discard(boost); iterator.remove(); changed = true; }
            }
            if (changed) publish(world.getKey());
            if (boosts.isEmpty()) worlds.remove();
        }
    }

    public static void clear() { WORLDS.clear(); reservedExtraTicks = 0; }
}
