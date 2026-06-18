package com.czqwq.wikisearch.search;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import javax.net.ssl.SSLSocketFactory;

import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import com.czqwq.wikisearch.ChromeLikeSSLSocketFactory;
import com.czqwq.wikisearch.Config;
import com.czqwq.wikisearch.GTNHWikiSearch;

/**
 * Searches the GTNH wiki via DuckDuckGo's <b>Lite</b> endpoint.
 * <p>
 * DDG's {@code html.duckduckgo.com} now classifies non-browser clients as
 * {@code cc=botnet} and serves an empty anomaly page. The Lite route
 * ({@code lite.duckduckgo.com/lite/}) is a plain-HTML interface designed
 * for pre-2010 devices — it has no JS, simple {@code 
 * 
<table>
 * } markup,
 * and reliably returns results when presented with a proper form POST.
 */
public class DuckDuckGoSearchEngine implements SearchEngine {

    private static final String LITE_URL = "https://lite.duckduckgo.com/lite/";
    private static final int TIMEOUT_MS = 10000;
    private static final String UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) "
        + "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36";
    private static final SSLSocketFactory CHROME_SSL = new ChromeLikeSSLSocketFactory(
        (SSLSocketFactory) SSLSocketFactory.getDefault());

    @Override
    public String getName() {
        return "DuckDuckGo";
    }

    @Override
    public boolean isAvailable() {
        return Config.wikiSearchDomain != null && !Config.wikiSearchDomain.isEmpty();
    }

    @Override
    public List<SearchResult> search(String keyword) throws Exception {
        String query = "site:" + Config.wikiSearchDomain + " " + keyword;

        Connection.Response resp = Jsoup.connect(LITE_URL)
            .method(Connection.Method.POST)
            .userAgent(UA)
            .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8")
            .header("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8")
            .header("Content-Type", "application/x-www-form-urlencoded")
            .header("Origin", "https://lite.duckduckgo.com")
            .header("Referer", "https://lite.duckduckgo.com/")
            .sslSocketFactory(CHROME_SSL)
            .data("q", query)
            .data("b", "")
            .data("kl", "wt-wt")
            .timeout(TIMEOUT_MS)
            .execute();

        Document doc = resp.parse();
        List<SearchResult> results = parseLite(doc, keyword);
        logDiag(doc, keyword, results);
        return results;
    }

    /**
     * Parse DDG Lite's {@code 
     * 
    <table>
     * }-based result layout.
     */
    private List<SearchResult> parseLite(Document doc, String keyword) {
        List<SearchResult> out = new ArrayList<>();
        // Lite uses compact tables: each result has a.result-link
        Elements links = doc.select("a.result-link");
        for (Element a : links) {
            String raw = a.attr("href");
            String realUrl = extractRealUrl(raw);
            if (realUrl == null || !realUrl.contains(Config.wikiSearchDomain)) continue;
            String title = a.hasText() ? a.text() : deriveTitle(realUrl);
            out.add(new SearchResult(stripSuffix(title), realUrl));
        }
        if (out.isEmpty()) {
            // fallback: any link to the target domain
            for (Element a : doc.select("a[href*=" + Config.wikiSearchDomain + "]")) {
                String raw = a.attr("href");
                String realUrl = extractRealUrl(raw);
                if (realUrl == null || !realUrl.contains(Config.wikiSearchDomain)) continue;
                out.add(new SearchResult(stripSuffix(a.text()), realUrl));
            }
        }
        return out;
    }

    // ── Title ─────────────────────────────────────────────────────────────

    static String stripSuffix(String title) {
        if (title == null || title.isEmpty()) return title;
        for (String s : Config.titleStripSuffixes) {
            if (title.endsWith(s)) {
                String st = title.substring(0, title.length() - s.length());
                if (!st.isEmpty()) return st;
                break;
            }
        }
        return title;
    }

    private static String deriveTitle(String url) {
        if (url == null || url.isEmpty()) return "Unknown";
        String p = url;
        int sa = p.indexOf("://");
        if (sa >= 0) p = p.substring(sa + 3);
        int fs = p.indexOf('/');
        if (fs >= 0) p = p.substring(fs + 1);
        if (p.startsWith("wiki/")) p = p.substring(5);
        int qi = p.indexOf('?');
        if (qi >= 0) p = p.substring(0, qi);
        try {
            p = URLDecoder.decode(p, StandardCharsets.UTF_8.name());
        } catch (Exception ignore) {}
        return p.replace('_', ' ')
            .trim();
    }

    // ── URL ───────────────────────────────────────────────────────────────

    static String extractRealUrl(String raw) {
        if (raw == null || raw.isEmpty()) return null;
        if (raw.contains("uddg=")) {
            int u = raw.indexOf("uddg=") + 5;
            String tail = raw.substring(u);
            int a = tail.indexOf('&');
            try {
                return URLDecoder.decode(a > 0 ? tail.substring(0, a) : tail, StandardCharsets.UTF_8.name());
            } catch (Exception e) {
                return null;
            }
        }
        // direct or protocol-relative
        if (!raw.contains("duckduckgo.com/l/")) {
            return raw.startsWith("//") ? "https:" + raw : raw;
        }
        return null;
    }

    // ── Logging ───────────────────────────────────────────────────────────

    private static void logDiag(Document doc, String keyword, List<SearchResult> r) {
        GTNHWikiSearch.LOGGER.debug(
            "[WikiSearch] DDG Lite: links={} results={} title='{}'",
            doc.select("a.result-link")
                .size(),
            r.size(),
            doc.title());
        if (r.isEmpty()) {
            String s = doc.body() != null ? doc.body()
                .html()
                .substring(
                    0,
                    Math.min(
                        doc.body()
                            .html()
                            .length(),
                        300))
                : "";
            GTNHWikiSearch.LOGGER
                .debug("[WikiSearch] DDG empty: keyword='{}' snippet='{}'", keyword, s.replace('\n', ' '));
        }
    }
}
