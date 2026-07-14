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
        for (String prefix : gatewayPrefixes) {
            String stripped = stripPrefix(normalizedRaw, prefix);
            if (stripped != null) {
                paths.add(stripped);
                afterGateway.add(stripped);
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
        int templateIndex = 0;
        int requestIndex = 0;
        while (templateIndex < templateSegments.size()) {
            String templateSegment = templateSegments.get(templateIndex);
            if ("**".equals(templateSegment) || isMultiSegmentVariable(templateSegment)) {
                return true;
            }
            if (requestIndex >= requestSegments.size()) {
                return false;
            }
            if (!matchesSegment(templateSegment, requestSegments.get(requestIndex))) {
                return false;
            }
            templateIndex++;
            requestIndex++;
        }
        return requestIndex == requestSegments.size();
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
