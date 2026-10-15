package com.example.machinestarter;

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

@Mod(modid=MachineStarter.ID,name="Machine Starter",version="1.0.0",acceptedMinecraftVersions="[1.12.2]")
@Mod.EventBusSubscriber(modid=MachineStarter.ID)
public final class MachineStarter {
    public static final String ID="machinestarter";
    public static final MachineBlock MACHINE=new MachineBlock();
    @Mod.Instance(ID) public static MachineStarter instance;
    @SidedProxy(clientSide="com.example.machinestarter.ClientHooks",serverSide="com.example.machinestarter.MachineStarter$CommonProxy")
    public static CommonProxy proxy;
    public static class CommonProxy {
        public Object screen(EntityPlayer player,MachineTile tile) {return null;}
    }
    @SubscribeEvent public static void blocks(RegistryEvent.Register<Block> event) {
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
                if(id!=0 || !world.isBlockLoaded(pos))return null;
                TileEntity tile=world.getTileEntity(pos);return tile instanceof MachineTile?(MachineTile)tile:null;
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
