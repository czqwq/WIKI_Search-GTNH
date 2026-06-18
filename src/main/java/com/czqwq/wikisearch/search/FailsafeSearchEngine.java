package com.czqwq.wikisearch.search;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.czqwq.wikisearch.GTNHWikiSearch;

/**
 * Composite search engine that tries a chain of backends.
 * <p>
 * Engines are tried in order. The first engine that returns a non-empty
 * result list wins. If all engines fail, an empty list is returned
 * and a warning is logged.
 */
public class FailsafeSearchEngine implements SearchEngine {

    private final List<SearchEngine> chain;

    public FailsafeSearchEngine(List<SearchEngine> engines) {
        this.chain = Collections.unmodifiableList(new ArrayList<>(engines));
    }

    @Override
    public String getName() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < chain.size(); i++) {
            if (i > 0) sb.append(" → ");
            sb.append(
                chain.get(i)
                    .getName());
        }
        return sb.toString();
    }

    @Override
    public boolean isAvailable() {
        for (SearchEngine e : chain) if (e.isAvailable()) return true;
        return false;
    }

    @Override
    public List<SearchResult> search(String keyword) throws Exception {
        List<String> errors = new ArrayList<>();

        for (SearchEngine engine : chain) {
            if (!engine.isAvailable()) {
                GTNHWikiSearch.LOGGER.debug("[WikiSearch] Skipping {} — not configured", engine.getName());
                continue;
            }
            try {
                List<SearchResult> results = engine.search(keyword);
                if (!results.isEmpty()) {
                    if (chain.indexOf(engine) > 0) {
                        GTNHWikiSearch.LOGGER
                            .info("[WikiSearch] Primary engine failed, {} succeeded", engine.getName());
                    }
                    return results;
                }
                GTNHWikiSearch.LOGGER.debug("[WikiSearch] {} returned 0 results, trying next", engine.getName());
            } catch (Exception e) {
                String msg = e.getMessage() != null ? e.getMessage()
                    : e.getClass()
                        .getSimpleName();
                errors.add(engine.getName() + ": " + msg);
                GTNHWikiSearch.LOGGER.debug("[WikiSearch] {} threw: {}", engine.getName(), msg);
            }
        }

        // All engines exhausted — log summary
        if (!errors.isEmpty()) {
            GTNHWikiSearch.LOGGER.debug("[WikiSearch] All engines failed: {}", String.join(" | ", errors));
        } else {
            GTNHWikiSearch.LOGGER.debug("[WikiSearch] All engines returned 0 results");
        }
        return Collections.emptyList();
    }
}
