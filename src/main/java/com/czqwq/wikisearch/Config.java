package com.czqwq.wikisearch;

import java.io.File;

import net.minecraftforge.common.config.Configuration;

public class Config {

    private static Configuration config;

    public static String cookie = "";

    public static void init(File configFile) {
        config = new Configuration(configFile);
        load();
    }

    private static void load() {
        cookie = config.getString(
            "cookie",
            Configuration.CATEGORY_GENERAL,
            "",
            "Cookie string for GTNH Huiji Wiki access (set via /wikisearch cookie <value>)");
        if (config.hasChanged()) {
            config.save();
        }
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
