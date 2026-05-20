package com.sount.restful.search;

import com.sount.restful.method.HttpMethod;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;

public record SearchQuery(
        String rawInput,
        @Nullable HttpMethod methodFilter,
        @Nullable String urlPattern,
        @Nullable String classNamePattern,
        @Nullable String methodNamePattern
) {
    public static final SearchQuery EMPTY = new SearchQuery("", null, null, null, null);

    public static SearchQuery parse(@Nullable String input) {
        if (input == null || input.isBlank()) return EMPTY;

        String trimmed = input.trim();
        HttpMethod method = null;
        String remainder = trimmed;

        // Check for method prefix: "GET /api/users", "POST /api/users"
        for (HttpMethod m : HttpMethod.values()) {
            String prefix = m.name() + " ";
            if (remainder.toUpperCase(Locale.ROOT).startsWith(prefix)) {
                method = m;
                remainder = remainder.substring(prefix.length()).trim();
                break;
            }
        }

        // Check for class#method pattern: "UserController#getUser"
        if (remainder.contains("#")) {
            int lastHash = remainder.lastIndexOf('#');
            String classPart = remainder.substring(0, lastHash).trim();
            String methodPart = remainder.substring(lastHash + 1).trim();
            return new SearchQuery(trimmed, method,
                    null,
                    classPart.isEmpty() ? null : classPart,
                    methodPart.isEmpty() ? null : methodPart);
        }

        // Otherwise treat as URL/text pattern
        String urlPattern = remainder.isEmpty() ? null : remainder;
        return new SearchQuery(trimmed, method, urlPattern, null, null);
    }

    public boolean isEmpty() {
        return rawInput.isEmpty() && methodFilter == null && urlPattern == null
                && classNamePattern == null && methodNamePattern == null;
    }
}
