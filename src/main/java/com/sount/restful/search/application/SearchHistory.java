package com.sount.restful.search.application;

import com.intellij.openapi.components.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.sount.restful.endpoint.navigation.RestServiceItem;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

@Service(Service.Level.PROJECT)
@State(name = "RestServiceSearchHistory", storages = @Storage(StoragePathMacros.WORKSPACE_FILE))
public final class SearchHistory implements PersistentStateComponent<SearchHistory.State> {

    private static final Logger LOG = Logger.getInstance(SearchHistory.class);
    private static final Map<Project, SearchHistory> FALLBACK_INSTANCES =
            Collections.synchronizedMap(new WeakHashMap<>());
    static final int MAX_TRACKED_ENTRIES = 200;

    private State myState = new State();

    public static class State {
        public List<String> recentQueries = new ArrayList<>();
        public List<String> favoriteEndpoints = new ArrayList<>();
        public Map<String, Long> accessTimes = new LinkedHashMap<>();
        public Map<String, String> selectedEndpointsByQuery = new LinkedHashMap<>();
        public Map<String, Integer> selectedIndexByQuery = new LinkedHashMap<>();
        public Map<String, Integer> firstVisibleIndexByQuery = new LinkedHashMap<>();
        public Map<String, Integer> scrollYByQuery = new LinkedHashMap<>();
        public Map<String, Integer> useCountByEndpoint = new LinkedHashMap<>();
    }

    public static SearchHistory getInstance(@NotNull Project project) {
        SearchHistory history = project.getService(SearchHistory.class);
        if (history != null) {
            return history;
        }
        LOG.warn("SearchHistory project service is unavailable; using an in-memory fallback instance");
        return FALLBACK_INSTANCES.computeIfAbsent(project, ignored -> new SearchHistory());
    }

    /**
     * 返回当前搜索历史状态的防御性深拷贝，供 IntelliJ 平台序列化。
     *
     * <p><b>线程约束：</b>平台可能在后台序列化线程读取状态，而 UI 或搜索任务同时写入；
     * 因此必须在实例锁内复制所有可变集合，不能直接暴露 {@code myState}。</p>
     * <p><b>兼容约束：</b>返回对象必须保留 {@link State} 的既有字段结构和字段名。</p>
     */
    @Override
    public synchronized @Nullable SearchHistory.State getState() {
        return copyState(myState);
    }

    /**
     * 加载并规范化平台持久化的历史状态。
     *
     * <p><b>兼容约束：</b>旧版本可能缺少新增集合字段或使用无序 Map；加载后统一补空值、
     * 转换为插入有序 Map，并执行与当前版本一致的容量裁剪。</p>
     */
    @Override
    public synchronized void loadState(@NotNull SearchHistory.State state) {
        // PersistentStateComponent.loadState() is called on the EDT during project loading.
        // All other access to myState is synchronized on this instance for thread safety.
        myState = state;
        ensureState();
        pruneState();
    }

    public synchronized void recordAccess(@NotNull RestServiceItem item) {
        ensureState();
        String key = item.getEndpointKey();
        myState.accessTimes.put(key, System.currentTimeMillis());
        pruneMap(myState.accessTimes);
        recordUse(item);
    }

    public synchronized void recordUse(@NotNull RestServiceItem item) {
        ensureState();
        myState.useCountByEndpoint.merge(item.getEndpointKey(), 1, Integer::sum);
        pruneMap(myState.useCountByEndpoint);
    }

    public synchronized int getUseCount(@NotNull RestServiceItem item) {
        return myState.useCountByEndpoint.getOrDefault(item.getEndpointKey(), 0);
    }

    public synchronized void recordQuery(@NotNull String query) {
        ensureState();
        if (query.isBlank()) return;
        myState.recentQueries.remove(query);
        myState.recentQueries.addFirst(query);
        if (myState.recentQueries.size() > 50) {
            myState.recentQueries = new ArrayList<>(myState.recentQueries.subList(0, 50));
        }
    }

    public synchronized void recordSelectedEndpoint(@NotNull String query, @NotNull RestServiceItem item) {
        ensureState();
        putQueryValue(myState.selectedEndpointsByQuery, query, item.getSearchSelectionKey());
    }

    public synchronized @Nullable String getSelectedEndpointKey(@NotNull String query) {
        return getQueryValue(myState.selectedEndpointsByQuery, query);
    }

    public synchronized void recordWindowState(@NotNull String query, int selectedIndex, int firstVisibleIndex, int scrollY) {
        ensureState();
        if (selectedIndex >= 0) {
            putQueryValue(myState.selectedIndexByQuery, query, selectedIndex);
        }
        if (firstVisibleIndex >= 0) {
            putQueryValue(myState.firstVisibleIndexByQuery, query, firstVisibleIndex);
        }
        if (scrollY >= 0) {
            putQueryValue(myState.scrollYByQuery, query, scrollY);
        }
    }

    public synchronized @Nullable Integer getSelectedIndex(@NotNull String query) {
        return getQueryValue(myState.selectedIndexByQuery, query);
    }

    public synchronized @Nullable Integer getFirstVisibleIndex(@NotNull String query) {
        return getQueryValue(myState.firstVisibleIndexByQuery, query);
    }

    public synchronized @Nullable Integer getScrollY(@NotNull String query) {
        return getQueryValue(myState.scrollYByQuery, query);
    }

    public synchronized long getLastAccessTime(@NotNull RestServiceItem item) {
        return myState.accessTimes.getOrDefault(item.getEndpointKey(), 0L);
    }

    public synchronized @NotNull List<String> getRecentQueries() {
        return Collections.unmodifiableList(new ArrayList<>(myState.recentQueries));
    }

    private static @NotNull String normalizeQuery(@NotNull String query) {
        return query.trim();
    }

    /**
     * 按统一的查询规范化和容量裁剪规则写入一项窗口状态。
     */
    private static <V> void putQueryValue(@NotNull Map<String, V> values,
                                          @NotNull String query,
                                          @NotNull V value) {
        String normalizedQuery = normalizeQuery(query);
        if (normalizedQuery.isEmpty()) {
            return;
        }
        values.put(normalizedQuery, value);
        pruneMap(values);
    }

    /**
     * 使用与写入一致的查询规范化规则读取窗口状态。
     */
    private static <V> @Nullable V getQueryValue(@NotNull Map<String, V> values,
                                                  @NotNull String query) {
        String normalizedQuery = normalizeQuery(query);
        return normalizedQuery.isEmpty() ? null : values.get(normalizedQuery);
    }

    private void ensureState() {
        if (myState.recentQueries == null) myState.recentQueries = new ArrayList<>();
        if (myState.favoriteEndpoints == null) myState.favoriteEndpoints = new ArrayList<>();
        myState.accessTimes = orderedMap(myState.accessTimes);
        myState.selectedEndpointsByQuery = orderedMap(myState.selectedEndpointsByQuery);
        myState.selectedIndexByQuery = orderedMap(myState.selectedIndexByQuery);
        myState.firstVisibleIndexByQuery = orderedMap(myState.firstVisibleIndexByQuery);
        myState.scrollYByQuery = orderedMap(myState.scrollYByQuery);
        myState.useCountByEndpoint = orderedMap(myState.useCountByEndpoint);
    }

    private void pruneState() {
        pruneMap(myState.accessTimes);
        pruneMap(myState.selectedEndpointsByQuery);
        pruneMap(myState.selectedIndexByQuery);
        pruneMap(myState.firstVisibleIndexByQuery);
        pruneMap(myState.scrollYByQuery);
        pruneMap(myState.useCountByEndpoint);
    }

    private static <V> Map<String, V> orderedMap(@Nullable Map<String, V> map) {
        if (map == null) {
            return new LinkedHashMap<>();
        }
        if (map instanceof LinkedHashMap<String, V>) {
            return map;
        }
        return new LinkedHashMap<>(map);
    }

    private static void pruneMap(@NotNull Map<?, ?> map) {
        while (map.size() > MAX_TRACKED_ENTRIES) {
            Iterator<?> iterator = map.keySet().iterator();
            if (!iterator.hasNext()) {
                return;
            }
            iterator.next();
            iterator.remove();
        }
    }

    /**
     * 深拷贝持久化状态中的全部集合，隔离平台序列化线程与运行时写操作。
     */
    private static State copyState(@NotNull State source) {
        State copy = new State();
        copy.recentQueries = new ArrayList<>(source.recentQueries);
        copy.favoriteEndpoints = new ArrayList<>(source.favoriteEndpoints);
        copy.accessTimes = new LinkedHashMap<>(source.accessTimes);
        copy.selectedEndpointsByQuery = new LinkedHashMap<>(source.selectedEndpointsByQuery);
        copy.selectedIndexByQuery = new LinkedHashMap<>(source.selectedIndexByQuery);
        copy.firstVisibleIndexByQuery = new LinkedHashMap<>(source.firstVisibleIndexByQuery);
        copy.scrollYByQuery = new LinkedHashMap<>(source.scrollYByQuery);
        copy.useCountByEndpoint = new LinkedHashMap<>(source.useCountByEndpoint);
        return copy;
    }
}
