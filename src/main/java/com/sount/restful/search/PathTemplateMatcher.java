package com.sount.restful.search;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;

/** Matches real request paths against Spring-style endpoint templates. */
final class PathTemplateMatcher {

    private PathTemplateMatcher() {
    }

    static @NotNull List<String> candidates(@NotNull String rawPath, @NotNull String contextPath,
                                             @NotNull List<String> gatewayPrefixes) {
        LinkedHashSet<String> paths = new LinkedHashSet<>();
        String normalizedRaw = normalizePath(rawPath);
        if (normalizedRaw.isEmpty()) {
            return List.of();
        }
        paths.add(normalizedRaw); // Always retain the unmodified path as a fallback.

        List<String> afterGateway = new ArrayList<>();
        afterGateway.add(normalizedRaw);
        for (int candidateIndex = 0; candidateIndex < afterGateway.size(); candidateIndex++) {
            String candidate = afterGateway.get(candidateIndex);
            // Apply every configured prefix to each newly discovered path. This makes
            // layered prefixes independent of the order in which users entered them.
            for (String prefix : gatewayPrefixes) {
                String stripped = stripPrefix(candidate, prefix);
                if (stripped != null && paths.add(stripped)) {
                    afterGateway.add(stripped);
                }
            }
        }

        for (String path : afterGateway) {
            String strippedContext = stripPrefix(path, contextPath);
            if (strippedContext != null) {
                paths.add(strippedContext);
            }
        }
        return List.copyOf(paths);
    }

    static boolean matches(@NotNull String template, @NotNull String requestPath) {
        List<String> templateSegments = segments(template);
        List<String> requestSegments = segments(requestPath);
        return matchesSegments(templateSegments, 0, requestSegments, 0);
    }

    private static boolean matchesSegments(@NotNull List<String> templateSegments, int templateIndex,
                                           @NotNull List<String> requestSegments, int requestIndex) {
        if (templateIndex == templateSegments.size()) {
            return requestIndex == requestSegments.size();
        }

        String templateSegment = templateSegments.get(templateIndex);
        if ("**".equals(templateSegment) || isMultiSegmentVariable(templateSegment)) {
            // A multi-segment wildcard can consume zero or more path segments, but any
            // template segments after it must still match.
            for (int nextRequestIndex = requestIndex; nextRequestIndex <= requestSegments.size(); nextRequestIndex++) {
                if (matchesSegments(templateSegments, templateIndex + 1, requestSegments, nextRequestIndex)) {
                    return true;
                }
            }
            return false;
        }

        return requestIndex < requestSegments.size()
                && matchesSegment(templateSegment, requestSegments.get(requestIndex))
                && matchesSegments(templateSegments, templateIndex + 1, requestSegments, requestIndex + 1);
    }

    static @NotNull String normalizePath(String path) {
        if (path == null || path.isBlank()) {
            return "";
        }
        String normalized = path.trim().replaceAll("/{2,}", "/");
        if (!normalized.startsWith("/")) {
            normalized = "/" + normalized;
        }
        return normalized.length() > 1 && normalized.endsWith("/")
                ? normalized.substring(0, normalized.length() - 1) : normalized;
    }

    private static boolean matchesSegment(@NotNull String templateSegment, @NotNull String requestSegment) {
        return "*".equals(templateSegment)
                || isSingleSegmentVariable(templateSegment)
                || templateSegment.equalsIgnoreCase(requestSegment);
    }

    private static boolean isSingleSegmentVariable(@NotNull String value) {
        return value.length() > 2 && value.startsWith("{") && value.endsWith("}") && !value.startsWith("{*");
    }

    private static boolean isMultiSegmentVariable(@NotNull String value) {
        return value.length() > 3 && value.startsWith("{*") && value.endsWith("}");
    }

    private static List<String> segments(@NotNull String path) {
        String normalized = normalizePath(path);
        if ("/".equals(normalized)) {
            return List.of();
        }
        String[] split = normalized.substring(1).split("/");
        List<String> result = new ArrayList<>(split.length);
        for (String segment : split) {
            if (!segment.isEmpty()) {
                result.add(segment.toLowerCase(Locale.ROOT));
            }
        }
        return result;
    }

    private static String stripPrefix(@NotNull String path, String prefix) {
        String normalizedPrefix = normalizePath(prefix);
        if (normalizedPrefix.isEmpty() || "/".equals(normalizedPrefix)) {
            return null;
        }
        if (path.equals(normalizedPrefix)) {
            return "/";
        }
        if (path.startsWith(normalizedPrefix + "/")) {
            return path.substring(normalizedPrefix.length());
        }
        return null;
    }
}
