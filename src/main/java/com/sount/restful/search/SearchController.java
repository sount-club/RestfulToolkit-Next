package com.sount.restful.search;

import com.intellij.openapi.module.Module;
import com.intellij.ui.components.JBList;
import com.sount.restful.method.HttpMethod;
import com.sount.restful.navigation.RestServiceItem;
import com.sount.restful.utils.RestfulToolkitBundle;
import com.sount.restful.utils.RestfulToolkitBundle.Keys;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.*;
import java.util.function.ToLongFunction;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Search dispatch, filtering logic, and module/method filter management
 * for the unified search popup.
 */
final class SearchController {

    private SearchController() {
    }

    // --- Search execution ---

    static void performSearch(@NotNull String text, @NotNull EndpointIndex index,
                              @NotNull DefaultListModel<SearchResult> model,
                              @NotNull JBList<SearchResult> resultList,
                              @NotNull JLabel statusLabel,
                              @NotNull JButton searchAllModulesBtn,
                              @Nullable Module filterModule,
                              @NotNull UnifiedSearchRenderer renderer,
                              @NotNull UpdateGuard updateGuard,
                              @Nullable HttpMethod methodFilter,
                              @Nullable String preferredEndpointKey,
                              @Nullable Integer preferredSelectionIndex,
                              @Nullable Integer preferredFirstVisibleIndex,
                              @Nullable Integer preferredScrollY) {
        long updateGeneration = updateGuard.nextGeneration();
        List<RestServiceItem> allItems = index.getItems();
        boolean indexReady = index.isReady();
        List<RestServiceItem> searchableItems = allItems;
        if (filterModule != null) {
            List<RestServiceItem> filtered = new ArrayList<>();
            for (RestServiceItem item : allItems) {
                if (filterModule.equals(item.getModule())) {
                    filtered.add(item);
                }
            }
            searchableItems = filtered;
        }
        final int totalCount = searchableItems.size();
        SearchQuery query = SearchQuery.parse(text);

        // If method filter is set but query didn't parse it, combine
        if (methodFilter != null && query.methodFilter() == null) {
            query = new SearchQuery(query.rawInput(), methodFilter, query.urlPattern(),
                    query.classNamePattern(), query.methodNamePattern(), query.tokens());
        }

        SearchHistory history = SearchHistory.getInstance(index.getProject());
        List<SearchResult> results;

        // Empty query: show recently accessed endpoints (top 20)
        if (text.isEmpty() && methodFilter == null) {
            results = buildRecentResults(searchableItems, history::getLastAccessTime);
        } else {
            results = SearchEngine.search(query, searchableItems, 200, history::getUseCount);
        }

        // Set highlight tokens for renderer
        renderer.setHighlightTokens(query.tokens());

        SwingUtilities.invokeLater(() -> runIfLatest(updateGuard, updateGeneration, () -> {
            model.clear();
            for (SearchResult result : results) {
                model.addElement(result);
            }
            if (!results.isEmpty()) {
                int selectionIndex = SearchPopupModel.findSelectionIndex(results, preferredEndpointKey, preferredSelectionIndex);
                SearchPopupActions.selectAndRevealIndex(resultList, selectionIndex, preferredFirstVisibleIndex, preferredScrollY);
            }

            statusLabel.setText(SearchPopupModel.buildStatusText(text, results.size(), totalCount, indexReady,
                    filterModule, methodFilter));

            // Show "搜索全部模块" button when no results and a module filter is active
            searchAllModulesBtn.setVisible(results.isEmpty() && filterModule != null && !text.isEmpty());
        }));
    }

    static void runIfLatest(@NotNull UpdateGuard updateGuard, long generation, @NotNull Runnable update) {
        if (updateGuard.isLatest(generation)) {
            update.run();
        }
    }

    static final class UpdateGuard {
        private final AtomicLong generation = new AtomicLong();

        long nextGeneration() {
            return generation.incrementAndGet();
        }

        boolean isLatest(long candidate) {
            return generation.get() == candidate;
        }
    }

    // --- Filter resolution ---

    static @Nullable HttpMethod resolveSelectedMethod(ButtonGroup group, EnumMap<HttpMethod, JToggleButton> buttons) {
        for (Map.Entry<HttpMethod, JToggleButton> entry : buttons.entrySet()) {
            if (entry.getValue().isSelected()) {
                return entry.getKey();
            }
        }
        return null; // "All" selected
    }

    static @Nullable Module resolveSelectedModule(@NotNull JComboBox<String> combo,
                                                  @Nullable Module currentModule,
                                                  @NotNull List<RestServiceItem> items) {
        Object selected = combo.getSelectedItem();
        if (selected == null || RestfulToolkitBundle.message(Keys.SEARCH_POPUP_MODULE_ALL).equals(selected)) return null;
        String selectedStr = selected.toString();
        if (currentModule != null && selectedStr.equals(formatCurrentModule(currentModule))) {
            return currentModule;
        }
        // Otherwise find by module name
        for (RestServiceItem item : items) {
            if (selectedStr.equals(item.getModuleName())) {
                return item.getModule();
            }
        }
        return null;
    }

    static void refreshModuleFilter(@NotNull JComboBox<String> combo,
                                    @NotNull List<RestServiceItem> items,
                                    @Nullable Module currentModule,
                                    @Nullable Object preferredSelection) {
        combo.removeAllItems();
        String allModulesText = RestfulToolkitBundle.message(Keys.SEARCH_POPUP_MODULE_ALL);
        combo.addItem(allModulesText);
        if (currentModule != null) {
            combo.addItem(formatCurrentModule(currentModule));
        }

        Set<String> modules = new LinkedHashSet<>();
        for (RestServiceItem item : items) {
            String name = item.getModuleName();
            if (name != null && !name.isEmpty()) {
                // Skip current module if it's already shown as "Current Module: xxx"
                if (currentModule != null && name.equals(currentModule.getName())) continue;
                modules.add(name);
            }
        }
        List<String> sorted = new ArrayList<>(modules);
        Collections.sort(sorted);
        for (String m : sorted) {
            combo.addItem(m);
        }

        if (preferredSelection != null) {
            String selectedText = preferredSelection.toString();
            for (int i = 0; i < combo.getItemCount(); i++) {
                if (selectedText.equals(combo.getItemAt(i))) {
                    combo.setSelectedIndex(i);
                    return;
                }
            }
        }
        combo.setSelectedItem(allModulesText);
    }

    // --- Delegating helpers (to SearchPopupModel) ---

    static @NotNull List<SearchResult> buildRecentResults(@NotNull List<RestServiceItem> items,
                                                          @NotNull ToLongFunction<RestServiceItem> lastAccessLookup) {
        return SearchPopupModel.buildRecentResults(items, lastAccessLookup);
    }

    static @NotNull String formatCurrentModule(@NotNull Module module) {
        return RestfulToolkitBundle.message(Keys.SEARCH_POPUP_MODULE_CURRENT, module.getName());
    }
}
