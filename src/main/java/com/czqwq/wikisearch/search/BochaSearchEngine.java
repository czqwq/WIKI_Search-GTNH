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
 * Searches via the <a href="https://open.bochaai.com/">BoCha Search API</a>.
 * Requires {@code searchProvider=bocha} and {@code searchApiKey} in config.
 */
public class BochaSearchEngine implements SearchEngine {

    private static final String API_URL = "https://api.bochaai.com/v1/web-search";
    private static final int TIMEOUT_MS = 10000;

    @Override
    public String getName() {
        return "BoCha";
    }

    @Override
    public boolean isAvailable() {
        return "bocha".equals(Config.searchProvider) && Config.searchApiKey != null && !Config.searchApiKey.isEmpty();
    }

    @Override
    public List<SearchResult> search(String keyword) throws Exception {
        String json = "{\"query\":\"site:" + Config.wikiSearchDomain + " " + escapeJson(keyword) + "\",\"count\":8}";

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
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setRequestProperty("Accept-Encoding", "gzip, deflate"); // avoid brotli issues

        try (OutputStreamWriter w = new OutputStreamWriter(conn.getOutputStream(), StandardCharsets.UTF_8)) {
            w.write(json);
            w.flush();
        }

        int code = conn.getResponseCode();
        if (code != 200) {
            throw new RuntimeException("BoCha HTTP " + code);
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
            JsonObject data = root.getAsJsonObject("data");
            if (data == null) return out;
            JsonObject wp = data.getAsJsonObject("webPages");
            if (wp == null) return out;
            JsonArray rows = wp.getAsJsonArray("value");
            if (rows == null) return out;
            for (int i = 0; i < rows.size(); i++) {
                JsonObject item = rows.get(i)
                    .getAsJsonObject();
                String title = item.has("name") ? item.get("name")
                    .getAsString() : "";
                String url = item.has("url") ? item.get("url")
                    .getAsString() : "";
                if (url.isEmpty() || !url.contains(Config.wikiSearchDomain)) continue;
                out.add(new SearchResult(DuckDuckGoSearchEngine.stripSuffix(title), url));
            }
        } catch (Exception e) {
            GTNHWikiSearch.LOGGER.debug("[WikiSearch] BoCha parse error", e);
        }
        return out;
    }

    private static String escapeJson(String s) {
        return s.replace("\\", "\\\\")
            .replace("\"", "\\\"");
    }
}
