package com.czqwq.wikisearch;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.List;

import com.czqwq.wikisearch.chat.ChatFormatter;
import com.czqwq.wikisearch.search.*;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

@SideOnly(Side.CLIENT)
public final class WikiSearchFetcher {

    private static final int TIMEOUT_MS = 15000;

    /** The active engine chain. Null until first access. */
    private static volatile SearchEngine engine;

    private WikiSearchFetcher() {}

    /** Build the default engine chain: DDG → Bing → custom (if configured). */
    public static SearchEngine getEngine() {
        if (engine == null) {
            synchronized (WikiSearchFetcher.class) {
                if (engine == null) {
                    engine = buildChain();
                }
            }
        }
        return engine;
    }

    private static SearchEngine buildChain() {
        List<SearchEngine> engines = new java.util.ArrayList<>();
        // Order: API providers first (each checks isAvailable via searchProvider),
        // then free HTML-scrape fallbacks.
        engines.add(new BraveSearchEngine()); // searchProvider=brave + searchApiKey
        engines.add(new TavilySearchEngine()); // searchProvider=tavily + searchApiKey
        engines.add(new BochaSearchEngine()); // searchProvider=bocha + searchApiKey
        engines.add(new BaiduSearchEngine()); // searchProvider=baidu + searchApiKey
        engines.add(new DuckDuckGoSearchEngine()); // free, always available
        engines.add(new BingSearchEngine()); // free, always available
        return new FailsafeSearchEngine(engines);
    }

    /** Allow replacing the engine at runtime. */
    public static void setEngine(SearchEngine e) {
        engine = e;
    }

    // -- Search --

    public static void fetchAndDisplay(String itemName) {
        Thread t = new Thread(() -> {
            try {
                List<SearchResult> results = getEngine().search(itemName);
                if (results.isEmpty()) {
                    ChatFormatter.displayAllEnginesFailed();
                } else {
                    ChatFormatter.displayResults(itemName, results);
                }
            } catch (Exception e) {
                GTNHWikiSearch.LOGGER.debug("[WikiSearch] Search failed", e);
                ChatFormatter.displaySearchError(itemName, e);
            }
        }, "WikiSearch-" + itemName);
        t.setDaemon(true);
        t.start();
    }

    // -- Ping --

    public static void pingAndDisplay(String host) {
        ChatFormatter.displayPingStart(host);
        Thread t = new Thread(() -> {
            try {
                long start = System.currentTimeMillis();
                InetAddress addr = InetAddress.getByName(host);
                if (addr.isReachable(TIMEOUT_MS)) {
                    ChatFormatter.displayPingSuccess(host, addr.getHostAddress(), System.currentTimeMillis() - start);
                } else {
                    tryTcp(host, addr, start);
                }
            } catch (Exception e) {
                GTNHWikiSearch.LOGGER.debug("[WikiSearch] Ping failed host={}", host, e);
                ChatFormatter.displayPingError(host);
            }
        }, "WikiSearch-ping");
        t.setDaemon(true);
        t.start();
    }

    private static void tryTcp(String host, InetAddress addr, long start) {
        try (Socket s = new Socket()) {
            s.connect(new InetSocketAddress(addr, 80), TIMEOUT_MS);
            ChatFormatter.displayPingSuccessTcp(host, addr.getHostAddress(), System.currentTimeMillis() - start);
        } catch (Exception e) {
            ChatFormatter.displayPingFailure(host, addr.getHostAddress(), e.getMessage());
        }
    }
}
