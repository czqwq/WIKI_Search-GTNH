package com.czqwq.wikisearch.search;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import javax.net.ssl.SSLSocketFactory;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import com.czqwq.wikisearch.ChromeLikeSSLSocketFactory;
import com.czqwq.wikisearch.Config;
import com.czqwq.wikisearch.GTNHWikiSearch;

public class DuckDuckGoSearchEngine implements SearchEngine {

    private static final String DDG_URL = "https://html.duckduckgo.com/html/";
    private static final int TIMEOUT_MS = 10000;
    private static final String UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";
    private static final SSLSocketFactory CHROME_SSL = new ChromeLikeSSLSocketFactory(
        (SSLSocketFactory) SSLSocketFactory.getDefault());

    private static final String BODY_SEL = ".result__body";
    private static final String TITLE_SEL = "a.result__a";
    private static final String URL_SEL = "a.result__url";
    private static final String FB_BODY = ".web-result";
    private static final String FB_TITLE = "a.result__a, h2 a";
    private static final String FB_URL = "a.result__url, .result__url a";

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
        String q = "site:" + Config.wikiSearchDomain + " " + keyword;
        String eq = java.net.URLEncoder.encode(q, StandardCharsets.UTF_8.name());

        Document doc = Jsoup.connect(DDG_URL + "?q=" + eq)
            .userAgent(UA)
            .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
            .header("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8")
            .header("Cache-Control", "no-cache")
            .header("sec-ch-ua", "\"Chromium\";v=\"120\", \"Google Chrome\";v=\"120\", \"Not-A.Brand\";v=\"99\"")
            .header("sec-ch-ua-mobile", "?0")
            .header("sec-ch-ua-platform", "\"Windows\"")
            .sslSocketFactory(CHROME_SSL)
            .timeout(TIMEOUT_MS)
            .post();

        List<SearchResult> results = parseResults(doc);
        logDiag(doc, keyword, results);
        return results;
    }

    private List<SearchResult> parseResults(Document doc) {
        List<SearchResult> out = new ArrayList<>();
        for (Element b : doc.select(BODY_SEL)) {
            SearchResult r = extract(
                b,
                b.select(TITLE_SEL)
                    .first(),
                b.select(URL_SEL)
                    .first());
            if (r != null) out.add(r);
        }
        if (out.isEmpty()) {
            for (Element b : doc.select(FB_BODY)) {
                Element t = b.select(FB_TITLE)
                    .first();
                Element u = b.select(FB_URL)
                    .first();
                if (t == null) t = b.select("a")
                    .first();
                if (t != null) {
                    SearchResult r = extractFallback(t, u);
                    if (r != null) out.add(r);
                }
            }
        }
        if (out.isEmpty()) {
            for (Element a : doc.select("a[href*=" + Config.wikiSearchDomain + "]")) {
                String ru = extractRealUrl(a.attr("href"));
                if (ru != null && ru.contains(Config.wikiSearchDomain)) {
                    out.add(new SearchResult(stripSuffix(a.hasText() ? a.text() : deriveTitle(ru)), ru));
                }
            }
        }
        return out;
    }

    private SearchResult extract(Element body, Element tl, Element ul) {
        String raw = ul != null ? ul.attr("href") : null;
        if ((raw == null || raw.isEmpty()) && tl != null) raw = tl.attr("href");
        if (raw == null || raw.isEmpty()) return null;
        String real = extractRealUrl(raw);
        if (real == null || !real.contains(Config.wikiSearchDomain)) return null;
        String title = cleanTitle(tl, ul, real);
        return new SearchResult(title, real);
    }

    private SearchResult extractFallback(Element t, Element u) {
        String raw = u != null ? u.attr("href") : t.attr("href");
        if (raw == null || raw.isEmpty()) return null;
        String real = extractRealUrl(raw);
        if (real == null || !real.contains(Config.wikiSearchDomain)) return null;
        return new SearchResult(stripSuffix(t.hasText() ? t.text() : deriveTitle(real)), real);
    }

    // -- title --

    private static String cleanTitle(Element tl, Element ul, String realUrl) {
        if (tl != null) {
            String t = tl.text();
            if (t != null && !t.isEmpty() && !t.startsWith("http")) return stripSuffix(t.trim());
        }
        if (ul != null) {
            String t = ul.text();
            if (t != null && !t.isEmpty() && !t.startsWith("http") && t.length() < 120) return stripSuffix(t.trim());
        }
        return deriveTitle(realUrl);
    }

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

    // -- URL --

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
        int fi = p.indexOf('#');
        if (fi >= 0) p = p.substring(0, fi);
        try {
            p = URLDecoder.decode(p, StandardCharsets.UTF_8.name());
        } catch (Exception ignore) {}
        return p.replace('_', ' ')
            .trim();
    }

    static String extractRealUrl(String raw) {
        if (raw == null || raw.isEmpty()) return null;
        if (!raw.contains("duckduckgo.com/l/")) return raw.startsWith("//") ? "https:" + raw : raw;
        int u = raw.indexOf("uddg=");
        if (u < 0) return null;
        String tail = raw.substring(u + 5);
        int a = tail.indexOf('&');
        try {
            return URLDecoder.decode(a > 0 ? tail.substring(0, a) : tail, StandardCharsets.UTF_8.name());
        } catch (Exception e) {
            return null;
        }
    }

    private static void logDiag(Document doc, String kw, List<SearchResult> r) {
        GTNHWikiSearch.LOGGER.debug(
            "[WikiSearch] DDG: bodies={} urls={} fb={} results={}",
            doc.select(BODY_SEL)
                .size(),
            doc.select(URL_SEL)
                .size(),
            doc.select(FB_BODY)
                .size(),
            r.size());
        if (r.isEmpty()) {
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
            GTNHWikiSearch.LOGGER.debug("[WikiSearch] DDG empty: keyword='{}' snippet='{}'", kw, s.replace('\n', ' '));
        }
    }
}
