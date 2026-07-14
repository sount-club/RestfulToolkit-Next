package com.sount.restful.search;

import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/** Project-level options used to normalize request paths before endpoint matching. */
public record PathSearchOptions(@NotNull List<String> gatewayPrefixes) {

    public static final String GATEWAY_PREFIXES_KEY = "RestfulToolkit.Search.GatewayPrefixes";
    public static final PathSearchOptions EMPTY = new PathSearchOptions(List.of());

    public PathSearchOptions {
        gatewayPrefixes = List.copyOf(gatewayPrefixes);
    }

    public static @NotNull PathSearchOptions forProject(@NotNull Project project) {
        return parse(PropertiesComponent.getInstance(project).getValue(GATEWAY_PREFIXES_KEY, ""));
    }

    /** Parses comma or line separated prefixes such as {@code /gateway, /api}. */
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
