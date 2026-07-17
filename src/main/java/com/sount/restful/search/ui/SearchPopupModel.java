package com.sount.restful.search.ui;

import com.intellij.openapi.module.Module;
import com.sount.restful.endpoint.navigation.RestServiceItem;
import com.sount.restful.method.HttpMethod;
import com.sount.restful.search.domain.SearchResult;
import com.sount.restful.utils.RestfulToolkitBundle;
import com.sount.restful.utils.RestfulToolkitBundle.Keys;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public final class SearchPopupModel {

    private SearchPopupModel() {
    }

    public static @NotNull String resolveInitialSearchText(@Nullable String initialText, @NotNull List<String> recentQueries) {
        if (initialText != null && !initialText.isBlank()) {
            return initialText;
        }
        return recentQueries.isEmpty() ? "" : recentQueries.get(0);
    }

    public static @NotNull String buildStatusText(@NotNull String text, int resultCount, int totalCount, boolean indexReady) {
        return buildStatusText(text, resultCount, totalCount, indexReady, null, null);
    }

    public static @NotNull String buildStatusText(@NotNull String text, int resultCount, int totalCount, boolean indexReady,
                                                   @Nullable Module filterModule, @Nullable HttpMethod methodFilter) {
        if (!indexReady && resultCount == 0) {
            if (text.isEmpty()) {
                return RestfulToolkitBundle.message(Keys.SEARCH_POPUP_STATUS_INDEXING);
            }
            return RestfulToolkitBundle.message(Keys.SEARCH_POPUP_STATUS_INDEXING_QUERY, text);
        }

        if (text.isEmpty()) {
            if (totalCount == 0) {
                return RestfulToolkitBundle.message(Keys.SEARCH_POPUP_STATUS_NO_ENDPOINTS);
            }
            return RestfulToolkitBundle.message(Keys.SEARCH_POPUP_STATUS_ENDPOINTS_LOADED, totalCount);
        }

        if (resultCount == 0) {
            StringBuilder sb = new StringBuilder();
            sb.append(RestfulToolkitBundle.message(Keys.SEARCH_POPUP_STATUS_NO_RESULTS, text));
            if (methodFilter != null) {
                sb.append(" [").append(RestfulToolkitBundle.message(Keys.SEARCH_POPUP_STATUS_METHOD)).append(": ")
                        .append(methodFilter.name()).append("]");
            }
            if (filterModule != null) {
                sb.append(" · ").append(RestfulToolkitBundle.message(Keys.SEARCH_POPUP_STATUS_MODULE)).append(": ")
                        .append(filterModule.getName());
            }
            sb.append(RestfulToolkitBundle.message(Keys.SEARCH_POPUP_STATUS_NO_RESULTS_HINT));
            return sb.toString();
        }

        StringBuilder status = new StringBuilder();
        status.append(RestfulToolkitBundle.message(Keys.SEARCH_POPUP_STATUS_RESULTS_FOUND, resultCount));
        if (methodFilter != null) {
            status.append(" [").append(methodFilter.name()).append("]");
        }
        if (filterModule != null) {
            status.append(" ").append(RestfulToolkitBundle.message(Keys.SEARCH_POPUP_STATUS_IN_MODULE, filterModule.getName()));
        }
        return status.toString();
    }

    public static int findSelectionIndex(@NotNull List<SearchResult<RestServiceItem>> results,
                                         @Nullable String preferredEndpointKey) {
        return findSelectionIndex(results, preferredEndpointKey, null);
    }

    public static int findSelectionIndex(@NotNull List<SearchResult<RestServiceItem>> results,
                                         @Nullable String preferredEndpointKey,
                                         @Nullable Integer preferredSelectionIndex) {
        if (preferredSelectionIndex != null
                && preferredSelectionIndex >= 0
                && preferredSelectionIndex < results.size()) {
            return preferredSelectionIndex;
        }
        if (preferredEndpointKey != null && !preferredEndpointKey.isBlank()) {
            for (int i = 0; i < results.size(); i++) {
                if (preferredEndpointKey.equals(results.get(i).item().getSearchSelectionKey())) {
                    return i;
                }
            }
            for (int i = 0; i < results.size(); i++) {
                if (preferredEndpointKey.equals(results.get(i).item().getEndpointKey())) {
                    return i;
                }
            }
        }
        return results.isEmpty() ? -1 : 0;
    }
}
