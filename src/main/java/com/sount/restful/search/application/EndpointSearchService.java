package com.sount.restful.search.application;

import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.sount.restful.endpoint.navigation.RestServiceItem;
import com.sount.restful.search.domain.PathSearchOptions;
import com.sount.restful.search.domain.SearchDocument;
import com.sount.restful.search.domain.SearchEngine;
import com.sount.restful.search.domain.SearchResult;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.PriorityQueue;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.ToLongFunction;

/**
 * 搜索应用服务：负责捕获端点快照、后台评分和最新请求校验。
 *
 * <p>该服务不持有 Swing 组件，也不决定界面如何渲染结果；界面更新由 Presenter 完成。</p>
 */
public final class EndpointSearchService {
    private static final Logger LOG = Logger.getInstance(EndpointSearchService.class);
    private static final int RECENT_RESULT_LIMIT = 20;

    private EndpointSearchService() {
    }

    /**
     * 调度一次异步搜索，并仅把最新 generation 的响应交给调用方。
     *
     * <p><b>线程约束：</b>请求在 EDT 创建，评分在后台线程执行，成功与失败回调均在 EDT 执行。</p>
     */
    public static void search(@NotNull SearchRequest request,
                              @NotNull EndpointIndex index,
                              @NotNull UpdateGuard updateGuard,
                              @NotNull Consumer<SearchResponse> responseConsumer,
                              @NotNull Consumer<Throwable> errorConsumer) {
        long updateGeneration = updateGuard.nextGeneration();
        List<RestServiceItem> allItems = index.getItems();
        boolean indexReady = index.isReady();
        SearchHistory history = SearchHistory.getInstance(index.getProject());
        PathSearchOptions pathSearchOptions = PathSearchSettings.forProject(index.getProject());

        ReadAction.nonBlocking(() -> computeResponse(request, allItems, indexReady,
                        history::getLastAccessTime, history::getUseCount, pathSearchOptions))
                .expireWith(index.getProject())
                .finishOnUiThread(ModalityState.any(), response -> runIfLatest(updateGuard, updateGeneration,
                        () -> responseConsumer.accept(response)))
                .submit(AppExecutorUtil.getAppExecutorService())
                .onError(error -> {
                    if (index.getProject().isDisposed()) {
                        return;
                    }
                    LOG.warn("REST endpoint search failed for query: " + request.text(), error);
                    SwingUtilities.invokeLater(() -> runIfLatest(updateGuard, updateGeneration, () -> {
                        if (!index.getProject().isDisposed()) {
                            errorConsumer.accept(error);
                        }
                    }));
                });
    }

    /**
     * 根据不可变请求和端点快照计算搜索响应，不读取或更新任何 Swing 组件。
     *
     * <p><b>性能约束：</b>模块过滤先于评分执行；普通搜索保留 top-N 上限，空查询只构建最近访问的 20 项。</p>
     */
    static @NotNull SearchResponse computeResponse(
            @NotNull SearchRequest request,
            @NotNull List<RestServiceItem> allItems,
            boolean indexReady,
            @NotNull ToLongFunction<RestServiceItem> lastAccessLookup,
            @NotNull Function<RestServiceItem, Integer> useCountLookup,
            @NotNull PathSearchOptions pathSearchOptions) {
        List<RestServiceItem> searchableItems = filterByModule(allItems, request.filterModule());
        boolean recentMode = request.isRecentMode();
        long startTime = System.nanoTime();
        List<SearchResult<RestServiceItem>> results = recentMode
                ? buildRecentResults(searchableItems, lastAccessLookup)
                : SearchEngine.search(request.query(), toSearchDocuments(searchableItems), request.maxResults(),
                        useCountLookup, pathSearchOptions);
        if (!recentMode) {
            logSearchPerformance(request, searchableItems.size(), results.size(), startTime);
        }
        return new SearchResponse(results, searchableItems.size(), indexReady, request.query().tokens());
    }

    /**
     * 在 application 边界记录搜索耗时，使纯评分领域层不直接依赖 IntelliJ 日志 API。
     */
    private static void logSearchPerformance(@NotNull SearchRequest request,
                                             int itemCount,
                                             int resultCount,
                                             long startTime) {
        long elapsedMs = (System.nanoTime() - startTime) / 1_000_000;
        if (elapsedMs > 10 || LOG.isDebugEnabled()) {
            LOG.debug("Search '" + request.query().rawInput() + "': " + resultCount + "/" + itemCount
                    + " items in " + elapsedMs + "ms");
        }
    }

    /**
     * 从端点快照中选出最近访问的固定数量结果，避免对完整集合进行全量排序。
     */
    static @NotNull List<SearchResult<RestServiceItem>> buildRecentResults(
            @NotNull List<RestServiceItem> items,
            @NotNull ToLongFunction<RestServiceItem> lastAccessLookup) {
        PriorityQueue<RestServiceItem> topItems = new PriorityQueue<>(Comparator.comparingLong(lastAccessLookup));
        for (RestServiceItem item : items) {
            topItems.add(item);
            if (topItems.size() > RECENT_RESULT_LIMIT) {
                topItems.poll();
            }
        }

        List<RestServiceItem> sorted = new ArrayList<>(topItems);
        sorted.sort(Comparator.comparingLong(lastAccessLookup).reversed());
        return sorted.stream().map(item -> new SearchResult<>(toSearchDocument(item), 0, null)).toList();
    }

    /**
     * 在应用层边界把导航对象转换为纯搜索文档，隔离领域算法与 IntelliJ PSI/Module。
     *
     * <p><b>性能约束：</b>只复用索引阶段已构建的 descriptor 和 context-path，不重新读取 PSI。</p>
     */
    private static @NotNull List<SearchDocument<RestServiceItem>> toSearchDocuments(
            @NotNull List<RestServiceItem> items) {
        return items.stream().map(EndpointSearchService::toSearchDocument).toList();
    }

    /**
     * 为单个导航端点创建只读搜索文档，并保留原对象作为结果 payload。
     */
    private static @NotNull SearchDocument<RestServiceItem> toSearchDocument(@NotNull RestServiceItem item) {
        return new SearchDocument<>(item, item.getDescriptor(), item.getContextPath());
    }

    /**
     * 在评分前按模块过滤端点；未选择模块时复用原快照，避免无意义复制。
     */
    private static @NotNull List<RestServiceItem> filterByModule(
            @NotNull List<RestServiceItem> allItems,
            @Nullable Module filterModule) {
        if (filterModule == null) {
            return allItems;
        }
        List<RestServiceItem> filtered = new ArrayList<>();
        for (RestServiceItem item : allItems) {
            if (filterModule.equals(item.getModule())) {
                filtered.add(item);
            }
        }
        return filtered;
    }

    /**
     * 仅当 generation 仍为最新时执行响应回调，用于丢弃乱序完成的后台搜索。
     */
    static void runIfLatest(@NotNull UpdateGuard updateGuard, long generation, @NotNull Runnable update) {
        if (updateGuard.isLatest(generation)) {
            update.run();
        }
    }

    /**
     * 维护搜索请求的单调 generation，作为“后发请求优先”的并发守卫。
     */
    public static final class UpdateGuard {
        private final AtomicLong generation = new AtomicLong();

        /**
         * 生成并返回下一次搜索的唯一 generation。
         */
        long nextGeneration() {
            return generation.incrementAndGet();
        }

        /**
         * 判断候选 generation 是否仍代表最新一次搜索。
         */
        boolean isLatest(long candidate) {
            return generation.get() == candidate;
        }
    }
}
