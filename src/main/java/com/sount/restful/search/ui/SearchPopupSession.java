package com.sount.restful.search.ui;

import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.ui.components.JBList;
import com.intellij.util.Alarm;
import com.sount.restful.endpoint.navigation.RestServiceItem;
import com.sount.restful.search.application.EndpointIndex;
import com.sount.restful.search.application.EndpointSearchService;
import com.sount.restful.search.application.SearchHistory;
import com.sount.restful.search.domain.SearchResult;
import com.sount.restful.utils.RestfulToolkitBundle;
import com.sount.restful.utils.RestfulToolkitBundle.Keys;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * 管理一次搜索弹窗的可变会话状态和资源释放。
 *
 * <p><b>线程约束：</b>所有方法均由 EDT 调用，字段不承担跨线程同步职责；后台搜索结果
 * 通过 {@link EndpointSearchService} 回到 EDT 后再触发本会话。</p>
 * <p><b>生命周期约束：</b>弹窗关闭时必须调用 {@link #dispose()}，统一取消延迟请求、
 * 移除索引监听并持久化外部状态。</p>
 */
final class SearchPopupSession {
    private final EndpointIndex index;
    private final SearchHistory history;
    private final JBList<SearchResult<RestServiceItem>> resultList;
    private final JLabel statusLabel;
    private final Runnable disposeAction;
    private final Alarm searchAlarm = new Alarm(Alarm.ThreadToUse.SWING_THREAD);
    private final EndpointSearchService.UpdateGuard updateGuard = new EndpointSearchService.UpdateGuard();

    private @Nullable JBPopup popup;
    private @Nullable Runnable indexListener;
    private boolean navigateWhenResultsArrive;
    private boolean disposed;

    /**
     * 创建会话并捕获导航、状态提示与关闭持久化所需的稳定依赖。
     */
    SearchPopupSession(@NotNull EndpointIndex index,
                       @NotNull SearchHistory history,
                       @NotNull JBList<SearchResult<RestServiceItem>> resultList,
                       @NotNull JLabel statusLabel,
                       @NotNull Runnable disposeAction) {
        this.index = index;
        this.history = history;
        this.resultList = resultList;
        this.statusLabel = statusLabel;
        this.disposeAction = disposeAction;
    }

    @NotNull EndpointSearchService.UpdateGuard updateGuard() {
        return updateGuard;
    }

    /**
     * 绑定当前会话创建的 popup，供导航关闭和配置入口取消使用。
     */
    void attachPopup(@NotNull JBPopup popup) {
        this.popup = popup;
    }

    /**
     * 注册本会话的端点索引监听器，并在 {@link #dispose()} 中对称移除。
     */
    void attachIndexListener(@NotNull Runnable listener) {
        indexListener = listener;
        index.addListener(listener);
    }

    /**
     * 取消尚未执行的搜索防抖任务，并按指定延迟调度最新任务。
     */
    void scheduleSearch(@NotNull Runnable search, int delayMillis) {
        searchAlarm.cancelAllRequests();
        searchAlarm.addRequest(search, delayMillis);
    }

    /**
     * 尝试导航当前选中结果；结果尚未产生时记录待导航状态并触发索引自恢复。
     */
    void navigateOrQueue() {
        JBPopup activePopup = popup;
        if (activePopup == null) {
            return;
        }
        SearchPopupActions.NavigationResult result = SearchPopupActions.navigateToSelected(
                resultList, activePopup, history, index, statusLabel);
        if (result == SearchPopupActions.NavigationResult.NAVIGATED) {
            return;
        }

        navigateWhenResultsArrive = true;
        if (result == SearchPopupActions.NavigationResult.NO_SELECTION) {
            index.ensureRebuildScheduled();
            statusLabel.setText(index.isReady()
                    ? RestfulToolkitBundle.message(Keys.SEARCH_POPUP_STATUS_WAITING_RESULTS)
                    : RestfulToolkitBundle.message(Keys.SEARCH_POPUP_STATUS_INDEXING));
        }
    }

    /**
     * 在最新搜索结果完成选择恢复后执行排队导航。
     *
     * <p><b>行为约束：</b>有选中项时只执行一次；索引已就绪但仍无结果时清除等待标记。</p>
     */
    void navigatePendingSelection() {
        if (!navigateWhenResultsArrive) {
            return;
        }
        JBPopup activePopup = popup;
        if (activePopup != null && resultList.getSelectedValue() != null) {
            navigateWhenResultsArrive = false;
            SearchPopupActions.navigateToSelected(resultList, activePopup, history, index, statusLabel);
        } else if (index.isReady()) {
            navigateWhenResultsArrive = false;
        }
    }

    /**
     * 取消当前 popup；关闭监听仍负责执行统一的会话释放流程。
     */
    void cancelPopup() {
        if (popup != null) {
            popup.cancel();
        }
    }

    /**
     * 幂等释放弹窗会话资源，并执行一次外部窗口状态持久化。
     */
    void dispose() {
        if (disposed) {
            return;
        }
        disposed = true;
        searchAlarm.cancelAllRequests();
        if (indexListener != null) {
            index.removeListener(indexListener);
            indexListener = null;
        }
        disposeAction.run();
    }
}
