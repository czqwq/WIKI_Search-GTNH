package com.czqwq.wikisearch.search;

/** Immutable search result: a page title and its URL. */
public final class SearchResult {

    private final String title;
    private final String url;

    public SearchResult(String title, String url) {
        this.title = title;
        this.url = url;
    }

    public String title() {
        return title;
    }

    public String url() {
        return url;
    }
}
