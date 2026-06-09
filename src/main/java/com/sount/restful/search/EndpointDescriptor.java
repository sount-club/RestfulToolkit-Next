package com.sount.restful.search;

import com.sount.restful.method.HttpMethod;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;

public record EndpointDescriptor(
        @Nullable HttpMethod method,
        @Nullable String requestMethod,
        @Nullable String url,
        @Nullable String controllerName,
        @Nullable String methodName,
        @Nullable String packageName,
        @Nullable String description,
        @Nullable String moduleName
) {

    public @NotNull String methodText() {
        if (method != null) {
            return method.name();
        }
        return requestMethod != null ? requestMethod : "";
    }

    public @NotNull String locationText() {
        String controller = normalize(controllerName);
        String endpointMethod = normalize(methodName);
        if (controller.isEmpty() && endpointMethod.isEmpty()) {
            return "";
        }
        return controller + "#" + endpointMethod;
    }

    public @NotNull String endpointKey() {
        String methodPart = methodText().isEmpty() ? "UNKNOWN" : methodText();
        return methodPart + ":" + normalize(url);
    }

    public @NotNull String searchSelectionKey() {
        return endpointKey() + ":" + locationText() + ":" + normalize(moduleName);
    }

    public @NotNull String searchableText() {
        StringBuilder sb = new StringBuilder(128);
        appendNonEmpty(sb, lowerMethodText());
        appendNonEmpty(sb, lowerUrl());
        appendNonEmpty(sb, lowerDescription());
        appendNonEmpty(sb, lowerControllerName());
        appendNonEmpty(sb, lowerMethodName());
        appendNonEmpty(sb, lowerModuleName());
        return sb.toString();
    }

    public @NotNull String lowerUrl() {
        return lower(url);
    }

    public @NotNull String lowerMethodName() {
        return lower(methodName);
    }

    public @NotNull String lowerModuleName() {
        return lower(moduleName);
    }

    public @NotNull String lowerControllerName() {
        return lower(controllerName);
    }

    public @NotNull String lowerDescription() {
        return lower(description);
    }

    public @NotNull String lowerMethodText() {
        return lower(methodText());
    }

    private static @NotNull String normalize(@Nullable String value) {
        return value != null ? value : "";
    }

    private static @NotNull String lower(@Nullable String value) {
        return value != null ? value.toLowerCase(Locale.ROOT) : "";
    }

    private static void appendNonEmpty(@NotNull StringBuilder sb, @Nullable String value) {
        if (value != null && !value.isEmpty()) {
            if (!sb.isEmpty()) sb.append(' ');
            sb.append(value);
        }
    }
}
