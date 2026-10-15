package dev.agentic.workshop;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.command.*;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Biomes;
import net.minecraft.init.Blocks;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.world.WorldServer;
public final class WorldSiteCommand extends CommandBase {
    @Override public String getName() {return "workshopsite";}
    @Override public String getUsage(ICommandSender sender) {return "/workshopsite";}
    @Override public int getRequiredPermissionLevel() {return 2;}
    @Override public void execute(MinecraftServer server,ICommandSender sender,String[] args) throws CommandException {
        if(args.length!=0)throw new WrongUsageException(getUsage(sender));
        EntityPlayerMP player=getCommandSenderAsPlayer(sender);WorldServer world=player.getServerWorld();
        if(world.provider.getDimension()!=0)throw new CommandException("Use this command in the Overworld.");
        int gridX=Math.floorDiv(player.getPosition().getX(),128),gridZ=Math.floorDiv(player.getPosition().getZ(),128);
        List<BlockPos> candidates=new ArrayList<>();
        for(int x=-8;x<=8;x++)for(int z=-8;z<=8;z++) {
            BlockPos pos=new BlockPos((gridX+x)*128+8,0,(gridZ+z)*128+8);
            if(world.getBiomeProvider().getBiome(pos)==Biomes.PLAINS)candidates.add(pos);
        }
        candidates.sort(Comparator.comparingDouble(pos->pos.distanceSq(player.posX,0,player.posZ)));
        // Explicit operator request: at most4 candidate groups, not a background chunk loader.
        for(int i=0;i<Math.min(4,candidates.size());i++) {
            BlockPos pos=candidates.get(i);int cx=pos.getX()>>4,cz=pos.getZ()>>4;
            world.getChunk(cx,cz);world.getChunk(cx+1,cz);world.getChunk(cx,cz+1);world.getChunk(cx+1,cz+1);
            for(int y=4;y<=220;y++) {
                BlockPos base=new BlockPos(pos.getX(),y,pos.getZ());
                if(world.getBlockState(base.up(2)).getBlock()==Blocks.GOLD_BLOCK && world.getBlockState(base.east()).getBlock()==Blocks.CHEST) {
                    sender.sendMessage(new TextComponentString("Test site at "+base.getX()+", "+y+", "+base.getZ()+". Teleport above it: /tp "+player.getName()+" "+base.getX()+" "+(y+4)+" "+base.getZ()));
                    BlockPos ore=findOre(world,cx,cz);
                    sender.sendMessage(new TextComponentString(ore==null?"No Test Ore remains in this site chunk.":"Test Ore at "+ore.getX()+", "+ore.getY()+", "+ore.getZ()+" (underground)."));return;
                }
            }
        }
        throw new CommandException("No test site found nearby. Try a fresh default world or move into new Plains terrain.");
    }
    public static BlockPos findOre(WorldServer world,int cx,int cz) {
        for(int x=0;x<16;x++)for(int z=0;z<16;z++)for(int y=4;y<=36;y++) {
            BlockPos pos=new BlockPos(cx*16+x,y,cz*16+z);
            if(world.getBlockState(pos).getBlock()==Content.ORE)return pos;
        }
        return null;
    }
}
