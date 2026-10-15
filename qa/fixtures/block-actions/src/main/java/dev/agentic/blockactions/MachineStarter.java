package dev.agentic.blockactions;

import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemBlock;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.SidedProxy;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.network.IGuiHandler;
import net.minecraftforge.fml.common.network.NetworkRegistry;
import net.minecraftforge.fml.common.registry.GameRegistry;

@Mod(modid=MachineStarter.ID,name="Block Actions Proof",version="1.0.0",acceptedMinecraftVersions="[1.12.2]")
@Mod.EventBusSubscriber(modid=MachineStarter.ID)
public final class MachineStarter {
    static {net.minecraftforge.fluids.FluidRegistry.enableUniversalBucket();}
    public static final String ID="blockactions";
    public static final net.minecraftforge.fluids.Fluid PROOF_FLUID=new net.minecraftforge.fluids.Fluid("blockactions_proof",new ResourceLocation("minecraft:blocks/water_still"),new ResourceLocation("minecraft:blocks/water_flow")).setColor(0xFF50D080);
    static {net.minecraftforge.fluids.FluidRegistry.registerFluid(PROOF_FLUID);net.minecraftforge.fluids.FluidRegistry.addBucketForFluid(PROOF_FLUID);}
    public static final net.minecraftforge.fluids.BlockFluidClassic FLUID_BLOCK=new net.minecraftforge.fluids.BlockFluidClassic(PROOF_FLUID,net.minecraft.block.material.Material.WATER);
    public static final MachineBlock MACHINE=new MachineBlock();
    @Mod.Instance(ID) public static MachineStarter instance;
    @SidedProxy(clientSide="dev.agentic.blockactions.ClientHooks",serverSide="dev.agentic.blockactions.MachineStarter$CommonProxy")
    public static CommonProxy proxy;
    public static class CommonProxy {
        public Object screen(EntityPlayer player,MachineTile tile) {return null;}
    }
    @SubscribeEvent public static void blocks(RegistryEvent.Register<Block> event) {
        FLUID_BLOCK.setRegistryName(ID,"proof_fluid");FLUID_BLOCK.setTranslationKey(ID+".proof_fluid");
        FLUID_BLOCK.setQuantaPerBlock(4);FLUID_BLOCK.setTickRate(20);
        event.getRegistry().register(FLUID_BLOCK);
        event.getRegistry().register(MACHINE);
        GameRegistry.registerTileEntity(MachineTile.class,new ResourceLocation(ID,"machine"));
    }
    @SubscribeEvent public static void items(RegistryEvent.Register<Item> event) {
        event.getRegistry().register(new ItemBlock(MACHINE).setRegistryName(MACHINE.getRegistryName()));
    }
    @Mod.EventHandler public void preInit(FMLPreInitializationEvent event) {
        NetworkRegistry.INSTANCE.registerGuiHandler(this,new IGuiHandler() {
            private MachineTile tile(int id,World world,int x,int y,int z) {
                BlockPos pos=new BlockPos(x,y,z);
                if(id!=0)return null;
                TileEntity tile;
                if(world.isRemote){if(!world.isBlockLoaded(pos))return null;tile=world.getTileEntity(pos);}
                else {net.minecraft.world.chunk.Chunk c=LoadedAccess.chunk(world,pos);tile=c==null?null:c.getTileEntityMap().get(pos);}
                return tile instanceof MachineTile?(MachineTile)tile:null;
            }
            public Object getServerGuiElement(int id,EntityPlayer player,World world,int x,int y,int z) {
                MachineTile tile=tile(id,world,x,y,z);return tile!=null && tile.isUsableByPlayer(player)?new MachineContainer(player.inventory,tile):null;
            }
            public Object getClientGuiElement(int id,EntityPlayer player,World world,int x,int y,int z) {
                MachineTile tile=tile(id,world,x,y,z);return tile==null?null:proxy.screen(player,tile);
            }
        });
    }
}
