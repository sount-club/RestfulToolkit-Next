package com.sount.restful.search.application;

import com.intellij.openapi.module.Module;
import com.sount.restful.method.HttpMethod;
import com.sount.restful.search.domain.SearchQuery;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * 一次弹窗搜索所需的不可变输入快照。
 *
 * <p><b>线程约束：</b>实例在 EDT 创建后可传入后台线程；不得加入 Swing 组件或可变 PSI 状态。</p>
 * <p><b>行为约束：</b>查询文本中显式声明的 HTTP 方法优先于界面方法筛选器，
 * 与重构前的组合规则一致。</p>
 */
public record SearchRequest(
        @NotNull String text,
        @NotNull SearchQuery query,
        @Nullable Module filterModule,
        @Nullable HttpMethod methodFilter,
        int maxResults
) {
    /**
     * 对查询 token 做防御性复制，保证请求跨线程传递期间保持稳定。
     */
    public SearchRequest {
        query = new SearchQuery(query.rawInput(), query.methodFilter(), query.urlPattern(),
                query.classNamePattern(), query.methodNamePattern(), List.copyOf(query.tokens()));
    }

    /**
     * 解析查询文本并合并界面筛选器，生成可安全传递给后台搜索的请求。
     */
    public static @NotNull SearchRequest create(
            @NotNull String text,
            @Nullable Module filterModule,
            @Nullable HttpMethod methodFilter,
            int maxResults) {
        SearchQuery parsed = SearchQuery.parse(text);
        SearchQuery effectiveQuery = parsed;
        if (methodFilter != null && parsed.methodFilter() == null) {
            effectiveQuery = new SearchQuery(parsed.rawInput(), methodFilter, parsed.urlPattern(),
                    parsed.classNamePattern(), parsed.methodNamePattern(), parsed.tokens());
        }
        return new SearchRequest(text, effectiveQuery, filterModule, methodFilter, maxResults);
    }

    /**
     * 判断是否应使用最近访问端点模式；仅空文本且未选择方法筛选器时成立。
     */
    boolean isRecentMode() {
        return text.isEmpty() && methodFilter == null;
    }
}
