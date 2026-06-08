package com.czqwq.wikisearch;

import java.awt.Desktop;
import java.net.URI;
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
        return "/wikisearch <auth|cookie <cookie>|reload|ping [host]>";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 0;
    }

    @Override
    public void processCommand(ICommandSender sender, String[] args) throws CommandException {
        if (args.length >= 1 && args[0].equalsIgnoreCase("auth")) {
            LocalAuthServer server = LocalAuthServer.startNew();
            if (server == null) {
                sender.addChatMessage(
                    new ChatComponentText(
                        EnumChatFormatting.GOLD + "[WikiSearch] "
                            + EnumChatFormatting.RED
                            + "无法启动本地认证服务器（端口"
                            + Config.PORT_START
                            + " - "
                            + Config.PORT_END
                            + "均被占用）。"));
                return;
            }
            String url = "http://localhost:" + server.getPort() + "/";
            sender.addChatMessage(
                new ChatComponentText(
                    EnumChatFormatting.GOLD + "[WikiSearch] "
                        + EnumChatFormatting.GREEN
                        + "正在打开浏览器认证助手... "
                        + EnumChatFormatting.YELLOW
                        + url));
            try {
                if (Desktop.isDesktopSupported() && Desktop.getDesktop()
                    .isSupported(Desktop.Action.BROWSE)) {
                    Desktop.getDesktop()
                        .browse(new URI(url));
                } else {
                    // Fallback: try xdg-open on Linux
                    Runtime.getRuntime()
                        .exec(new String[] { "xdg-open", url });
                }
            } catch (Exception e) {
                GTNHWikiSearch.LOGGER.debug("[WikiSearch] Failed to open browser", e);
                sender.addChatMessage(
                    new ChatComponentText(
                        EnumChatFormatting.GOLD + "[WikiSearch] "
                            + EnumChatFormatting.RED
                            + "无法自动打开浏览器，请手动访问: "
                            + EnumChatFormatting.YELLOW
                            + url));
            }
            return;
        }

        if (args.length >= 1 && args[0].equalsIgnoreCase("reload")) {
            Config.reload();
            sender.addChatMessage(
                new ChatComponentText(
                    EnumChatFormatting.GOLD + "[WikiSearch] " + EnumChatFormatting.GREEN + "配置已从本地文件重新加载。"));
            return;
        }

        if (args.length >= 1 && args[0].equalsIgnoreCase("ping")) {
            String host = args.length >= 2 ? args[1] : Config.pingHost;
            if (host == null || host.isEmpty()) host = Config.DEFAULT_PING_HOST;
            final String targetHost = host;
            Thread t = new Thread(() -> WikiSearchFetcher.pingAndDisplay(targetHost), "WikiSearch-ping");
            t.setDaemon(true);
            t.start();
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
            return getListOfStringsMatchingLastWord(args, "auth", "cookie", "reload", "ping");
        }
        return null;
    }
}
