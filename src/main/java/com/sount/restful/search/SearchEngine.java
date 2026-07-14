package com.sount.restful.search;

import com.intellij.openapi.diagnostic.Logger;
import com.sount.restful.navigation.RestServiceItem;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

public final class SearchEngine {

    private static final Logger LOG = Logger.getInstance(SearchEngine.class);

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
        return search(query, items, maxResults, useCountLookup, PathSearchOptions.EMPTY);
    }

    public static @NotNull List<SearchResult> search(@Nullable SearchQuery query, @NotNull List<RestServiceItem> items,
                                                      int maxResults,
                                                      @Nullable Function<RestServiceItem, Integer> useCountLookup,
                                                      @NotNull PathSearchOptions pathSearchOptions) {
        if (query == null || query.isEmpty()) {
            return items.stream()
                    .map(item -> new SearchResult(item, 0, null))
                    .sorted()
                    .limit(maxResults)
                    .collect(Collectors.toList());
        }

        long startTime = System.nanoTime();

        // Pre-lower tokens once
        List<String> lowerTokens = query.tokens().stream()
                .map(t -> t.toLowerCase(Locale.ROOT))
                .collect(Collectors.toList());

        // Top-N with min-heap: avoid sorting the full result list
        PriorityQueue<SearchResult> topN = new PriorityQueue<>(maxResults + 1);
        for (RestServiceItem item : items) {
            ScoreResult sr = scoreItem(query, item, lowerTokens, useCountLookup, pathSearchOptions);
            if (sr.score > 0) {
                topN.add(new SearchResult(item, sr.score, null, sr.matchedFields));
                if (topN.size() > maxResults) {
                    topN.poll(); // remove lowest score
                }
            }
        }

        List<SearchResult> results = new ArrayList<>(topN);
        Collections.sort(results);

        long elapsedMs = (System.nanoTime() - startTime) / 1_000_000;
        if (elapsedMs > 10 || LOG.isDebugEnabled()) {
            LOG.debug("Search '" + query.rawInput() + "': " + results.size() + "/" + items.size()
                    + " items in " + elapsedMs + "ms");
        }

        return results;
    }

    private static @NotNull ScoreResult scoreItem(@NotNull SearchQuery query, @NotNull RestServiceItem item,
                                                   @NotNull List<String> lowerTokens,
                                                   @Nullable Function<RestServiceItem, Integer> useCountLookup,
                                                   @NotNull PathSearchOptions pathSearchOptions) {
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

        ScoreResult pathMatch = scorePathQuery(query, item, pathSearchOptions, score, matchedFields);
        if (pathMatch != null) {
            return addUseCountBonus(pathMatch, item, useCountLookup);
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

        // All tokens present — now compute per-token best score using pre-computed lowercase fields
        String path = item.getLowerUrl();
        String methodName = item.getLowerMethodName();
        String description = item.getLowerDescription();
        String httpMethod = item.getLowerHttpMethod();
        String moduleName = item.getLowerModuleName();
        String controllerName = item.getLowerControllerName();

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

        return addUseCountBonus(new ScoreResult(score,
                matchedFields != null ? matchedFields : Collections.emptySet()), item, useCountLookup);
    }

    /** Returns null for regular text queries so their existing scoring behavior is unchanged. */
    private static @Nullable ScoreResult scorePathQuery(@NotNull SearchQuery query, @NotNull RestServiceItem item,
                                                        @NotNull PathSearchOptions options, int methodScore,
                                                        @Nullable Set<String> matchedFields) {
        if (query.tokens().size() != 1 || !query.tokens().get(0).startsWith("/")) {
            return null;
        }
        String rawPath = query.tokens().get(0);
        String endpointPath = item.getUrl();
        if (endpointPath == null || endpointPath.isBlank()) {
            return null;
        }

        List<String> candidates = PathTemplateMatcher.candidates(rawPath, item.getContextPath(), options.gatewayPrefixes());
        for (int i = 0; i < candidates.size(); i++) {
            String candidate = candidates.get(i);
            if (PathTemplateMatcher.matches(endpointPath, candidate)) {
                int matchScore = endpointPath.equalsIgnoreCase(candidate) ? SCORE_PATH_EXACT : SCORE_PATH_STARTS_WITH;
                // Prefer the untouched request path over a normalized variant, but keep both usable.
                matchScore -= i * 5;
                Set<String> fields = addMatchedField(matchedFields, MatchField.PATH);
                return new ScoreResult(methodScore + matchScore, fields);
            }
        }
        return null;
    }

    private static @NotNull ScoreResult addUseCountBonus(@NotNull ScoreResult result, @NotNull RestServiceItem item,
                                                         @Nullable Function<RestServiceItem, Integer> useCountLookup) {
        if (result.score <= 0 || useCountLookup == null) {
            return result;
        }
        int useCount = useCountLookup.apply(item);
        return new ScoreResult(result.score + Math.min(useCount, MAX_USE_COUNT_BONUS), result.matchedFields);
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
