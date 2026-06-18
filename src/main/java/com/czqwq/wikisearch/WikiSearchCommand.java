package com.czqwq.wikisearch;

import java.util.Arrays;
import java.util.List;

import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;

import com.czqwq.wikisearch.chat.ChatFormatter;

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
        return "/wikisearch <cookie <value>|ping [host]|reload>";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 0;
    }

    @Override
    public void processCommand(ICommandSender sender, String[] args) throws CommandException {
        // -- /wikisearch auth — 暂时禁用，未来重新设计认证流程时再开启 --
        // if (args.length >= 1 && args[0].equalsIgnoreCase("auth")) { doAuth(sender); return; }

        if (args.length >= 1 && args[0].equalsIgnoreCase("reload")) {
            Config.reload();
            ChatFormatter.displayConfigReloaded();
            return;
        }

        if (args.length >= 1 && args[0].equalsIgnoreCase("ping")) {
            String host = args.length >= 2 ? args[1] : Config.pingHost;
            if (host == null || host.isEmpty()) host = Config.DEFAULT_PING_HOST;
            WikiSearchFetcher.pingAndDisplay(host);
            return;
        }

        if (args.length < 2 || !args[0].equalsIgnoreCase("cookie")) {
            ChatFormatter.displayUsage(getCommandUsage(sender));
            return;
        }

        String rawCookie = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
        Config.setCookie(rawCookie);
        ChatFormatter.displayCookieSaved();
        GTNHWikiSearch.LOGGER.info("WikiSearch cookie updated (length={})", Config.cookie.length());
    }

    // private static void doAuth(ICommandSender sender) { ... } // 暂时禁用

    @Override
    @SuppressWarnings("rawtypes")
    public List addTabCompletionOptions(ICommandSender sender, String[] args) {
        if (args.length == 1) return getListOfStringsMatchingLastWord(args, "cookie", "ping", "reload");
        return null;
    }
}
