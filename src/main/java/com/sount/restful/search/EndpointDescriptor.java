package com.sount.restful.search;

import com.sount.restful.method.HttpMethod;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;

public final class EndpointDescriptor {
    private final @Nullable HttpMethod method;
    private final @Nullable String requestMethod;
    private final @Nullable String url;
    private final @Nullable String controllerName;
    private final @Nullable String methodName;
    private final @Nullable String packageName;
    private final @Nullable String description;
    private final @Nullable String moduleName;

    private final @NotNull String methodText;
    private final @NotNull String locationText;
    private final @NotNull String endpointKey;
    private final @NotNull String searchSelectionKey;
    private final @NotNull String lowerUrl;
    private final @NotNull String lowerMethodName;
    private final @NotNull String lowerModuleName;
    private final @NotNull String lowerControllerName;
    private final @NotNull String lowerDescription;
    private final @NotNull String lowerMethodText;
    private final @NotNull String searchableText;

    public EndpointDescriptor(@Nullable HttpMethod method,
                              @Nullable String requestMethod,
                              @Nullable String url,
                              @Nullable String controllerName,
                              @Nullable String methodName,
                              @Nullable String packageName,
                              @Nullable String description,
                              @Nullable String moduleName) {
        this.method = method;
        this.requestMethod = requestMethod;
        this.url = url;
        this.controllerName = controllerName;
        this.methodName = methodName;
        this.packageName = packageName;
        this.description = description;
        this.moduleName = moduleName;

        this.methodText = computeMethodText(method, requestMethod);
        this.locationText = computeLocationText(controllerName, methodName);
        this.endpointKey = computeEndpointKey(methodText, url);
        this.searchSelectionKey = endpointKey + ":" + locationText + ":" + normalize(moduleName);
        this.lowerUrl = lower(url);
        this.lowerMethodName = lower(methodName);
        this.lowerModuleName = lower(moduleName);
        this.lowerControllerName = lower(controllerName);
        this.lowerDescription = lower(description);
        this.lowerMethodText = lower(methodText);
        this.searchableText = computeSearchableText();
    }

    public @Nullable HttpMethod method() {
        return method;
    }

    public @Nullable String requestMethod() {
        return requestMethod;
    }

    public @Nullable String url() {
        return url;
    }

    public @Nullable String controllerName() {
        return controllerName;
    }

    public @Nullable String methodName() {
        return methodName;
    }

    public @Nullable String packageName() {
        return packageName;
    }

    public @Nullable String description() {
        return description;
    }

    public @Nullable String moduleName() {
        return moduleName;
    }

    public @NotNull String methodText() {
        return methodText;
    }

    public @NotNull String locationText() {
        return locationText;
    }

    public @NotNull String endpointKey() {
        return endpointKey;
    }

    public @NotNull String searchSelectionKey() {
        return searchSelectionKey;
    }

    public @NotNull String searchableText() {
        return searchableText;
    }

    public @NotNull String lowerUrl() {
        return lowerUrl;
    }

    public @NotNull String lowerMethodName() {
        return lowerMethodName;
    }

    public @NotNull String lowerModuleName() {
        return lowerModuleName;
    }

    public @NotNull String lowerControllerName() {
        return lowerControllerName;
    }

    public @NotNull String lowerDescription() {
        return lowerDescription;
    }

    public @NotNull String lowerMethodText() {
        return lowerMethodText;
    }

    private static @NotNull String computeMethodText(@Nullable HttpMethod method, @Nullable String requestMethod) {
        if (method != null) {
            return method.name();
        }
        return requestMethod != null ? requestMethod : "";
    }

    private static @NotNull String computeLocationText(@Nullable String controllerName, @Nullable String methodName) {
        String controller = normalize(controllerName);
        String endpointMethod = normalize(methodName);
        if (controller.isEmpty() && endpointMethod.isEmpty()) {
            return "";
        }
        return controller + "#" + endpointMethod;
    }

    private static @NotNull String computeEndpointKey(@NotNull String methodText, @Nullable String url) {
        String methodPart = methodText.isEmpty() ? "UNKNOWN" : methodText;
        return methodPart + ":" + normalize(url);
    }

    private @NotNull String computeSearchableText() {
        StringBuilder sb = new StringBuilder(128);
        appendNonEmpty(sb, lowerMethodText);
        appendNonEmpty(sb, lowerUrl);
        appendNonEmpty(sb, lowerDescription);
        appendNonEmpty(sb, lowerControllerName);
        appendNonEmpty(sb, lowerMethodName);
        appendNonEmpty(sb, lowerModuleName);
        return sb.toString();
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
