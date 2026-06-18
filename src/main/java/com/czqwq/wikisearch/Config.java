package com.czqwq.wikisearch;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import net.minecraftforge.common.config.Configuration;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;

public class Config {

    private static Configuration config;

    // ── Runtime values ──────────────────────────────────────────────────────
    // public static String searchApiUrl = ""; // 暂时禁用 — 已改用搜索引擎
    // public static String wikiPageBase = ""; // 暂时禁用
    // public static String userAgent = ""; // 暂时禁用
    public static String cookie = "";
    public static String pingHost = "";
    public static String wikiSearchDomain = "";

    /**
     * Search provider. Supported values (matching AstrBot):
     * <ul>
     * <li>{@code duckduckgo} — free HTML scrape</li>
     * <li>{@code bing} — free HTML scrape</li>
     * <li>{@code brave} — API, free 2000/month</li>
     * <li>{@code tavily} — API</li>
     * <li>{@code bocha} — API</li>
     * <li>{@code baidu} — Baidu AI Search (Qianfan)</li>
     * </ul>
     */
    public static String searchProvider = "";
    /** API key for provider that requires one (brave / tavily / bocha / baidu). */
    public static String searchApiKey = "";

    /** Parsed list of title suffixes to strip (never null). */
    public static List<String> titleStripSuffixes = Collections.emptyList();
    public static int PORT_START;
    public static int PORT_END;

    // ── Defaults ────────────────────────────────────────────────────────────
    // static final String DEFAULT_SEARCH_API_URL = ...; // 暂时禁用
    // static final String DEFAULT_WIKI_PAGE_BASE = ...; // 暂时禁用
    // static final String DEFAULT_USER_AGENT = ...; // 暂时禁用
    static final String DEFAULT_WIKI_SEARCH_DOMAIN = "gtnh.huijiwiki.com";
    static final String DEFAULT_TITLE_STRIP_SUFFIXES = "[\" - GTNH 中文维基 - 灰机wiki - 北京嘉闻杰诺网络科技有限公司\"]";
    static final String DEFAULT_PING_HOST = "baidu.com";

    public static void init(File configFile) {
        config = new Configuration(configFile);
        load();
    }

    private static void load() {
        PORT_START = config.getInt(
            "LocalServerStartPort",
            Configuration.CATEGORY_GENERAL,
            10000,
            0,
            65535,
            "start port for local server to listen on");
        PORT_END = config.getInt(
            "LocalServerEndPort",
            Configuration.CATEGORY_GENERAL,
            25590,
            0,
            65535,
            "end port for local server to listen on");
        cookie = config.getString(
            "cookie",
            Configuration.CATEGORY_GENERAL,
            "",
            "Cookie string for wiki access (set via /wikisearch cookie <value>)");
        // searchApiUrl = config.getString(...); // 暂时禁用
        // wikiPageBase = config.getString(...); // 暂时禁用
        // userAgent = config.getString(...); // 暂时禁用
        pingHost = config.getString(
            "pingHost",
            Configuration.CATEGORY_GENERAL,
            DEFAULT_PING_HOST,
            "Hostname used by /wikisearch ping to test network connectivity.");
        wikiSearchDomain = config.getString(
            "wikiSearchDomain",
            Configuration.CATEGORY_GENERAL,
            DEFAULT_WIKI_SEARCH_DOMAIN,
            "Wiki domain for site-scoped queries.");

        // ── Provider-based search configuration ────────────────────────────
        searchProvider = config.getString(
            "searchProvider",
            Configuration.CATEGORY_GENERAL,
            "duckduckgo",
            "Search provider. One of: duckduckgo, bing, brave, tavily, bocha, baidu.\n"
                + "duckduckgo / bing : free, no API key needed.\n"
                + "brave : (https://api.search.brave.com/).\n"
                + "tavily : needs searchApiKey (https://tavily.com/).\n"
                + "bocha  : needs searchApiKey (https://open.bochaai.com/).\n"
                + "baidu  : needs searchApiKey (https://cloud.baidu.com/).");
        searchApiKey = config.getString(
            "searchApiKey",
            Configuration.CATEGORY_GENERAL,
            "",
            "API key for the selected provider (brave / tavily / bocha / baidu).");

        String suffixesRaw = config.getString(
            "titleStripSuffixes",
            Configuration.CATEGORY_GENERAL,
            DEFAULT_TITLE_STRIP_SUFFIXES,
            "JSON array of suffixes to strip from search result titles.\n"
                + "Example: [\" (page)\", \" - Wikipedia\"]");
        titleStripSuffixes = parseSuffixesJson(suffixesRaw);

        if (config.hasChanged()) config.save();
    }

    /** Re-read all values from the config file on disk. */
    public static void reload() {
        if (config == null) return;
        config.load();
        load();
    }

    // ── JSON suffix parser ─────────────────────────────────────────────────

    private static List<String> parseSuffixesJson(String raw) {
        if (raw == null || raw.trim()
            .isEmpty()) return Collections.emptyList();
        try {
            JsonArray arr = new JsonParser().parse(raw.trim())
                .getAsJsonArray();
            List<String> list = new ArrayList<>(arr.size());
            for (JsonElement el : arr) list.add(el.getAsString());
            return Collections.unmodifiableList(list);
        } catch (JsonSyntaxException | IllegalStateException e) {
            GTNHWikiSearch.LOGGER.warn("[WikiSearch] Failed to parse titleStripSuffixes: '{}' — {}", raw, e.toString());
            return Collections.emptyList();
        }
    }

    // ── Cookie / UA setters ────────────────────────────────────────────────

    public static void setCookie(String rawCookie) {
        String c = rawCookie.trim();
        if (c.contains(":")) {
            String[] parts = c.split(":", 2);
            if (parts[0].trim()
                .equalsIgnoreCase("cookie")) c = parts[1].trim();
        }
        if (c.toLowerCase()
            .startsWith("cookie="))
            c = c.substring("cookie=".length())
                .trim();
        cookie = c;
        save();
    }

    // public static void setUserAgent(...) { ... } // 暂时禁用

    public static void save() {
        if (config == null) return;
        config.get(Configuration.CATEGORY_GENERAL, "cookie", "")
            .set(cookie);
        config.save();
    }
}
