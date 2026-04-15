package com.czqwq.wikisearch;

import java.util.Arrays;
import java.util.List;

import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.EnumChatFormatting;

public class WikiSearchCommand extends CommandBase {

    @Override
    public String getCommandName() {
        return "wikisearch";
    }

    @Override
    public String getCommandUsage(ICommandSender sender) {
        return "/wikisearch cookie <cookie>";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 2; // OP
    }

    @Override
    public void processCommand(ICommandSender sender, String[] args) throws CommandException {
        if (args.length < 2 || !args[0].equalsIgnoreCase("cookie")) {
            sender.addChatMessage(
                new ChatComponentText(
                    EnumChatFormatting.RED + "用法: " + EnumChatFormatting.YELLOW + getCommandUsage(sender)));
            return;
        }

        // Join remaining args in case cookie value contains spaces
        String rawCookie = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
        Config.setCookie(rawCookie);

        sender.addChatMessage(
            new ChatComponentText(
                EnumChatFormatting.GOLD + "[WikiSearch] " + EnumChatFormatting.GREEN + "Cookie已设置并保存到配置文件。"));

        wikisearch.LOG.info(
            "WikiSearch cookie updated by " + sender.getCommandSenderName() + " (length=" + Config.cookie.length()
                + ")");
    }

    @Override
    @SuppressWarnings("rawtypes")
    public List addTabCompletionOptions(ICommandSender sender, String[] args) {
        if (args.length == 1) {
            return getListOfStringsMatchingLastWord(args, "cookie");
        }
        return null;
    }
}
