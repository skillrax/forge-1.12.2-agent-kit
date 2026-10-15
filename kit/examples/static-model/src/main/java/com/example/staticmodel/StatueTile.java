package com.example.staticmodel;

import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.AxisAlignedBB;

public final class StatueTile extends TileEntity {
    @Override public AxisAlignedBB getRenderBoundingBox(){return new AxisAlignedBB(pos.add(-1,0,-1),pos.add(2,4,2));}
    @Override public double getMaxRenderDistanceSquared(){return 4096;}
}
