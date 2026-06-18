package com.czqwq.wikisearch.search;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import javax.net.ssl.HttpsURLConnection;

import com.czqwq.wikisearch.Config;
import com.czqwq.wikisearch.GTNHWikiSearch;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * Searches via the <a href="https://cloud.baidu.com/">Baidu AI Search API</a>
 * (Qianfan AppBuilder). Matches AstrBot's {@code web_search_baidu} tool.
 * <p>
 * Requires {@code searchProvider=baidu} and {@code searchApiKey} in config.
 */
public class BaiduSearchEngine implements SearchEngine {

    private static final String API_URL = "https://qianfan.baidubce.com/v2/ai_search/web_search";
    private static final int TIMEOUT_MS = 10000;

    @Override
    public String getName() {
        return "Baidu AI Search";
    }

    @Override
    public boolean isAvailable() {
        return "baidu".equals(Config.searchProvider) && Config.searchApiKey != null && !Config.searchApiKey.isEmpty();
    }

    @Override
    public List<SearchResult> search(String keyword) throws Exception {
        // Build payload matching AstrBot's _baidu_search format
        String body = "{\"messages\":[{\"role\":\"user\",\"content\":\"" + escapeJson(keyword)
            + "\"}],\"search_source\":\"baidu_search_v2\","
            + "\"resource_type_filter\":[{\"type\":\"web\",\"top_k\":8}],"
            + "\"search_filter\":{\"match\":{\"site\":[\""
            + escapeJson(Config.wikiSearchDomain)
            + "\"]}}}";

        URL url = new URL(API_URL);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        if (conn instanceof HttpsURLConnection) {
            ((HttpsURLConnection) conn)
                .setSSLSocketFactory(com.czqwq.wikisearch.ChromeLikeSSLSocketFactory.createDefault());
        }
        conn.setConnectTimeout(TIMEOUT_MS);
        conn.setReadTimeout(TIMEOUT_MS);
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);
        conn.setRequestProperty("Authorization", "Bearer " + Config.searchApiKey);
        conn.setRequestProperty("X-Appbuilder-Authorization", "Bearer " + Config.searchApiKey);
        conn.setRequestProperty("Content-Type", "application/json");

        try (OutputStreamWriter w = new OutputStreamWriter(conn.getOutputStream(), StandardCharsets.UTF_8)) {
            w.write(body);
            w.flush();
        }

        int code = conn.getResponseCode();
        if (code != 200) {
            throw new RuntimeException("Baidu AI Search HTTP " + code);
        }

        StringBuilder sb = new StringBuilder();
        try (BufferedReader r = new BufferedReader(
            new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = r.readLine()) != null) sb.append(line);
        }

        return parseResults(sb.toString());
    }

    private List<SearchResult> parseResults(String json) {
        List<SearchResult> out = new ArrayList<>();
        try {
            JsonObject root = new JsonParser().parse(json)
                .getAsJsonObject();
            JsonArray refs = root.getAsJsonArray("references");
            if (refs == null) return out;
            for (int i = 0; i < refs.size(); i++) {
                JsonObject item = refs.get(i)
                    .getAsJsonObject();
                String title = item.has("title") ? item.get("title")
                    .getAsString() : "";
                String url = item.has("url") ? item.get("url")
                    .getAsString() : "";
                if (url.isEmpty() || !url.contains(Config.wikiSearchDomain)) continue;
                out.add(new SearchResult(DuckDuckGoSearchEngine.stripSuffix(title), url));
            }
        } catch (Exception e) {
            GTNHWikiSearch.LOGGER.debug("[WikiSearch] Baidu AI parse error", e);
        }
        return out;
    }

    private static String escapeJson(String s) {
        return s.replace("\\", "\\\\")
            .replace("\"", "\\\"");
    }
}
