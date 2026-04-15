package com.czqwq.wikisearch;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLSocketFactory;

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

        // Apply Chrome-like cipher suite ordering on HTTPS connections.
        // Cloudflare uses the JA3 TLS fingerprint to detect non-browser clients;
        // reordering cipher suites makes our ClientHello much closer to Chrome 124.
        if (conn instanceof HttpsURLConnection https) {
            https.setSSLSocketFactory(new ChromeLikeSSLSocketFactory((SSLSocketFactory) SSLSocketFactory.getDefault()));
        }

        conn.setConnectTimeout(TIMEOUT_MS);
        conn.setReadTimeout(TIMEOUT_MS);
        conn.setInstanceFollowRedirects(true);
        // --- Headers that match a real Chrome 124 browser request ---
        conn.setRequestProperty(
            "User-Agent",
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) "
                + "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36");
        conn.setRequestProperty("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8");
        conn.setRequestProperty("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8");
        conn.setRequestProperty("Connection", "keep-alive");
        conn.setRequestProperty("Upgrade-Insecure-Requests", "1");
        conn.setRequestProperty(
            "sec-ch-ua",
            "\"Chromium\";v=\"124\", \"Google Chrome\";v=\"124\", \"Not-A.Brand\";v=\"99\"");
        conn.setRequestProperty("sec-ch-ua-mobile", "?0");
        conn.setRequestProperty("sec-ch-ua-platform", "\"Windows\"");
        conn.setRequestProperty("Sec-Fetch-Site", "none");
        conn.setRequestProperty("Sec-Fetch-Mode", "navigate");
        conn.setRequestProperty("Sec-Fetch-User", "?1");
        conn.setRequestProperty("Sec-Fetch-Dest", "document");
        if (cookie != null && !cookie.isEmpty()) {
            conn.setRequestProperty("Cookie", cookie);
        }

        int code = conn.getResponseCode();
        if (code != 200) {
            // Collect diagnostic info for a detailed error message
            StringBuilder diag = new StringBuilder();
            diag.append("URL=")
                .append(apiUrl);
            diag.append(", Cookie=")
                .append(cookie != null && !cookie.isEmpty() ? "set(len=" + cookie.length() + ")" : "not set");
            // Log Cloudflare diagnostic headers if present
            String cfRay = conn.getHeaderField("cf-ray");
            String cfMitigated = conn.getHeaderField("cf-mitigated");
            String server = conn.getHeaderField("server");
            if (cfRay != null) diag.append(", cf-ray=")
                .append(cfRay);
            if (cfMitigated != null) diag.append(", cf-mitigated=")
                .append(cfMitigated);
            if (server != null) diag.append(", server=")
                .append(server);
            // Try to read the error response body (first 512 chars)
            try (BufferedReader errReader = new BufferedReader(
                new InputStreamReader(conn.getErrorStream(), StandardCharsets.UTF_8))) {
                StringBuilder body = new StringBuilder();
                String l;
                while ((l = errReader.readLine()) != null && body.length() < 512) {
                    body.append(l);
                }
                if (body.length() > 0) {
                    diag.append(", errorBody=")
                        .append(body.substring(0, Math.min(body.length(), 256)));
                }
            } catch (Exception ignored) {}
            throw new RuntimeException("HTTP " + code + " [" + diag + "]");
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

    /**
     * Test connectivity to the configured ping host and send the result to chat.
     * Must be called from a background thread.
     */
    public static void pingAndDisplay(String host) {
        sendChat(
            new ChatComponentText(
                EnumChatFormatting.GOLD + "[WikiSearch] "
                    + EnumChatFormatting.RESET
                    + "正在 ping "
                    + EnumChatFormatting.YELLOW
                    + host
                    + EnumChatFormatting.RESET
                    + " ..."));
        try {
            long start = System.currentTimeMillis();
            InetAddress addr = InetAddress.getByName(host);
            boolean reachable = addr.isReachable(TIMEOUT_MS);
            long elapsed = System.currentTimeMillis() - start;
            if (reachable) {
                sendChat(
                    new ChatComponentText(
                        EnumChatFormatting.GOLD + "[WikiSearch] "
                            + EnumChatFormatting.GREEN
                            + "✔ "
                            + host
                            + " ("
                            + addr.getHostAddress()
                            + ") 可达，耗时 "
                            + elapsed
                            + " ms"));
            } else {
                // isReachable may return false when ICMP is blocked; try a TCP connection to port 80
                try (java.net.Socket s = new java.net.Socket()) {
                    s.connect(new java.net.InetSocketAddress(addr, 80), TIMEOUT_MS);
                    elapsed = System.currentTimeMillis() - start;
                    sendChat(
                        new ChatComponentText(
                            EnumChatFormatting.GOLD + "[WikiSearch] "
                                + EnumChatFormatting.GREEN
                                + "✔ "
                                + host
                                + " ("
                                + addr.getHostAddress()
                                + ") TCP:80 可达，耗时 "
                                + elapsed
                                + " ms"));
                } catch (Exception tcpEx) {
                    sendChat(
                        new ChatComponentText(
                            EnumChatFormatting.GOLD + "[WikiSearch] "
                                + EnumChatFormatting.RED
                                + "✘ "
                                + host
                                + " ("
                                + addr.getHostAddress()
                                + ") 不可达: "
                                + tcpEx.getMessage()));
                }
            }
        } catch (Exception e) {
            GTNHWikiSearch.LOGGER.error("WikiSearch ping failed for host={}", host, e);
            sendChat(
                new ChatComponentText(
                    EnumChatFormatting.GOLD + "[WikiSearch] "
                        + EnumChatFormatting.RED
                        + "✘ ping "
                        + host
                        + " 失败: "
                        + e.getMessage()));
        }
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
