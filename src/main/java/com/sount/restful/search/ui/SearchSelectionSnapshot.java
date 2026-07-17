package com.sount.restful.search.ui;

import com.sount.restful.search.application.SearchHistory;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * 搜索结果列表的不可变 UI 恢复快照。
 *
 * <p>该值对象只描述选中项和滚动位置，不参与搜索条件或评分计算。</p>
 */
record SearchSelectionSnapshot(
        @Nullable String preferredEndpointKey,
        @Nullable Integer preferredSelectionIndex,
        @Nullable Integer preferredFirstVisibleIndex,
        @Nullable Integer preferredScrollY
) {
    /**
     * 从搜索历史中读取指定查询的选择与滚动状态。
     */
    static @NotNull SearchSelectionSnapshot restore(@NotNull SearchHistory history, @NotNull String query) {
        return new SearchSelectionSnapshot(
                history.getSelectedEndpointKey(query),
                history.getSelectedIndex(query),
                history.getFirstVisibleIndex(query),
                history.getScrollY(query));
    }
}
