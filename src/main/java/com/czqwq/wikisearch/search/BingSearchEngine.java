package com.czqwq.wikisearch.search;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import javax.net.ssl.SSLSocketFactory;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import com.czqwq.wikisearch.ChromeLikeSSLSocketFactory;
import com.czqwq.wikisearch.Config;
import com.czqwq.wikisearch.GTNHWikiSearch;

/**
 * Searches via Bing's HTML search with {@code site:} filtering.
 * <p>
 * Bing is less aggressive with bot detection than DDG and serves
 * plain-HTML results that Jsoup can parse without JS execution.
 */
public class BingSearchEngine implements SearchEngine {

    private static final String BING_URL = "https://www.bing.com/search";
    private static final int TIMEOUT_MS = 10000;
    private static final String UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";
    private static final SSLSocketFactory CHROME_SSL = new ChromeLikeSSLSocketFactory(
        (SSLSocketFactory) SSLSocketFactory.getDefault());

    // Bing result blocks: li.b_algo > h2 > a
    private static final String RESULT_SEL = "li.b_algo h2 a";
    private static final String FALLBACK_SEL = "ol#b_results h2 a";

    @Override
    public String getName() {
        return "Bing";
    }

    @Override
    public boolean isAvailable() {
        return Config.wikiSearchDomain != null && !Config.wikiSearchDomain.isEmpty();
    }

    @Override
    public List<SearchResult> search(String keyword) throws Exception {
        String q = "site:" + Config.wikiSearchDomain + " " + keyword;
        String eq = java.net.URLEncoder.encode(q, StandardCharsets.UTF_8.name());

        Document doc = Jsoup.connect(BING_URL + "?q=" + eq)
            .userAgent(UA)
            .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
            .header("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8")
            .header("sec-ch-ua", "\"Chromium\";v=\"120\", \"Google Chrome\";v=\"120\"")
            .header("sec-ch-ua-mobile", "?0")
            .header("sec-ch-ua-platform", "\"Windows\"")
            .sslSocketFactory(CHROME_SSL)
            .timeout(TIMEOUT_MS)
            .get();

        List<SearchResult> results = parseResults(doc);
        GTNHWikiSearch.LOGGER.debug(
            "[WikiSearch] Bing: links={} results={}",
            doc.select(RESULT_SEL)
                .size(),
            results.size());
        if (results.isEmpty()) {
            String s = doc.body() != null ? doc.body()
                .html()
                .substring(
                    0,
                    Math.min(
                        doc.body()
                            .html()
                            .length(),
                        200))
                : "";
            GTNHWikiSearch.LOGGER
                .debug("[WikiSearch] Bing empty: keyword='{}' snippet='{}'", keyword, s.replace('\n', ' '));
        }
        return results;
    }

    private List<SearchResult> parseResults(Document doc) {
        List<SearchResult> out = new ArrayList<>();
        Elements links = doc.select(RESULT_SEL);
        if (links.isEmpty()) links = doc.select(FALLBACK_SEL);

        for (Element a : links) {
            String href = a.attr("href");
            if (href == null || href.isEmpty()) continue;
            if (!href.contains(Config.wikiSearchDomain)) continue;
            String title = a.text();
            if (title == null || title.isEmpty()) title = deriveTitle(href);
            out.add(new SearchResult(DuckDuckGoSearchEngine.stripSuffix(title), href));
        }
        return out;
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
}
