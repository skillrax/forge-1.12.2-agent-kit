package com.example.rubymod;

import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.WrongUsageException;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.text.TextComponentString;

/** /rubies <player> [count]. Registered from the main class's FMLServerStartingEvent handler. */
public class RubyCommand extends CommandBase {
    @Override public String getName() { return "rubies"; }
    @Override public String getUsage(ICommandSender sender) { return "/rubies <player> [count]"; }
    /** 2 = operators / cheats enabled. CommandBase defaults to 4 (console only). */
    @Override public int getRequiredPermissionLevel() { return 2; }

    @Override
    public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
        if (args.length < 1) throw new WrongUsageException(getUsage(sender));
        EntityPlayerMP target = getPlayer(server, sender, args[0]);
        int count = args.length > 1 ? parseInt(args[1], 1, 64) : 1;
        target.inventory.addItemStackToInventory(new ItemStack(ModItems.RUBY, count));
        sender.sendMessage(new TextComponentString("Gave " + count + " rubies to " + target.getName()));
    }
}
