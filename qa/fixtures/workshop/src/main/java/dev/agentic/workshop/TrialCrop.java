package dev.agentic.workshop;
import net.minecraft.block.BlockCrops;
import net.minecraft.item.Item;
public final class TrialCrop extends BlockCrops {
    public TrialCrop() {setRegistryName(Workshop.ID,"test_crop");setTranslationKey(Workshop.ID+".test_crop");}
    @Override protected Item getSeed() {return Content.SEEDS;}
    @Override protected Item getCrop() {return Content.HARVEST;}
    @Override public net.minecraftforge.common.EnumPlantType getPlantType(net.minecraft.world.IBlockAccess world,net.minecraft.util.math.BlockPos pos) {
        return net.minecraftforge.common.EnumPlantType.Crop;
    }
}
