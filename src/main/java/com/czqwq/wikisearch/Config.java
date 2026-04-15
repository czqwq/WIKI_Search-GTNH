package com.czqwq.wikisearch;

import java.io.File;

import net.minecraftforge.common.config.Configuration;

public class Config {

    private static Configuration config;

    public static String cookie = "";
    public static String searchApiUrl = "";
    public static String wikiPageBase = "";

    static final String DEFAULT_SEARCH_API_URL = "https://gtnh.huijiwiki.com/api.php?action=query&list=search&srnamespace=0&srlimit=8&format=json&srsearch={item}";
    static final String DEFAULT_WIKI_PAGE_BASE = "https://gtnh.huijiwiki.com";

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

    public static void save() {
        if (config == null) return;
        config.get(Configuration.CATEGORY_GENERAL, "cookie", "")
            .set(cookie);
        config.save();
    }
}
