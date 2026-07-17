package com.sount.restful.search.domain;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/** Matches real request paths against Spring-style endpoint templates. */
final class PathTemplateMatcher {

    private static final Pattern DUPLICATE_SLASHES = Pattern.compile("/{2,}");

    private PathTemplateMatcher() {
    }

    /**
     * 为真实请求路径生成按优先级排列的匹配候选。
     *
     * <p><b>行为约束：</b>原始路径始终位于首位；gateway 前缀可按任意配置顺序逐层剥离；
     * context-path 只在 gateway 候选生成完成后剥离；所有结果保持首次出现顺序并去重。</p>
     *
     * @param rawPath 用户输入的真实请求路径
     * @param contextPath 当前端点构建期缓存的 context-path
     * @param gatewayPrefixes 项目配置的 gateway 前缀
     * @return 不可变且按匹配优先级排列的候选路径
     */
    static @NotNull List<String> candidates(@NotNull String rawPath, @NotNull String contextPath,
                                             @NotNull List<String> gatewayPrefixes) {
        LinkedHashSet<String> paths = new LinkedHashSet<>();
        String normalizedRaw = normalizePath(rawPath);
        if (normalizedRaw.isEmpty()) {
            return List.of();
        }
        paths.add(normalizedRaw); // Always retain the unmodified path as a fallback.

        List<String> afterGateway = expandGatewayCandidates(normalizedRaw, gatewayPrefixes, paths);
        appendContextCandidates(afterGateway, contextPath, paths);
        return List.copyOf(paths);
    }

    /**
     * 逐层展开可剥离 gateway 前缀的路径，且允许配置前缀以任意顺序组合。
     */
    private static @NotNull List<String> expandGatewayCandidates(@NotNull String normalizedRaw,
                                                                  @NotNull List<String> gatewayPrefixes,
                                                                  @NotNull LinkedHashSet<String> paths) {
        List<String> afterGateway = new ArrayList<>();
        afterGateway.add(normalizedRaw);
        for (int candidateIndex = 0; candidateIndex < afterGateway.size(); candidateIndex++) {
            String candidate = afterGateway.get(candidateIndex);
            for (String prefix : gatewayPrefixes) {
                String stripped = stripPrefix(candidate, prefix);
                if (stripped != null && paths.add(stripped)) {
                    afterGateway.add(stripped);
                }
            }
        }
        return afterGateway;
    }

    /**
     * 在 gateway 候选全部生成后追加剥离 context-path 的候选，保持旧优先级顺序。
     */
    private static void appendContextCandidates(@NotNull List<String> gatewayCandidates,
                                                @NotNull String contextPath,
                                                @NotNull LinkedHashSet<String> paths) {
        for (String path : gatewayCandidates) {
            String strippedContext = stripPrefix(path, contextPath);
            if (strippedContext != null) {
                paths.add(strippedContext);
            }
        }
    }

    /**
     * 判断真实请求路径是否满足 Spring 风格的端点模板。
     *
     * <p><b>行为约束：</b>{@code *} 和普通路径变量只匹配一个 segment，
     * {@code **} 和 {@code {*path}} 可匹配零个或多个 segment，普通文本忽略大小写。</p>
     */
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
        String normalized = DUPLICATE_SLASHES.matcher(path.trim()).replaceAll("/");
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
