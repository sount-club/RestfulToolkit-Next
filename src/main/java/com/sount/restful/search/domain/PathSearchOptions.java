package com.sount.restful.search.domain;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * 路径匹配前的不可变规范化选项。
 */
public record PathSearchOptions(@NotNull List<String> gatewayPrefixes) {

    public static final PathSearchOptions EMPTY = new PathSearchOptions(List.of());

    /**
     * 防御性复制网关前缀，保证跨线程搜索期间选项不发生变化。
     */
    public PathSearchOptions {
        gatewayPrefixes = List.copyOf(gatewayPrefixes);
    }

    /**
     * 解析逗号或换行分隔的网关前缀，例如 {@code /gateway, /api}。
     */
    public static @NotNull PathSearchOptions parse(String value) {
        if (value == null || value.isBlank()) {
            return EMPTY;
        }
        LinkedHashSet<String> prefixes = new LinkedHashSet<>();
        for (String part : value.split("[,\\n\\r]+")) {
            String prefix = PathTemplateMatcher.normalizePath(part);
            if (!prefix.isEmpty() && !"/".equals(prefix)) {
                prefixes.add(prefix);
            }
        }
        return new PathSearchOptions(new ArrayList<>(prefixes));
    }
}
