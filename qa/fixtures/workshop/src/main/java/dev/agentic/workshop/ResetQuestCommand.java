package dev.agentic.workshop;

import java.util.Collections;
import java.util.List;
import net.minecraft.command.*;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentString;

/** Test convenience: explicit operator-only reset, never a general player-data wipe. */
public final class ResetQuestCommand extends CommandBase {
    @Override public String getName() {return "workshopreset";}
    @Override public String getUsage(ICommandSender sender) {return "/workshopreset [online-player]";}
    @Override public int getRequiredPermissionLevel() {return 2;}
    @Override public void execute(MinecraftServer server,ICommandSender sender,String[] args) throws CommandException {
        if(args.length>1)throw new WrongUsageException(getUsage(sender));
        EntityPlayerMP target=args.length==0?getCommandSenderAsPlayer(sender):getPlayer(server,sender,args[0]);
        PlayerQuest.reset(target);
        TrialAdvancement.reset(target);
        TextComponentString message=new TextComponentString("Test Quest and harvest advancement reset for "+target.getName()+". Inventory unchanged; quest rewards can be earned again.");
        sender.sendMessage(message);if(sender!=target)target.sendMessage(message);
    }
    @Override public List<String> getTabCompletions(MinecraftServer server,ICommandSender sender,String[] args,BlockPos pos) {
        return args.length==1?getListOfStringsMatchingLastWord(args,server.getOnlinePlayerNames()):Collections.emptyList();
    }
}
