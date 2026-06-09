package com.sount.restful.search;

import com.intellij.ui.components.JBList;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiMethod;
import com.intellij.util.ui.UIUtil;
import com.sount.restful.navigation.action.RestServiceItem;

import javax.swing.*;
import java.util.List;

public class UnifiedSearchPopupTest extends BasePlatformTestCase {

    public void testResolveInitialSearchTextKeepsExplicitText() {
        assertEquals("copied-url", UnifiedSearchPopup.resolveInitialSearchText("copied-url", java.util.List.of("last-query")));
    }

    public void testResolveInitialSearchTextFallsBackToRecentQuery() {
        assertEquals("last-query", UnifiedSearchPopup.resolveInitialSearchText(null, java.util.List.of("last-query")));
        assertEquals("last-query", UnifiedSearchPopup.resolveInitialSearchText("", java.util.List.of("last-query")));
    }

    public void testStatusShowsLoadingBeforeEndpointIndexIsReady() {
        assertEquals("Indexing REST endpoints... Results will refresh automatically.",
                UnifiedSearchPopup.buildStatusText("", 0, 0, false));
        assertEquals("Indexing REST endpoints for \"users\"... Results will refresh automatically.",
                UnifiedSearchPopup.buildStatusText("users", 0, 0, false));
        assertEquals("Indexing REST endpoints for \"/commo\"... Results will refresh automatically.",
                UnifiedSearchPopup.buildStatusText("/commo", 0, 12, false));
    }

    public void testStatusShowsNoEndpointsOnlyAfterEndpointIndexIsReady() {
        assertEquals("No endpoints found. Check if project has Spring/JAX-RS controllers and IDE indexing is complete.",
                UnifiedSearchPopup.buildStatusText("", 0, 0, true));
    }

    public void testFindSelectionIndexPrefersSavedEndpointKey() {
        RestServiceItem first = createItem("GET", "/activity/list");
        RestServiceItem second = createItem("GET", "/activity/rewardDetail");
        List<SearchResult> results = List.of(
                new SearchResult(first, 100, "url"),
                new SearchResult(second, 90, "url")
        );

        assertEquals(1, UnifiedSearchPopup.findSelectionIndex(results, second.getEndpointKey()));
    }

    public void testFindSelectionIndexDistinguishesDuplicateMethodAndUrl() {
        RestServiceItem first = createItem("GET", "/activity/rewardDetail", "UserActivityAction", "rewardDetail");
        RestServiceItem second = createItem("GET", "/activity/rewardDetail", "AdminActivityAction", "rewardDetail");
        List<SearchResult> results = List.of(
                new SearchResult(first, 100, "url"),
                new SearchResult(second, 90, "url")
        );

        assertEquals(1, UnifiedSearchPopup.findSelectionIndex(results, second.getSearchSelectionKey()));
    }

    public void testFindSelectionIndexPrefersRecordedWindowIndex() {
        RestServiceItem first = createItem("GET", "/activity/rewardDetail", "UserActivityAction", "rewardDetail");
        RestServiceItem second = createItem("GET", "/activity/rewardDetail", "AdminActivityAction", "rewardDetail");
        List<SearchResult> results = List.of(
                new SearchResult(first, 100, "url"),
                new SearchResult(second, 90, "url")
        );

        assertEquals(1, UnifiedSearchPopup.findSelectionIndex(results, first.getSearchSelectionKey(), 1));
    }

    public void testFindSelectionIndexFallsBackToFirstResult() {
        RestServiceItem first = createItem("GET", "/activity/list");
        List<SearchResult> results = List.of(new SearchResult(first, 100, "url"));

        assertEquals(0, UnifiedSearchPopup.findSelectionIndex(results, "POST:/missing"));
    }

    public void testBuildRecentResultsKeepsOnlyTopTwentyByLastAccessTime() {
        List<RestServiceItem> items = java.util.stream.IntStream.range(0, 25)
                .mapToObj(i -> createItem("GET", "/activity/" + i))
                .toList();

        List<SearchResult> results = UnifiedSearchPopup.buildRecentResults(items, item -> {
            String url = item.getUrl();
            return Long.parseLong(url.substring(url.lastIndexOf('/') + 1));
        });

        assertEquals(20, results.size());
        assertEquals("/activity/24", results.get(0).item().getUrl());
        assertEquals("/activity/5", results.get(19).item().getUrl());
    }

    public void testRefreshModuleFilterAddsModulesAfterIndexBecomesAvailable() {
        JComboBox<String> combo = new JComboBox<>();
        RestServiceItem item = createItem("GET", "/activity/list");
        item.setModule(getModule());

        UnifiedSearchPopup.refreshModuleFilter(combo, List.of(), null, null);
        assertEquals(1, combo.getItemCount());

        UnifiedSearchPopup.refreshModuleFilter(combo, List.of(item), null, null);

        assertEquals(2, combo.getItemCount());
        assertEquals(getModule().getName(), combo.getItemAt(1));
    }

    public void testRefreshModuleFilterPreservesExistingSelection() {
        JComboBox<String> combo = new JComboBox<>();
        RestServiceItem item = createItem("GET", "/activity/list");
        item.setModule(getModule());

        UnifiedSearchPopup.refreshModuleFilter(combo, List.of(item), null, null);
        combo.setSelectedItem(getModule().getName());
        UnifiedSearchPopup.refreshModuleFilter(combo, List.of(item), null, combo.getSelectedItem());

        assertEquals(getModule().getName(), combo.getSelectedItem());
    }

    public void testMoveFocusToResultsKeepsExistingSelection() {
        JBList<SearchResult> list = new JBList<>(new DefaultListModel<>());
        RestServiceItem first = createItem("GET", "/activity/list");
        RestServiceItem second = createItem("GET", "/activity/rewardDetail");
        DefaultListModel<SearchResult> model = (DefaultListModel<SearchResult>) list.getModel();
        model.addElement(new SearchResult(first, 100, "url"));
        model.addElement(new SearchResult(second, 90, "url"));
        list.setSelectedIndex(1);

        UnifiedSearchPopup.moveFocusToResults(list);

        assertEquals(1, list.getSelectedIndex());
    }

    public void testMoveFocusToResultsSelectsFirstWhenNothingSelected() {
        JBList<SearchResult> list = new JBList<>(new DefaultListModel<>());
        RestServiceItem first = createItem("GET", "/activity/list");
        DefaultListModel<SearchResult> model = (DefaultListModel<SearchResult>) list.getModel();
        model.addElement(new SearchResult(first, 100, "url"));

        UnifiedSearchPopup.moveFocusToResults(list);

        assertEquals(0, list.getSelectedIndex());
    }

    public void testMoveSelectionFromSearchFieldMovesDownFromCurrentSelection() {
        JBList<SearchResult> list = createListWithThreeItems();
        list.setSelectedIndex(1);

        UnifiedSearchPopup.moveSelectionFromSearchField(list, 1);

        assertEquals(2, list.getSelectedIndex());
    }

    public void testMoveSelectionFromSearchFieldMovesUpFromCurrentSelection() {
        JBList<SearchResult> list = createListWithThreeItems();
        list.setSelectedIndex(1);

        UnifiedSearchPopup.moveSelectionFromSearchField(list, -1);

        assertEquals(0, list.getSelectedIndex());
    }

    public void testMoveSelectionFromSearchFieldSelectsFirstWhenNothingSelected() {
        JBList<SearchResult> list = createListWithThreeItems();

        UnifiedSearchPopup.moveSelectionFromSearchField(list, 1);

        assertEquals(0, list.getSelectedIndex());
    }

    public void testSelectAndRevealRepeatsRevealAfterLayout() {
        TrackingList list = new TrackingList();
        DefaultListModel<SearchResult> model = (DefaultListModel<SearchResult>) list.getModel();
        model.addElement(new SearchResult(createItem("GET", "/activity/list"), 100, "url"));
        model.addElement(new SearchResult(createItem("GET", "/activity/rewardDetail"), 90, "url"));

        UnifiedSearchPopup.selectAndRevealIndex(list, 1);
        UIUtil.dispatchAllInvocationEvents();

        assertEquals(1, list.getSelectedIndex());
        assertTrue(list.ensureVisibleCalls >= 2);
        assertEquals(1, list.lastVisibleIndex);
    }

    public void testSelectAndRevealCanRestoreRecordedVisibleIndex() {
        TrackingList list = new TrackingList();
        DefaultListModel<SearchResult> model = (DefaultListModel<SearchResult>) list.getModel();
        model.addElement(new SearchResult(createItem("GET", "/activity/list"), 100, "url"));
        model.addElement(new SearchResult(createItem("GET", "/activity/rewardDetail"), 90, "url"));

        UnifiedSearchPopup.selectAndRevealIndex(list, 1, 0);

        assertEquals(1, list.getSelectedIndex());
        assertEquals(0, list.lastVisibleIndex);
    }

    public void testSelectAndRevealCanRestoreScrollY() {
        JBList<SearchResult> list = createListWithManyItems();
        list.setFixedCellHeight(20);
        JScrollPane scrollPane = new JScrollPane(list);
        scrollPane.setBounds(0, 0, 300, 100);
        list.setBounds(0, 0, 300, list.getPreferredSize().height);
        scrollPane.doLayout();

        UnifiedSearchPopup.selectAndRevealIndex(list, 20, null, 200);
        UIUtil.dispatchAllInvocationEvents();

        assertEquals(20, list.getSelectedIndex());
        assertEquals(200, list.getVisibleRect().y);
    }

    private static class TrackingList extends JBList<SearchResult> {
        int ensureVisibleCalls;
        int lastVisibleIndex = -1;

        private TrackingList() {
            super(new DefaultListModel<>());
        }

        @Override
        public void ensureIndexIsVisible(int index) {
            ensureVisibleCalls++;
            lastVisibleIndex = index;
            super.ensureIndexIsVisible(index);
        }
    }

    private RestServiceItem createItem(String methodText, String url) {
        return createItem(methodText, url, "ActivityAction", "endpoint");
    }

    private RestServiceItem createItem(String methodText, String url, String className, String methodName) {
        PsiJavaFile javaFile = (PsiJavaFile) myFixture.configureByText(url.replace("/", "_") + "_" + className + ".java", """
                package demo;

                public class %s {
                    public void %s() {}
                }
                """.formatted(className, methodName));
        PsiClass psiClass = javaFile.getClasses()[0];
        PsiMethod method = psiClass.findMethodsByName(methodName, false)[0];
        return new RestServiceItem(method, methodText, url);
    }

    private JBList<SearchResult> createListWithThreeItems() {
        JBList<SearchResult> list = new JBList<>(new DefaultListModel<>());
        DefaultListModel<SearchResult> model = (DefaultListModel<SearchResult>) list.getModel();
        model.addElement(new SearchResult(createItem("GET", "/activity/list"), 100, "url"));
        model.addElement(new SearchResult(createItem("GET", "/activity/rewardDetail"), 90, "url"));
        model.addElement(new SearchResult(createItem("POST", "/activity/add"), 80, "url"));
        return list;
    }

    private JBList<SearchResult> createListWithManyItems() {
        JBList<SearchResult> list = new JBList<>(new DefaultListModel<>());
        DefaultListModel<SearchResult> model = (DefaultListModel<SearchResult>) list.getModel();
        for (int i = 0; i < 40; i++) {
            model.addElement(new SearchResult(createItem("GET", "/activity/" + i), 100 - i, "url"));
        }
        return list;
    }
}
