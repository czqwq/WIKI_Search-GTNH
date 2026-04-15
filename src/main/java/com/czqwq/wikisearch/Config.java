package com.czqwq.wikisearch;

import java.io.File;

import net.minecraftforge.common.config.Configuration;

public class Config {

    private static Configuration config;

    public static String cookie = "";
    public static String searchApiUrl = "";
    public static String wikiPageBase = "";
    public static String pingHost = "";
    public static String userAgent = "";

    static final String DEFAULT_SEARCH_API_URL = "https://gtnh.huijiwiki.com/api.php?action=query&list=search&srnamespace=0&srlimit=8&format=json&srsearch={item}";
    static final String DEFAULT_WIKI_PAGE_BASE = "https://gtnh.huijiwiki.com";
    static final String DEFAULT_PING_HOST = "baidu.com";
    static final String DEFAULT_USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) "
        + "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36";

    public static void init(File configFile) {
        config = new Configuration(configFile);
        load();
    }

    private static void load() {
        cookie = config.getString(
            "cookie",
            Configuration.CATEGORY_GENERAL,
            "",
            "Cookie string for wiki access (set via /wikisearch cookie <value>)");
        searchApiUrl = config.getString(
            "searchApiUrl",
            Configuration.CATEGORY_GENERAL,
            DEFAULT_SEARCH_API_URL,
            "Search API URL template. {item} is replaced with the URL-encoded item name.");
        wikiPageBase = config.getString(
            "wikiPageBase",
            Configuration.CATEGORY_GENERAL,
            DEFAULT_WIKI_PAGE_BASE,
            "Base URL used to build result page links as <wikiPageBase>/wiki/<title>.");
        pingHost = config.getString(
            "pingHost",
            Configuration.CATEGORY_GENERAL,
            DEFAULT_PING_HOST,
            "Hostname used by /wikisearch ping to test network connectivity.");
        userAgent = config.getString(
            "userAgent",
            Configuration.CATEGORY_GENERAL,
            DEFAULT_USER_AGENT,
            "HTTP User-Agent sent with wiki requests. Must match the browser used to obtain cf_clearance."
                + " Set automatically by /wikisearch auth.");
        if (config.hasChanged()) {
            config.save();
        }
    }

    /** Re-read all values from the config file on disk. */
    public static void reload() {
        if (config == null) return;
        config.load();
        load();
    }

    public static void setCookie(String rawCookie) {
        String c = rawCookie.trim();
        // Strip leading "Cookie: " or "cookie=" prefixes
        if (c.contains(":")) {
            String[] parts = c.split(":", 2);
            if (parts[0].trim()
                .equalsIgnoreCase("cookie")) {
                c = parts[1].trim();
            }
        }
        if (c.toLowerCase()
            .startsWith("cookie=")) {
            c = c.substring("cookie=".length())
                .trim();
        }
        cookie = c;
        save();
    }

    public static void setUserAgent(String ua) {
        userAgent = ua == null || ua.trim()
            .isEmpty() ? DEFAULT_USER_AGENT : ua.trim();
        if (config != null) {
            config.get(Configuration.CATEGORY_GENERAL, "userAgent", DEFAULT_USER_AGENT)
                .set(userAgent);
            config.save();
        }
    }

    public static void save() {
        if (config == null) return;
        config.get(Configuration.CATEGORY_GENERAL, "cookie", "")
            .set(cookie);
        config.save();
    }
}
