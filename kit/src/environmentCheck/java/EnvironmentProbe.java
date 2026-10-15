import net.minecraft.item.Item;
import net.minecraftforge.fml.common.Mod;

/** Compiled only by verifyEnvironment; never included in the mod jar. */
@Mod(modid = "environmentprobe", name = "Environment Probe", version = "1")
public final class EnvironmentProbe {
    @SuppressWarnings("unused")
    private final Item item = new Item().setRegistryName("environmentprobe", "probe");
}
