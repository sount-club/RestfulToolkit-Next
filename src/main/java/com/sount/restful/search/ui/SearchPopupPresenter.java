package com.sount.restful.search.ui;

import com.intellij.ui.components.JBList;
import com.sount.restful.endpoint.navigation.RestServiceItem;
import com.sount.restful.search.application.EndpointIndex;
import com.sount.restful.search.application.EndpointSearchService;
import com.sount.restful.search.application.SearchRequest;
import com.sount.restful.search.application.SearchResponse;
import com.sount.restful.search.domain.SearchResult;
import com.sount.restful.utils.RestfulToolkitBundle;
import com.sount.restful.utils.RestfulToolkitBundle.Keys;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * 搜索弹窗 Presenter：把应用层响应映射为 Swing 视图状态。
 */
final class SearchPopupPresenter {
    private SearchPopupPresenter() {
    }

    /**
     * 发起搜索，并在最新响应到达后统一更新结果、状态、选择和滚动位置。
     */
    static void performSearch(@NotNull SearchRequest request,
                              @NotNull SearchSelectionSnapshot selectionSnapshot,
                              @NotNull EndpointIndex index,
                              @NotNull SearchView view,
                              @NotNull EndpointSearchService.UpdateGuard updateGuard,
                              @Nullable Runnable resultsApplied) {
        EndpointSearchService.search(request, index, updateGuard,
                response -> applyResponse(request, selectionSnapshot, response, view, resultsApplied),
                error -> view.statusLabel().setText(
                        RestfulToolkitBundle.message(Keys.SEARCH_POPUP_STATUS_SEARCH_FAILED)));
    }

    /**
     * 在 EDT 将响应一次性应用到 renderer、列表模型、选择状态和状态栏。
     *
     * <p><b>行为约束：</b>先填充模型并恢复选择，再执行回调，保证排队导航可读取选中项。</p>
     */
    static void applyResponse(@NotNull SearchRequest request,
                              @NotNull SearchSelectionSnapshot selectionSnapshot,
                              @NotNull SearchResponse response,
                              @NotNull SearchView view,
                              @Nullable Runnable resultsApplied) {
        view.renderer().setHighlightTokens(response.highlightTokens());
        view.model().clear();
        for (SearchResult<RestServiceItem> result : response.results()) {
            view.model().addElement(result);
        }
        if (!response.results().isEmpty()) {
            int selectionIndex = SearchPopupModel.findSelectionIndex(response.results(),
                    selectionSnapshot.preferredEndpointKey(), selectionSnapshot.preferredSelectionIndex());
            SearchPopupActions.selectAndRevealIndex(view.resultList(), selectionIndex,
                    selectionSnapshot.preferredFirstVisibleIndex(), selectionSnapshot.preferredScrollY());
        }

        view.statusLabel().setText(SearchPopupModel.buildStatusText(request.text(), response.results().size(),
                response.totalCount(), response.indexReady(), request.filterModule(), request.methodFilter()));
        view.searchAllModulesButton().setVisible(response.results().isEmpty()
                && request.filterModule() != null && !request.text().isEmpty());
        if (resultsApplied != null) {
            resultsApplied.run();
        }
    }

    /**
     * 聚合 Presenter 应用响应所需的 Swing 组件，避免应用请求携带界面对象。
     */
    record SearchView(
            @NotNull DefaultListModel<SearchResult<RestServiceItem>> model,
            @NotNull JBList<SearchResult<RestServiceItem>> resultList,
            @NotNull JLabel statusLabel,
            @NotNull JButton searchAllModulesButton,
            @NotNull UnifiedSearchRenderer renderer
    ) {
    }
}
