package com.sount.restful.search;

import com.intellij.openapi.components.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.sount.restful.navigation.RestServiceItem;
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
     * Returns a defensive deep copy of the current state for platform serialization.
     * <p>
     * {@code PersistentStateComponent.getState()} may be invoked by the platform on a
     * background serialization thread while UI handlers (or the background search path)
     * mutate {@code myState}. Returning a snapshot copy under the instance lock keeps the
     * serialized state consistent and avoids {@link ConcurrentModificationException}.
     */
    @Override
    public synchronized @Nullable SearchHistory.State getState() {
        return copyState(myState);
    }

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
        String normalizedQuery = normalizeQuery(query);
        if (normalizedQuery.isEmpty()) return;
        myState.selectedEndpointsByQuery.put(normalizedQuery, item.getSearchSelectionKey());
        pruneMap(myState.selectedEndpointsByQuery);
    }

    public synchronized @Nullable String getSelectedEndpointKey(@NotNull String query) {
        String normalizedQuery = normalizeQuery(query);
        if (normalizedQuery.isEmpty()) return null;
        return myState.selectedEndpointsByQuery.get(normalizedQuery);
    }

    public synchronized void recordWindowState(@NotNull String query, int selectedIndex, int firstVisibleIndex, int scrollY) {
        ensureState();
        String normalizedQuery = normalizeQuery(query);
        if (normalizedQuery.isEmpty()) return;
        if (selectedIndex >= 0) {
            myState.selectedIndexByQuery.put(normalizedQuery, selectedIndex);
            pruneMap(myState.selectedIndexByQuery);
        }
        if (firstVisibleIndex >= 0) {
            myState.firstVisibleIndexByQuery.put(normalizedQuery, firstVisibleIndex);
            pruneMap(myState.firstVisibleIndexByQuery);
        }
        if (scrollY >= 0) {
            myState.scrollYByQuery.put(normalizedQuery, scrollY);
            pruneMap(myState.scrollYByQuery);
        }
    }

    public synchronized @Nullable Integer getSelectedIndex(@NotNull String query) {
        String normalizedQuery = normalizeQuery(query);
        if (normalizedQuery.isEmpty()) return null;
        return myState.selectedIndexByQuery.get(normalizedQuery);
    }

    public synchronized @Nullable Integer getFirstVisibleIndex(@NotNull String query) {
        String normalizedQuery = normalizeQuery(query);
        if (normalizedQuery.isEmpty()) return null;
        return myState.firstVisibleIndexByQuery.get(normalizedQuery);
    }

    public synchronized @Nullable Integer getScrollY(@NotNull String query) {
        String normalizedQuery = normalizeQuery(query);
        if (normalizedQuery.isEmpty()) return null;
        return myState.scrollYByQuery.get(normalizedQuery);
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
