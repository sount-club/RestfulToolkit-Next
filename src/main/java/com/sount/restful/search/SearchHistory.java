package com.sount.restful.search;

import com.intellij.openapi.components.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.sount.restful.navigation.action.RestServiceItem;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

@Service(Service.Level.PROJECT)
@State(name = "RestServiceSearchHistory", storages = @Storage(StoragePathMacros.WORKSPACE_FILE))
public final class SearchHistory implements PersistentStateComponent<SearchHistory.State> {

    private static final Logger LOG = Logger.getInstance(SearchHistory.class);
    private static final Map<Project, SearchHistory> FALLBACK_INSTANCES =
            Collections.synchronizedMap(new WeakHashMap<>());

    private State myState = new State();

    public static class State {
        public List<String> recentQueries = new ArrayList<>();
        public List<String> favoriteEndpoints = new ArrayList<>();
        public Map<String, Long> accessTimes = new HashMap<>();
        public Map<String, String> selectedEndpointsByQuery = new HashMap<>();
        public Map<String, Integer> selectedIndexByQuery = new HashMap<>();
        public Map<String, Integer> firstVisibleIndexByQuery = new HashMap<>();
        public Map<String, Integer> scrollYByQuery = new HashMap<>();
        public Map<String, Integer> useCountByEndpoint = new HashMap<>();
    }

    public static SearchHistory getInstance(@NotNull Project project) {
        SearchHistory history = project.getService(SearchHistory.class);
        if (history != null) {
            return history;
        }
        LOG.warn("SearchHistory project service is unavailable; using an in-memory fallback instance");
        return FALLBACK_INSTANCES.computeIfAbsent(project, ignored -> new SearchHistory());
    }

    @Override
    public @Nullable SearchHistory.State getState() {
        return myState;
    }

    @Override
    public void loadState(@NotNull SearchHistory.State state) {
        // PersistentStateComponent.loadState() is called on the EDT during project loading,
        // consistent with all other access to myState from UI event handlers.
        myState = state;
    }

    public void recordAccess(@NotNull RestServiceItem item) {
        String key = item.getEndpointKey();
        myState.accessTimes.put(key, System.currentTimeMillis());
        recordUse(item);
    }

    public void recordUse(@NotNull RestServiceItem item) {
        myState.useCountByEndpoint.merge(item.getEndpointKey(), 1, Integer::sum);
    }

    public int getUseCount(@NotNull RestServiceItem item) {
        return myState.useCountByEndpoint.getOrDefault(item.getEndpointKey(), 0);
    }

    public void recordQuery(@NotNull String query) {
        if (query.isBlank()) return;
        myState.recentQueries.remove(query);
        myState.recentQueries.addFirst(query);
        if (myState.recentQueries.size() > 50) {
            myState.recentQueries = new ArrayList<>(myState.recentQueries.subList(0, 50));
        }
    }

    public void recordSelectedEndpoint(@NotNull String query, @NotNull RestServiceItem item) {
        String normalizedQuery = normalizeQuery(query);
        if (normalizedQuery.isEmpty()) return;
        myState.selectedEndpointsByQuery.put(normalizedQuery, item.getSearchSelectionKey());
    }

    public @Nullable String getSelectedEndpointKey(@NotNull String query) {
        String normalizedQuery = normalizeQuery(query);
        if (normalizedQuery.isEmpty()) return null;
        return myState.selectedEndpointsByQuery.get(normalizedQuery);
    }

    public void recordWindowState(@NotNull String query, int selectedIndex, int firstVisibleIndex, int scrollY) {
        String normalizedQuery = normalizeQuery(query);
        if (normalizedQuery.isEmpty()) return;
        if (selectedIndex >= 0) {
            myState.selectedIndexByQuery.put(normalizedQuery, selectedIndex);
        }
        if (firstVisibleIndex >= 0) {
            myState.firstVisibleIndexByQuery.put(normalizedQuery, firstVisibleIndex);
        }
        if (scrollY >= 0) {
            myState.scrollYByQuery.put(normalizedQuery, scrollY);
        }
    }

    public @Nullable Integer getSelectedIndex(@NotNull String query) {
        String normalizedQuery = normalizeQuery(query);
        if (normalizedQuery.isEmpty()) return null;
        return myState.selectedIndexByQuery.get(normalizedQuery);
    }

    public @Nullable Integer getFirstVisibleIndex(@NotNull String query) {
        String normalizedQuery = normalizeQuery(query);
        if (normalizedQuery.isEmpty()) return null;
        return myState.firstVisibleIndexByQuery.get(normalizedQuery);
    }

    public @Nullable Integer getScrollY(@NotNull String query) {
        String normalizedQuery = normalizeQuery(query);
        if (normalizedQuery.isEmpty()) return null;
        return myState.scrollYByQuery.get(normalizedQuery);
    }

    public long getLastAccessTime(@NotNull RestServiceItem item) {
        return myState.accessTimes.getOrDefault(item.getEndpointKey(), 0L);
    }

    public @NotNull List<String> getRecentQueries() {
        return Collections.unmodifiableList(myState.recentQueries);
    }

    private static @NotNull String normalizeQuery(@NotNull String query) {
        return query.trim();
    }
}
