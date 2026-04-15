package com.czqwq.wikisearch;

import java.util.Arrays;
import java.util.List;

import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.EnumChatFormatting;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

@SideOnly(Side.CLIENT)
public class WikiSearchCommand extends CommandBase {

    @Override
    public String getCommandName() {
        return "wikisearch";
    }

    @Override
    public String getCommandUsage(ICommandSender sender) {
        return "/wikisearch <cookie <cookie>|reload>";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 0;
    }

    @Override
    public void processCommand(ICommandSender sender, String[] args) throws CommandException {
        if (args.length >= 1 && args[0].equalsIgnoreCase("reload")) {
            Config.reload();
            sender.addChatMessage(
                new ChatComponentText(
                    EnumChatFormatting.GOLD + "[WikiSearch] " + EnumChatFormatting.GREEN + "配置已从本地文件重新加载。"));
            return;
        }

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
                EnumChatFormatting.GOLD + "[WikiSearch] " + EnumChatFormatting.GREEN + "Cookie已设置并保存到本地配置文件。"));

        wikisearch.LOG.info("WikiSearch cookie updated (length=" + Config.cookie.length() + ")");
    }

    @Override
    @SuppressWarnings("rawtypes")
    public List addTabCompletionOptions(ICommandSender sender, String[] args) {
        if (args.length == 1) {
            return getListOfStringsMatchingLastWord(args, "cookie", "reload");
        }
        return null;
    }
}
