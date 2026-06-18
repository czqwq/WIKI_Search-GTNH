package com.czqwq.wikisearch.search;

import java.util.List;

/**
 * Abstraction over a search backend.
 * Implementations fetch results from a search engine (DDG, Bing, etc.)
 * and return structured {@link SearchResult}s.
 */
public interface SearchEngine {

    /** @return a display name for logging / error messages (e.g. "DuckDuckGo"). */
    String getName();

    /** @return true if this backend is configured and ready to use. */
    boolean isAvailable();

    /**
     * Search for pages matching the given keyword.
     * 
     * @return list of results, never null.
     * @throws Exception on network or parse errors.
     */
    List<SearchResult> search(String keyword) throws Exception;
}
