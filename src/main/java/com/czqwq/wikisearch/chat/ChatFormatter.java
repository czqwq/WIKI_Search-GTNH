package com.czqwq.wikisearch.chat;

import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.event.ClickEvent;
import net.minecraft.event.HoverEvent;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.ChatStyle;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.IChatComponent;
import net.minecraft.util.StatCollector;

import com.czqwq.wikisearch.Config;
import com.czqwq.wikisearch.search.SearchResult;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

@SideOnly(Side.CLIENT)
public final class ChatFormatter {

    private ChatFormatter() {}

    private static String t(String k) {
        return StatCollector.translateToLocal("wikisearch." + k);
    }

    private static String tf(String k, Object... a) {
        return String.format(t(k), a);
    }

    private static final String PREFIX = EnumChatFormatting.GOLD + "[WikiSearch] " + EnumChatFormatting.RESET;

    // -- search results --

    public static void displayResults(String itemName, List<SearchResult> results) {
        if (results.isEmpty()) {
            send(PREFIX + EnumChatFormatting.RED + tf("search.no_results", itemName));
            return;
        }
        send(PREFIX + tf("search.header", itemName, results.size()));
        int i = 1;
        for (SearchResult r : results) send(formatLine(i++, r));
    }

    public static void displaySearchError(String itemName, Exception error) {
        String reason = error.getMessage() != null ? error.getMessage() : t("search.unknown_error");
        String tmpl = reason.contains("HTTP 403") ? "search.error_403" : "search.error_generic";
        send(PREFIX + EnumChatFormatting.RED + tf(tmpl, reason));
    }

    /** All engines exhausted — suggest the player configure a provider. */
    public static void displayAllEnginesFailed() {
        send(PREFIX + EnumChatFormatting.RED + t("search.all_engines_failed"));
        send(PREFIX + EnumChatFormatting.GRAY + t("search.configure_fallback_hint"));
    }

    // -- ping --

    public static void displayPingStart(String host) {
        send(PREFIX + tf("ping.start", host));
    }

    public static void displayPingSuccess(String host, String ip, long ms) {
        send(PREFIX + EnumChatFormatting.GREEN + tf("ping.success", host, ip, ms));
    }

    public static void displayPingSuccessTcp(String host, String ip, long ms) {
        send(PREFIX + EnumChatFormatting.GREEN + tf("ping.success_tcp", host, ip, ms));
    }

    public static void displayPingFailure(String host, String ip, String detail) {
        send(PREFIX + EnumChatFormatting.RED + tf("ping.failure", host, ip, detail));
    }

    public static void displayPingError(String host) {
        send(PREFIX + EnumChatFormatting.RED + tf("ping.error", host));
    }

    // -- auth --

    public static void displayAuthPortsExhausted() {
        send(PREFIX + EnumChatFormatting.RED + tf("auth.ports_exhausted", Config.PORT_START, Config.PORT_END));
    }

    public static void displayAuthStarted(String url) {
        send(PREFIX + EnumChatFormatting.GREEN + tf("auth.server_started", url));
    }

    public static void displayAuthBrowserFailed(String url) {
        send(PREFIX + EnumChatFormatting.RED + tf("auth.browser_failed", url));
    }

    public static void displayAuthSuccess() {
        send(PREFIX + EnumChatFormatting.GREEN + t("auth.success"));
    }

    // -- command --

    public static void displayConfigReloaded() {
        send(PREFIX + EnumChatFormatting.GREEN + t("command.config_reloaded"));
    }

    public static void displayCookieSaved() {
        send(PREFIX + EnumChatFormatting.GREEN + t("command.cookie_saved"));
    }

    public static void displayUsage(String usage) {
        send(EnumChatFormatting.RED + t("command.usage_prefix") + " " + EnumChatFormatting.YELLOW + usage);
    }

    // -- internal --

    private static IChatComponent formatLine(int idx, SearchResult r) {
        IChatComponent line = new ChatComponentText(
            EnumChatFormatting.GRAY.toString() + idx + ". " + EnumChatFormatting.GREEN + r.title() + " ");
        IChatComponent btn = new ChatComponentText(EnumChatFormatting.AQUA + t("search.open_button"));
        btn.setChatStyle(
            new ChatStyle().setChatClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, r.url()))
                .setChatHoverEvent(
                    new HoverEvent(
                        HoverEvent.Action.SHOW_TEXT,
                        new ChatComponentText(t("search.open_hover") + " " + r.url()))));
        line.appendSibling(btn);
        return line;
    }

    private static void send(IChatComponent msg) {
        EntityPlayer p = Minecraft.getMinecraft().thePlayer;
        if (p != null) p.addChatMessage(msg);
    }

    private static void send(String text) {
        send(new ChatComponentText(text));
    }
}
