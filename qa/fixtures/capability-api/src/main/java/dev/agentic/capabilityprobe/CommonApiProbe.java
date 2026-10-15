package dev.agentic.capabilityprobe;

/** Maintainer compile probe only. No registration, gameplay or runtime claims. */
public final class CommonApiProbe {
    private CommonApiProbe() {}
    public static Class<?>[] surfaces() {
        return new Class<?>[] {
            net.minecraft.item.Item.class,
            net.minecraft.item.ItemFood.class,
            net.minecraft.item.ItemSword.class,
            net.minecraft.item.ItemArmor.class,
            net.minecraft.item.crafting.IRecipe.class,
            net.minecraft.block.Block.class,
            net.minecraft.block.BlockCrops.class,
            net.minecraft.block.state.IBlockState.class,
            net.minecraft.tileentity.TileEntity.class,
            net.minecraft.util.ITickable.class,
            net.minecraft.inventory.Container.class,
            net.minecraft.inventory.Slot.class,
            net.minecraftforge.fml.common.network.IGuiHandler.class,
            net.minecraftforge.items.ItemStackHandler.class,
            net.minecraftforge.fluids.FluidTank.class,
            net.minecraftforge.energy.EnergyStorage.class,
            net.minecraftforge.common.capabilities.ICapabilityProvider.class,
            net.minecraft.entity.EntityCreature.class,
            net.minecraft.entity.passive.EntityTameable.class,
            net.minecraft.entity.ai.EntityAIWander.class,
            net.minecraft.entity.ai.EntityAIAttackMelee.class,
            net.minecraftforge.fml.common.registry.EntityRegistry.class,
            net.minecraft.network.datasync.EntityDataManager.class,
            net.minecraft.entity.projectile.EntityThrowable.class,
            net.minecraft.world.storage.WorldSavedData.class,
            net.minecraft.nbt.NBTTagCompound.class,
            net.minecraftforge.fml.common.network.simpleimpl.SimpleNetworkWrapper.class,
            net.minecraftforge.event.entity.player.PlayerInteractEvent.class,
            net.minecraftforge.fml.common.IWorldGenerator.class,
            net.minecraft.world.gen.feature.WorldGenMinable.class,
            net.minecraft.world.gen.structure.StructureComponent.class,
            net.minecraft.world.biome.Biome.class,
            net.minecraftforge.common.DimensionManager.class,
            net.minecraft.world.WorldProvider.class,
            net.minecraft.world.gen.IChunkGenerator.class,
            net.minecraft.command.CommandBase.class,
            net.minecraftforge.common.config.Configuration.class,
            net.minecraft.world.storage.loot.LootTable.class,
            net.minecraft.advancements.Advancement.class,
            net.minecraft.util.SoundEvent.class
        };
    }
    public static net.minecraft.block.Block block() {
        return new net.minecraft.block.Block(net.minecraft.block.material.Material.ROCK);
    }
    public static net.minecraft.item.ItemBlock blockItem(net.minecraft.block.Block block) {
        return new net.minecraft.item.ItemBlock(block);
    }
    public static net.minecraftforge.items.ItemStackHandler inventory() {
        return new net.minecraftforge.items.ItemStackHandler(9);
    }
    public static net.minecraftforge.fluids.FluidTank tank() {
        return new net.minecraftforge.fluids.FluidTank(1000);
    }
    public static net.minecraftforge.energy.EnergyStorage energy() {
        return new net.minecraftforge.energy.EnergyStorage(1000);
    }
    public static abstract class Npc extends net.minecraft.entity.EntityCreature {
        protected Npc(net.minecraft.world.World world) { super(world); }
        @Override protected void initEntityAI() {
            tasks.addTask(1, new net.minecraft.entity.ai.EntityAIWander(this, 1D));
        }
    }
    public static abstract class Menu extends net.minecraft.inventory.Container {
        protected Menu(net.minecraft.inventory.IInventory inventory) {
            addSlotToContainer(new net.minecraft.inventory.Slot(inventory, 0, 8, 18));
        }
    }
}
