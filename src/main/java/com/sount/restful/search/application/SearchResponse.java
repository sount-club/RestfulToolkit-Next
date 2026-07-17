package com.sount.restful.search.application;

import com.sount.restful.endpoint.navigation.RestServiceItem;
import com.sount.restful.search.domain.SearchResult;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * 后台搜索计算产生的不可变响应快照。
 *
 * <p><b>线程约束：</b>后台线程创建，EDT 消费；构造时复制集合以阻止跨线程修改。</p>
 */
public record SearchResponse(
        @NotNull List<SearchResult<RestServiceItem>> results,
        int totalCount,
        boolean indexReady,
        @NotNull List<String> highlightTokens
) {
    /**
     * 防御性复制结果和高亮 token，隔离后台生产者与 EDT 消费者。
     */
    public SearchResponse {
        results = List.copyOf(results);
        highlightTokens = List.copyOf(highlightTokens);
    }
}
