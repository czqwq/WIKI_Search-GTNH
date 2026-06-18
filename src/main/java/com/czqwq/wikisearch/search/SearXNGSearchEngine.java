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

import com.czqwq.wikisearch.Config;
import com.czqwq.wikisearch.GTNHWikiSearch;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * Searches via any <a href="https://github.com/searxng/searxng">SearXNG</a> instance.
 * <p>
 * SearXNG is a self-hosted metasearch engine with a JSON API: {@code /search?q=...&format=json}.
 * No API key required. Many public instances exist. Configure {@code searxngUrl} in config.
 * <p>
 * Default: {@code https://search.bus-hit.me} (community public instance).
 */
public class SearXNGSearchEngine implements SearchEngine {

    private static final int TIMEOUT_MS = 10000;

    @Override
    public String getName() {
        return "SearXNG";
    }

    @Override
    public boolean isAvailable() {
        return Config.searxngUrl != null && !Config.searxngUrl.isEmpty()
            && Config.wikiSearchDomain != null
            && !Config.wikiSearchDomain.isEmpty();
    }

    @Override
    public List<SearchResult> search(String keyword) throws Exception {
        String q = "site:" + Config.wikiSearchDomain + " " + keyword;
        String url = Config.searxngUrl + "/search?q="
            + URLEncoder.encode(q, StandardCharsets.UTF_8.name())
            + "&format=json&language=zh-CN";

        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        if (conn instanceof HttpsURLConnection) {
            ((HttpsURLConnection) conn)
                .setSSLSocketFactory(com.czqwq.wikisearch.ChromeLikeSSLSocketFactory.createDefault());
        }
        conn.setConnectTimeout(TIMEOUT_MS);
        conn.setReadTimeout(TIMEOUT_MS);
        conn.setRequestProperty(
            "User-Agent",
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/124.0.0.0 Safari/537.36");
        conn.setRequestProperty("Accept", "application/json");

        int code = conn.getResponseCode();
        if (code != 200) {
            throw new RuntimeException("SearXNG HTTP " + code);
        }

        StringBuilder sb = new StringBuilder();
        try (BufferedReader r = new BufferedReader(
            new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = r.readLine()) != null) sb.append(line);
        }

        List<SearchResult> results = parseResults(sb.toString());
        GTNHWikiSearch.LOGGER.debug("[WikiSearch] SearXNG: results={}", results.size());
        return results;
    }

    private List<SearchResult> parseResults(String json) {
        List<SearchResult> out = new ArrayList<>();
        try {
            JsonObject root = new JsonParser().parse(json)
                .getAsJsonObject();
            JsonArray arr = root.getAsJsonArray("results");
            if (arr == null) return out;
            for (JsonElement el : arr) {
                JsonObject item = el.getAsJsonObject();
                String title = item.has("title") ? item.get("title")
                    .getAsString() : "";
                String url = item.has("url") ? item.get("url")
                    .getAsString() : "";
                if (url.isEmpty() || !url.contains(Config.wikiSearchDomain)) continue;
                out.add(new SearchResult(DuckDuckGoSearchEngine.stripSuffix(title), url));
            }
        } catch (Exception e) {
            GTNHWikiSearch.LOGGER.debug("[WikiSearch] SearXNG parse error", e);
        }
        return out;
    }
}
