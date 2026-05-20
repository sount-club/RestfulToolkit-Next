package com.sount.restful.search;

import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.components.StoragePathMacros;
import com.intellij.openapi.project.Project;
import com.sount.restful.navigation.action.RestServiceItem;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

@State(name = "RestServiceSearchHistory", storages = @Storage(StoragePathMacros.WORKSPACE_FILE))
public class SearchHistory implements PersistentStateComponent<SearchHistory.State> {

    private State myState = new State();

    public static class State {
        public List<String> recentQueries = new ArrayList<>();
        public List<String> favoriteEndpoints = new ArrayList<>();
        public Map<String, Long> accessTimes = new HashMap<>();
        public Map<String, String> selectedEndpointsByQuery = new HashMap<>();
        public Map<String, Integer> selectedIndexByQuery = new HashMap<>();
        public Map<String, Integer> firstVisibleIndexByQuery = new HashMap<>();
        public Map<String, Integer> scrollYByQuery = new HashMap<>();
    }

    public static SearchHistory getInstance(@NotNull Project project) {
        return project.getService(SearchHistory.class);
    }

    @Override
    public @Nullable SearchHistory.State getState() {
        return myState;
    }

    @Override
    public void loadState(@NotNull SearchHistory.State state) {
        myState = state;
    }

    public void recordAccess(@NotNull RestServiceItem item) {
        String key = item.getEndpointKey();
        myState.accessTimes.put(key, System.currentTimeMillis());
    }

    public void recordQuery(@NotNull String query) {
        if (query.isBlank()) return;
        myState.recentQueries.remove(query);
        myState.recentQueries.add(0, query);
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

    public void recordWindowState(@NotNull String query, int selectedIndex, int firstVisibleIndex) {
        recordWindowState(query, selectedIndex, firstVisibleIndex, -1);
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

    public void toggleFavorite(@NotNull RestServiceItem item) {
        String key = item.getEndpointKey();
        if (myState.favoriteEndpoints.contains(key)) {
            myState.favoriteEndpoints.remove(key);
        } else {
            myState.favoriteEndpoints.add(key);
        }
    }

    public boolean isFavorite(@NotNull RestServiceItem item) {
        return myState.favoriteEndpoints.contains(item.getEndpointKey());
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
