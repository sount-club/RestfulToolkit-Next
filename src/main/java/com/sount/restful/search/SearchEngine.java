package com.sount.restful.search;

import com.intellij.psi.codeStyle.MinusculeMatcher;
import com.intellij.psi.codeStyle.NameUtil;
import com.intellij.util.text.matching.MatchingMode;
import com.sount.restful.common.spring.AntPathMatcher;
import com.sount.restful.method.HttpMethod;
import com.sount.restful.navigation.action.RestServiceItem;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.stream.Collectors;

public final class SearchEngine {

    private static final int SCORE_EXACT_URL = 1000;
    private static final int SCORE_STARTS_WITH_URL = 800;
    private static final int SCORE_CONTAINS_URL = 600;
    private static final int SCORE_FUZZY_URL = 400;
    private static final int SCORE_METHOD_BONUS = 200;
    private static final int SCORE_CLASS_MATCH = 500;
    private static final int SCORE_METHOD_NAME_MATCH = 500;
    private static final int SCORE_FAVORITE_BONUS = 300;
    private static final int SCORE_RECENT_ACCESS_BONUS = 50;
    private static final int MAX_RECENT_BONUS = 200;

    private static final AntPathMatcher PATH_MATCHER = new AntPathMatcher();

    private SearchEngine() {}

    public static @NotNull List<SearchResult> search(@Nullable SearchQuery query, @NotNull List<RestServiceItem> items) {
        return search(query, items, 200);
    }

    public static @NotNull List<SearchResult> search(@Nullable SearchQuery query, @NotNull List<RestServiceItem> items, int maxResults) {
        if (query == null || query.isEmpty()) {
            // Return all items sorted by URL
            return items.stream()
                    .map(item -> new SearchResult(item, 0, null))
                    .sorted()
                    .limit(maxResults)
                    .collect(Collectors.toList());
        }

        List<SearchResult> results = new ArrayList<>();
        for (RestServiceItem item : items) {
            int score = scoreItem(query, item);
            if (score > 0) {
                String dimension = determineDimension(query, item);
                results.add(new SearchResult(item, score, dimension));
            }
        }

        Collections.sort(results);
        if (results.size() > maxResults) {
            return results.subList(0, maxResults);
        }
        return results;
    }

    private static int scoreItem(@NotNull SearchQuery query, @NotNull RestServiceItem item) {
        int score = 0;

        // Method filter
        if (query.methodFilter() != null) {
            if (item.getMethod() != query.methodFilter()) {
                return 0; // Hard filter - skip non-matching methods
            }
            score += SCORE_METHOD_BONUS;
        }

        // URL pattern matching
        if (query.urlPattern() != null) {
            int urlScore = scoreUrlMatch(query.urlPattern(), item.getUrl());
            if (urlScore == 0 && query.classNamePattern() == null && query.methodNamePattern() == null) {
                return 0; // No URL match and no other dimensions to match
            }
            score += urlScore;
        }

        // Class name matching
        if (query.classNamePattern() != null) {
            String locationText = item.getLocationText();
            if (locationText != null) {
                String className = extractClassName(locationText);
                if (containsIgnoreCase(className, query.classNamePattern())) {
                    score += SCORE_CLASS_MATCH;
                } else if (fuzzyMatch(className, query.classNamePattern())) {
                    score += SCORE_CLASS_MATCH / 2;
                } else if (query.urlPattern() == null && query.methodNamePattern() == null) {
                    return 0;
                }
            }
        }

        // Method name matching
        if (query.methodNamePattern() != null) {
            String locationText = item.getLocationText();
            if (locationText != null) {
                String methodName = extractMethodName(locationText);
                if (containsIgnoreCase(methodName, query.methodNamePattern())) {
                    score += SCORE_METHOD_NAME_MATCH;
                } else if (fuzzyMatch(methodName, query.methodNamePattern())) {
                    score += SCORE_METHOD_NAME_MATCH / 2;
                } else if (query.urlPattern() == null && query.classNamePattern() == null) {
                    return 0;
                }
            }
        }

        // If we only had a method filter and no other patterns matched, still include
        if (score == SCORE_METHOD_BONUS) {
            score += SCORE_FUZZY_URL; // Give some base score for method-only filter
        }

        return score;
    }

    private static int scoreUrlMatch(@NotNull String pattern, @Nullable String url) {
        if (url == null) return 0;

        String lowerPattern = pattern.toLowerCase(Locale.ROOT);
        String lowerUrl = url.toLowerCase(Locale.ROOT);

        // Exact match
        if (lowerUrl.equals(lowerPattern)) {
            return SCORE_EXACT_URL;
        }

        // Starts with
        if (lowerUrl.startsWith(lowerPattern)) {
            return SCORE_STARTS_WITH_URL;
        }

        // Contains
        if (lowerUrl.contains(lowerPattern)) {
            return SCORE_CONTAINS_URL;
        }

        // Fuzzy match with MinusculeMatcher
        MinusculeMatcher matcher = NameUtil.buildMatcher("*" + pattern)
                .withMatchingMode(MatchingMode.IGNORE_CASE)
                .build();
        if (matcher.matches(url)) {
            return SCORE_FUZZY_URL;
        }

        // AntPathMatcher for REST-style {variable} paths
        try {
            if (PATH_MATCHER.match(url, pattern) || PATH_MATCHER.match(pattern, url)) {
                return SCORE_FUZZY_URL;
            }
        } catch (Exception ignored) {
            // AntPathMatcher can throw on malformed patterns
        }

        return 0;
    }

    private static boolean fuzzyMatch(@Nullable String text, @Nullable String pattern) {
        if (text == null || pattern == null) return false;
        MinusculeMatcher matcher = NameUtil.buildMatcher("*" + pattern)
                .withMatchingMode(MatchingMode.IGNORE_CASE)
                .build();
        return matcher.matches(text);
    }

    private static boolean containsIgnoreCase(@Nullable String text, @Nullable String expected) {
        if (text == null || expected == null) return false;
        return text.toLowerCase(Locale.ROOT).contains(expected.toLowerCase(Locale.ROOT));
    }

    private static @Nullable String extractClassName(@NotNull String locationText) {
        int hashIndex = locationText.indexOf('#');
        if (hashIndex > 0) {
            return locationText.substring(0, hashIndex);
        }
        return locationText;
    }

    private static @Nullable String extractMethodName(@NotNull String locationText) {
        int hashIndex = locationText.indexOf('#');
        if (hashIndex >= 0 && hashIndex < locationText.length() - 1) {
            return locationText.substring(hashIndex + 1);
        }
        return null;
    }

    private static @Nullable String determineDimension(@NotNull SearchQuery query, @NotNull RestServiceItem item) {
        if (query.urlPattern() != null && scoreUrlMatch(query.urlPattern(), item.getUrl()) > 0) {
            return "url";
        }
        if (query.classNamePattern() != null && containsIgnoreCase(extractClassName(item.getLocationText()), query.classNamePattern())) {
            return "class";
        }
        if (query.methodNamePattern() != null && containsIgnoreCase(extractMethodName(item.getLocationText()), query.methodNamePattern())) {
            return "method-name";
        }
        if (query.methodFilter() != null && item.getMethod() == query.methodFilter()) {
            return "method";
        }
        return null;
    }
}
