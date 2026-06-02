package com.sount.restful.search;

import com.sount.restful.method.HttpMethod;
import com.sount.restful.navigation.action.RestServiceItem;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

public final class SearchEngine {

    private static final int SCORE_PATH_EXACT = 200;
    private static final int SCORE_PATH_STARTS_WITH = 130;
    private static final int SCORE_PATH_CONTAINS = 95;
    private static final int SCORE_METHOD_NAME_EXACT = 90;
    private static final int SCORE_METHOD_NAME_CONTAINS = 65;
    private static final int SCORE_DESCRIPTION_CONTAINS = 70;
    private static final int SCORE_HTTP_METHOD_EXACT = 75;
    private static final int SCORE_MODULE_NAME_CONTAINS = 55;
    private static final int SCORE_CONTROLLER_NAME_CONTAINS = 45;
    private static final int MAX_USE_COUNT_BONUS = 50;

    private SearchEngine() {}

    public static @NotNull List<SearchResult> search(@Nullable SearchQuery query, @NotNull List<RestServiceItem> items) {
        return search(query, items, 200, null);
    }

    public static @NotNull List<SearchResult> search(@Nullable SearchQuery query, @NotNull List<RestServiceItem> items, int maxResults) {
        return search(query, items, maxResults, null);
    }

    public static @NotNull List<SearchResult> search(@Nullable SearchQuery query, @NotNull List<RestServiceItem> items,
                                                      int maxResults,
                                                      @Nullable Function<RestServiceItem, Integer> useCountLookup) {
        if (query == null || query.isEmpty()) {
            // Return all items sorted by URL
            return items.stream()
                    .map(item -> new SearchResult(item, 0, null))
                    .sorted()
                    .limit(maxResults)
                    .collect(Collectors.toList());
        }

        // Pre-lower tokens once
        List<String> lowerTokens = query.tokens().stream()
                .map(t -> t.toLowerCase(Locale.ROOT))
                .collect(Collectors.toList());

        List<SearchResult> results = new ArrayList<>();
        for (RestServiceItem item : items) {
            ScoreResult sr = scoreItem(query, item, lowerTokens, useCountLookup);
            if (sr.score > 0) {
                results.add(new SearchResult(item, sr.score, null, sr.matchedFields));
            }
        }

        Collections.sort(results);
        if (results.size() > maxResults) {
            return results.subList(0, maxResults);
        }
        return results;
    }

    private static @NotNull ScoreResult scoreItem(@NotNull SearchQuery query, @NotNull RestServiceItem item,
                                                   @NotNull List<String> lowerTokens,
                                                   @Nullable Function<RestServiceItem, Integer> useCountLookup) {
        int score = 0;
        Set<String> matchedFields = null; // lazy init

        // Method filter — hard filter
        if (query.methodFilter() != null) {
            if (item.getMethod() != query.methodFilter()) {
                return ScoreResult.NO_MATCH;
            }
            score += SCORE_HTTP_METHOD_EXACT;
            matchedFields = addMatchedField(matchedFields, MatchField.HTTP_METHOD);
        }

        if (lowerTokens.isEmpty()) {
            if (query.methodFilter() != null) {
                return new ScoreResult(score, matchedFields != null ? matchedFields : Collections.emptySet());
            }
            return ScoreResult.NO_MATCH;
        }

        // Quick check: all tokens must exist somewhere in the searchable text
        String searchableText = item.getSearchableText();
        for (String token : lowerTokens) {
            if (!searchableText.contains(token)) {
                return ScoreResult.NO_MATCH;
            }
        }

        // All tokens present — now compute per-token best score using individual fields
        // These getters are cached after first call (lazy)
        String path = lower(item.getUrl());
        String methodName = lower(item.getMethodName());
        String description = lower(item.getDescription());
        String httpMethod = lower(item.getMethodText());
        String moduleName = lower(item.getModuleName());
        String controllerName = lower(item.getControllerName());

        for (String t : lowerTokens) {
            int bestTokenScore = 0;
            String bestField = null;

            // Path matching (highest weight)
            if (path.equals(t)) {
                bestTokenScore = SCORE_PATH_EXACT;
                bestField = MatchField.PATH;
            } else if (path.startsWith(t)) {
                bestTokenScore = SCORE_PATH_STARTS_WITH;
                bestField = MatchField.PATH;
            } else if (path.contains(t)) {
                bestTokenScore = SCORE_PATH_CONTAINS;
                bestField = MatchField.PATH;
            }

            // Method name
            if (methodName.equals(t) && SCORE_METHOD_NAME_EXACT > bestTokenScore) {
                bestTokenScore = SCORE_METHOD_NAME_EXACT;
                bestField = MatchField.METHOD_NAME;
            } else if (methodName.contains(t) && SCORE_METHOD_NAME_CONTAINS > bestTokenScore) {
                bestTokenScore = SCORE_METHOD_NAME_CONTAINS;
                bestField = MatchField.METHOD_NAME;
            }

            // Description
            if (description.contains(t) && SCORE_DESCRIPTION_CONTAINS > bestTokenScore) {
                bestTokenScore = SCORE_DESCRIPTION_CONTAINS;
                bestField = MatchField.DESCRIPTION;
            }

            // HTTP method text
            if (httpMethod.equals(t) && SCORE_HTTP_METHOD_EXACT > bestTokenScore) {
                bestTokenScore = SCORE_HTTP_METHOD_EXACT;
                bestField = MatchField.HTTP_METHOD;
            }

            // Module name
            if (moduleName.contains(t) && SCORE_MODULE_NAME_CONTAINS > bestTokenScore) {
                bestTokenScore = SCORE_MODULE_NAME_CONTAINS;
                bestField = MatchField.MODULE_NAME;
            }

            // Controller name
            if (controllerName.contains(t) && SCORE_CONTROLLER_NAME_CONTAINS > bestTokenScore) {
                bestTokenScore = SCORE_CONTROLLER_NAME_CONTAINS;
                bestField = MatchField.CONTROLLER_NAME;
            }

            // bestTokenScore is guaranteed > 0 since searchableText.contains(t) passed
            score += bestTokenScore;
            if (bestField != null) {
                matchedFields = addMatchedField(matchedFields, bestField);
            }
        }

        // Use count bonus
        if (useCountLookup != null) {
            int useCount = useCountLookup.apply(item);
            score += Math.min(useCount, MAX_USE_COUNT_BONUS);
        }

        return new ScoreResult(score, matchedFields != null ? matchedFields : Collections.emptySet());
    }

    private static Set<String> addMatchedField(@Nullable Set<String> set, String field) {
        if (set == null) {
            set = new LinkedHashSet<>();
        }
        set.add(field);
        return set;
    }

    private record ScoreResult(int score, Set<String> matchedFields) {
        static final ScoreResult NO_MATCH = new ScoreResult(0, Collections.emptySet());
    }

    private static @NotNull String lower(@Nullable String s) {
        return s != null ? s.toLowerCase(Locale.ROOT) : "";
    }
}
