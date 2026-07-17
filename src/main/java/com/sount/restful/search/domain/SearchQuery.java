package com.sount.restful.search.domain;

import com.sount.restful.method.HttpMethod;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

public record SearchQuery(
        String rawInput,
        @Nullable HttpMethod methodFilter,
        @Nullable String urlPattern,
        @Nullable String classNamePattern,
        @Nullable String methodNamePattern,
        @NotNull List<String> tokens
) {
    public static final SearchQuery EMPTY = new SearchQuery("", null, null, null, null, List.of());

    public static SearchQuery parse(@Nullable String input) {
        if (input == null || input.isBlank()) return EMPTY;

        String trimmed = input.trim();
        HttpMethod method = null;
        String remainder = trimmed;

        // 1. Check for method prefix: "GET /api/users", "POST /api/users"
        for (HttpMethod m : HttpMethod.values()) {
            String prefix = m.name() + " ";
            if (remainder.toUpperCase(Locale.ROOT).startsWith(prefix)) {
                method = m;
                remainder = remainder.substring(prefix.length()).trim();
                break;
            }
        }

        // 2. Extract path from full HTTP URL: "http://localhost:8080/api/users?id=1" → "/api/users"
        if (remainder.startsWith("http://") || remainder.startsWith("https://")) {
            remainder = extractUrlPath(remainder);
            if (remainder.isEmpty()) return EMPTY;
        }

        // 3. Check for class#method pattern: "UserController#getUser"
        if (remainder.contains("#")) {
            int lastHash = remainder.lastIndexOf('#');
            String classPart = remainder.substring(0, lastHash).trim();
            String methodPart = remainder.substring(lastHash + 1).trim();
            List<String> tokens = new ArrayList<>();
            tokens.addAll(tokenize(classPart));
            tokens.addAll(tokenize(methodPart));
            return new SearchQuery(trimmed, method,
                    null,
                    classPart.isEmpty() ? null : classPart,
                    methodPart.isEmpty() ? null : methodPart,
                    tokens);
        }

        // 3. Multi-token parsing
        String[] parts = remainder.split("\\s+");
        List<String> tokens = new ArrayList<>();
        for (String part : parts) {
            if (part.isBlank()) continue;
            // Check if token is an HTTP method
            HttpMethod tokenMethod = tryParseHttpMethod(part);
            if (tokenMethod != null && method == null) {
                method = tokenMethod;
            } else {
                tokens.add(part);
            }
        }

        String urlPattern = tokens.isEmpty() ? null : String.join(" ", tokens);
        return new SearchQuery(trimmed, method, urlPattern, null, null, tokens);
    }

    private static @Nullable HttpMethod tryParseHttpMethod(@NotNull String text) {
        String upper = text.toUpperCase(Locale.ROOT);
        for (HttpMethod m : HttpMethod.values()) {
            if (m.name().equals(upper)) {
                return m;
            }
        }
        return null;
    }

    private static @NotNull List<String> tokenize(@NotNull String text) {
        return Arrays.stream(text.split("\\s+"))
                .filter(s -> !s.isBlank())
                .collect(Collectors.toList());
    }

    /**
     * Extract path from a full HTTP URL, stripping scheme, host, port, query, and fragment.
     * {@code "http://localhost:8080/api/users?id=1#list"} → {@code "/api/users"}
     */
    static @NotNull String extractUrlPath(@NotNull String url) {
        int start = url.indexOf("://");
        if (start < 0) return url;
        start += 3;

        int pathStart = url.indexOf('/', start);
        if (pathStart < 0) return "/";

        int pathEnd = url.length();
        int queryStart = url.indexOf('?', pathStart);
        if (queryStart >= 0) pathEnd = queryStart;
        int fragmentStart = url.indexOf('#', pathStart);
        if (fragmentStart >= 0 && fragmentStart < pathEnd) pathEnd = fragmentStart;

        return url.substring(pathStart, pathEnd);
    }

    public boolean isEmpty() {
        return rawInput.isEmpty() && methodFilter == null && urlPattern == null
                && classNamePattern == null && methodNamePattern == null && tokens.isEmpty();
    }
}
