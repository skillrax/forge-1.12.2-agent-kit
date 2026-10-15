package dev.agentic.workshop;
import net.minecraft.world.DimensionType;
import net.minecraftforge.common.DimensionManager;
import net.minecraftforge.common.config.Configuration;
import java.io.File;
public final class TrialDimension {
    public static int id;
    public static DimensionType type;
    private TrialDimension() {}
    public static void register(File file) {
        Configuration config=new Configuration(file);config.load();
        id=config.getInt("dimensionId","dimension",72,2,10000,"Use the same free ID on server and clients. Never change it for an existing saved world.");
        if(config.hasChanged())config.save();
        if(DimensionManager.isDimensionRegistered(id))throw new IllegalStateException("Workshop dimension ID "+id+" is occupied; choose a free dimensionId in "+file);
        for(DimensionType existing:DimensionType.values())if(existing.getId()==id)
            throw new IllegalStateException("Workshop dimension type ID "+id+" is occupied; choose another dimensionId in "+file);
        type=DimensionType.register("kitworkshop_trial","_kitworkshop_trial",id,TrialWorldProvider.class,false);
        DimensionManager.registerDimension(id,type);
    }
}