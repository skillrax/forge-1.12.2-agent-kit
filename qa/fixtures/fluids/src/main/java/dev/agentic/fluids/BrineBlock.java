package dev.agentic.fluids;

import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.DamageSource;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.fluids.BlockFluidClassic;
import net.minecraftforge.fluids.Fluid;

public final class BrineBlock extends BlockFluidClassic {
    public BrineBlock(Fluid fluid) {
        super(fluid,Material.WATER);
        setRegistryName(FluidProof.ID,"biting_brine");setTranslationKey("fluidproof.biting_brine");
        // Viscosity initializes tickRate in the base constructor; set both deliberately.
        setTickRate(20);setQuantaPerBlock(4);
        // Keep the configured cadence observable; no random tick acceleration.
        setTickRandomly(false);
    }
    @Override public void onEntityCollision(World world,BlockPos pos,IBlockState state,Entity entity) {
        super.onEntityCollision(world,pos,state,entity);
        if(!world.isRemote && entity instanceof EntityPlayer && world.getTotalWorldTime()%20==0
            && entity.getEntityBoundingBox().minY<pos.getY()+getFilledPercentage(world,pos))
            entity.attackEntityFrom(DamageSource.MAGIC,2.0F);
    }
}
