package com.czqwq.wikisearch.search;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLSocketFactory;

import com.czqwq.wikisearch.Config;
import com.czqwq.wikisearch.GTNHWikiSearch;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * Searches via the <a href="https://api.search.brave.com/">Brave Search API</a>.
 * <p>
 * Brave offers a free tier (2 000 queries/month) and supports {@code site:}
 * filtering natively. An API key must be configured in {@link Config#braveApiKey}.
 * <p>
 * API docs: {@code https://api.search.brave.com/res/v1/web/search}
 */
public class BraveSearchEngine implements SearchEngine {

    private static final String BRAVE_API_URL = "https://api.search.brave.com/res/v1/web/search";
    private static final int TIMEOUT_MS = 10000;
    private static final SSLSocketFactory CHROME_SSL = com.czqwq.wikisearch.ChromeLikeSSLSocketFactory.createDefault();

    @Override
    public String getName() {
        return "Brave Search";
    }

    @Override
    public boolean isAvailable() {
        return "brave".equals(Config.searchProvider) && Config.searchApiKey != null
            && !Config.searchApiKey.isEmpty()
            && Config.wikiSearchDomain != null
            && !Config.wikiSearchDomain.isEmpty();
    }

    @Override
    public List<SearchResult> search(String keyword) throws Exception {
        // Build query with site: filter
        String q = "site:" + Config.wikiSearchDomain + " " + keyword;
        String params = "q=" + URLEncoder.encode(q, StandardCharsets.UTF_8.name())
            + "&count=8"
            + "&search_lang=zh-hans";

        URL url = new URL(BRAVE_API_URL + "?" + params);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        if (conn instanceof HttpsURLConnection) {
            ((HttpsURLConnection) conn).setSSLSocketFactory(CHROME_SSL);
        }
        conn.setConnectTimeout(TIMEOUT_MS);
        conn.setReadTimeout(TIMEOUT_MS);
        conn.setRequestMethod("GET");
        conn.setRequestProperty("Accept", "application/json");
        conn.setRequestProperty("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8");
        conn.setRequestProperty("X-Subscription-Token", Config.searchApiKey);

        int code = conn.getResponseCode();
        if (code != 200) {
            String body = readError(conn);
            GTNHWikiSearch.LOGGER.debug("[WikiSearch] Brave Search HTTP {}: {}", code, body);
            throw new RuntimeException("Brave Search HTTP " + code + (body != null ? ": " + body : ""));
        }

        String json = readBody(conn);
        return parseResults(json);
    }

    private List<SearchResult> parseResults(String json) {
        List<SearchResult> out = new ArrayList<>();
        try {
            JsonObject root = new JsonParser().parse(json)
                .getAsJsonObject();
            JsonObject web = root.getAsJsonObject("web");
            if (web == null) return out;
            JsonArray results = web.getAsJsonArray("results");
            if (results == null) return out;
            for (JsonElement el : results) {
                JsonObject item = el.getAsJsonObject();
                String title = item.has("title") ? item.get("title")
                    .getAsString() : "";
                String url = item.has("url") ? item.get("url")
                    .getAsString() : "";
                if (url.isEmpty() || !url.contains(Config.wikiSearchDomain)) continue;
                out.add(new SearchResult(DuckDuckGoSearchEngine.stripSuffix(title), url));
            }
        } catch (Exception e) {
            GTNHWikiSearch.LOGGER.debug("[WikiSearch] Brave Search parse error", e);
        }
        return out;
    }

    private static String readBody(HttpURLConnection conn) throws Exception {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader r = new BufferedReader(
            new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = r.readLine()) != null) sb.append(line);
        }
        return sb.toString();
    }

    private static String readError(HttpURLConnection conn) {
        try {
            StringBuilder sb = new StringBuilder();
            try (BufferedReader r = new BufferedReader(
                new InputStreamReader(conn.getErrorStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = r.readLine()) != null) sb.append(line);
            }
            return sb.length() > 0 ? sb.substring(0, Math.min(sb.length(), 200)) : null;
        } catch (Exception e) {
            return null;
        }
    }
}
