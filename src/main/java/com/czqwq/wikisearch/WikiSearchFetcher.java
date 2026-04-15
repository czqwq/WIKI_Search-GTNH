package com.czqwq.wikisearch;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.event.ClickEvent;
import net.minecraft.event.HoverEvent;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.ChatStyle;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.IChatComponent;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

@SideOnly(Side.CLIENT)
public class WikiSearchFetcher {

    private static final int TIMEOUT_MS = 15000;

    public static class SearchResult {

        public final String title;
        public final String url;

        public SearchResult(String title, String url) {
            this.title = title;
            this.url = url;
        }
    }

    /**
     * Perform search and send formatted results to the local player's chat.
     * Must be called from a background thread.
     */
    public static void fetchAndDisplay(String itemName) {
        List<SearchResult> results;
        try {
            results = fetchResults(itemName, Config.cookie);
        } catch (Exception e) {
            GTNHWikiSearch.LOGGER.error("WikiSearch HTTP request failed", e);
            sendChat(
                new ChatComponentText(
                    EnumChatFormatting.GOLD + "[WikiSearch] "
                        + EnumChatFormatting.RED
                        + "搜索请求失败: "
                        + e.getMessage()
                        + "。如遇403，请先用 "
                        + EnumChatFormatting.YELLOW
                        + "/wikisearch cookie <cookie>"
                        + EnumChatFormatting.RED
                        + " 设置Cookie。"));
            return;
        }

        if (results.isEmpty()) {
            sendChat(
                new ChatComponentText(
                    EnumChatFormatting.GOLD + "[WikiSearch] "
                        + EnumChatFormatting.RED
                        + "未找到 \""
                        + EnumChatFormatting.YELLOW
                        + itemName
                        + EnumChatFormatting.RED
                        + "\" 的相关页面。"));
            return;
        }

        sendChat(
            new ChatComponentText(
                EnumChatFormatting.GOLD + "[WikiSearch] "
                    + EnumChatFormatting.RESET
                    + "搜索 \""
                    + EnumChatFormatting.YELLOW
                    + itemName
                    + EnumChatFormatting.RESET
                    + "\" 的结果 ("
                    + results.size()
                    + " 项):"));

        for (int i = 0; i < results.size(); i++) {
            SearchResult result = results.get(i);

            IChatComponent line = new ChatComponentText(
                EnumChatFormatting.GRAY.toString() + (i + 1) + ". " + EnumChatFormatting.GREEN + result.title + " ");

            IChatComponent button = new ChatComponentText(EnumChatFormatting.AQUA + "[打开]");
            button.setChatStyle(
                new ChatStyle().setChatClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, result.url))
                    .setChatHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new ChatComponentText(result.url))));

            line.appendSibling(button);
            sendChat(line);
        }
    }

    /** Fetch search results from the MediaWiki API. */
    public static List<SearchResult> fetchResults(String itemName, String cookie) throws Exception {
        String encoded = URLEncoder.encode(itemName, StandardCharsets.UTF_8.name());
        String apiUrl = Config.searchApiUrl.replace("{item}", encoded);
        URL url = new URL(apiUrl);

        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setConnectTimeout(TIMEOUT_MS);
        conn.setReadTimeout(TIMEOUT_MS);
        conn.setRequestProperty(
            "User-Agent",
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) "
                + "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36");
        conn.setRequestProperty("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8");
        conn.setRequestProperty("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8");
        conn.setRequestProperty("Connection", "keep-alive");
        if (cookie != null && !cookie.isEmpty()) {
            conn.setRequestProperty("Cookie", cookie);
        }

        int code = conn.getResponseCode();
        if (code != 200) {
            throw new RuntimeException("HTTP " + code);
        }

        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
            new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
        }

        return parseResults(sb.toString());
    }

    private static List<SearchResult> parseResults(String json) {
        List<SearchResult> results = new ArrayList<>();
        try {
            JsonObject root = new JsonParser().parse(json)
                .getAsJsonObject();
            JsonObject query = root.getAsJsonObject("query");
            if (query == null) return results;
            JsonArray search = query.getAsJsonArray("search");
            if (search == null) return results;

            String base = Config.wikiPageBase;
            for (JsonElement element : search) {
                JsonObject obj = element.getAsJsonObject();
                String title = obj.get("title")
                    .getAsString();
                String pageUrl = base + "/wiki/"
                    + URLEncoder.encode(title, StandardCharsets.UTF_8.name())
                        .replace("+", "_");
                results.add(new SearchResult(title, pageUrl));
            }
        } catch (Exception e) {
            GTNHWikiSearch.LOGGER.error("WikiSearch failed to parse search results", e);
        }
        return results;
    }

    /** Add a chat message to the local player. Safe to call from a background thread in 1.7.10. */
    private static void sendChat(IChatComponent msg) {
        EntityPlayer player = Minecraft.getMinecraft().thePlayer;
        if (player != null) {
            player.addChatMessage(msg);
        }
    }
}
