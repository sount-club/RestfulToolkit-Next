package com.sount.restful.utils;

import com.intellij.DynamicBundle;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.PropertyKey;

/**
 * @author zhaow
 */
public class RestfulToolkitBundle extends DynamicBundle {

    @NonNls
    private static final String BUNDLE = "RestfulToolkitBundle";
    private static final RestfulToolkitBundle INSTANCE = new RestfulToolkitBundle();

    private RestfulToolkitBundle() {
        super(BUNDLE);
    }

    public static @Nls String message(@PropertyKey(resourceBundle = BUNDLE) String key, Object... params) {
        return INSTANCE.getMessage(key, params);
    }

    public static final class Keys {
        private Keys() {
        }

        @NonNls
        public static final String SEARCH_POPUP_TITLE = "search.popup.title";
        @NonNls
        public static final String SEARCH_POPUP_SEARCH_LABEL = "search.popup.search.label";
        @NonNls
        public static final String SEARCH_POPUP_FILTER_ALL = "search.popup.filter.all";
        @NonNls
        public static final String SEARCH_POPUP_MODULE_LABEL = "search.popup.module.label";
        @NonNls
        public static final String SEARCH_POPUP_MODULE_ALL = "search.popup.module.all";
        @NonNls
        public static final String SEARCH_POPUP_MODULE_CURRENT = "search.popup.module.current";
        @NonNls
        public static final String SEARCH_POPUP_KEYBOARD_HINT = "search.popup.keyboard.hint";
        @NonNls
        public static final String SEARCH_POPUP_SEARCH_ALL_MODULES = "search.popup.search.all.modules";
        @NonNls
        public static final String SEARCH_POPUP_STATUS_INDEXING = "search.popup.status.indexing";
        @NonNls
        public static final String SEARCH_POPUP_STATUS_INDEXING_QUERY = "search.popup.status.indexing.query";
        @NonNls
        public static final String SEARCH_POPUP_STATUS_NO_ENDPOINTS = "search.popup.status.no.endpoints";
        @NonNls
        public static final String SEARCH_POPUP_STATUS_ENDPOINTS_LOADED = "search.popup.status.endpoints.loaded";
        @NonNls
        public static final String SEARCH_POPUP_STATUS_NO_RESULTS = "search.popup.status.no.results";
        @NonNls
        public static final String SEARCH_POPUP_STATUS_NO_RESULTS_HINT = "search.popup.status.no.results.hint";
        @NonNls
        public static final String SEARCH_POPUP_STATUS_METHOD = "search.popup.status.method";
        @NonNls
        public static final String SEARCH_POPUP_STATUS_MODULE = "search.popup.status.module";
        @NonNls
        public static final String SEARCH_POPUP_STATUS_RESULTS_FOUND = "search.popup.status.results.found";
        @NonNls
        public static final String SEARCH_POPUP_STATUS_IN_MODULE = "search.popup.status.in.module";
        @NonNls
        public static final String SEARCH_POPUP_STATUS_WAITING_RESULTS = "search.popup.status.waiting.results";
        @NonNls
        public static final String SEARCH_POPUP_STATUS_NAVIGATION_FAILED = "search.popup.status.navigation.failed";
        @NonNls
        public static final String SEARCH_POPUP_STATUS_SEARCH_FAILED = "search.popup.status.search.failed";
        @NonNls
        public static final String SEARCH_POPUP_SELECTION_CURRENT = "search.popup.selection.current";

        @NonNls
        public static final String SEARCH_PREVIEW_SELECT = "search.preview.select";
        @NonNls
        public static final String SEARCH_PREVIEW_METHOD_CODE = "search.preview.method.code";
        @NonNls
        public static final String SEARCH_PREVIEW_DESCRIPTION = "search.preview.description";
        @NonNls
        public static final String SEARCH_PREVIEW_NO_DESCRIPTION = "search.preview.no.description";
        @NonNls
        public static final String SEARCH_PREVIEW_NO_METHOD_CODE = "search.preview.no.method.code";
        @NonNls
        public static final String SEARCH_PREVIEW_LOADING_METHOD_CODE = "search.preview.loading.method.code";
        @NonNls
        public static final String SEARCH_PREVIEW_SELECT_ENDPOINT = "search.preview.select.endpoint";

        @NonNls
        public static final String SEARCH_RENDERER_BEST_MATCH = "search.renderer.best.match";

        @NonNls
        public static final String ACTION_COPY_SUCCESS = "action.copy.success";
    }
}
