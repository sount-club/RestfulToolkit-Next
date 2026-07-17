package com.sount.restful.search.ui;

import com.intellij.ui.components.JBList;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiMethod;
import com.intellij.util.ui.UIUtil;
import com.sount.restful.endpoint.navigation.RestServiceItem;
import com.sount.restful.search.domain.SearchResult;

import javax.swing.*;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static com.sount.restful.search.ui.SearchResultTestFactory.result;

public class UnifiedSearchPopupTest extends BasePlatformTestCase {

    public void testResolveInitialSearchTextKeepsExplicitText() {
        assertEquals("copied-url", SearchPopupModel.resolveInitialSearchText("copied-url", java.util.List.of("last-query")));
    }

    public void testResolveInitialSearchTextFallsBackToRecentQuery() {
        assertEquals("last-query", SearchPopupModel.resolveInitialSearchText(null, java.util.List.of("last-query")));
        assertEquals("last-query", SearchPopupModel.resolveInitialSearchText("", java.util.List.of("last-query")));
    }

    public void testStatusShowsLoadingBeforeEndpointIndexIsReady() {
        assertEquals("Indexing REST endpoints... Results will refresh automatically.",
                SearchPopupModel.buildStatusText("", 0, 0, false));
        assertEquals("Indexing REST endpoints for \"users\"... Results will refresh automatically.",
                SearchPopupModel.buildStatusText("users", 0, 0, false));
        assertEquals("Indexing REST endpoints for \"/commo\"... Results will refresh automatically.",
                SearchPopupModel.buildStatusText("/commo", 0, 12, false));
    }

    public void testStatusShowsNoEndpointsOnlyAfterEndpointIndexIsReady() {
        assertEquals("No endpoints found. Check if project has Spring/JAX-RS controllers and IDE indexing is complete.",
                SearchPopupModel.buildStatusText("", 0, 0, true));
    }

    public void testFindSelectionIndexPrefersSavedEndpointKey() {
        RestServiceItem first = createItem("GET", "/activity/list");
        RestServiceItem second = createItem("GET", "/activity/rewardDetail");
        List<SearchResult<RestServiceItem>> results = List.of(
                result(first, 100, "url"),
                result(second, 90, "url")
        );

        assertEquals(1, SearchPopupModel.findSelectionIndex(results, second.getEndpointKey()));
    }

    public void testFindSelectionIndexDistinguishesDuplicateMethodAndUrl() {
        RestServiceItem first = createItem("GET", "/activity/rewardDetail", "UserActivityAction", "rewardDetail");
        RestServiceItem second = createItem("GET", "/activity/rewardDetail", "AdminActivityAction", "rewardDetail");
        List<SearchResult<RestServiceItem>> results = List.of(
                result(first, 100, "url"),
                result(second, 90, "url")
        );

        assertEquals(1, SearchPopupModel.findSelectionIndex(results, second.getSearchSelectionKey()));
    }

    public void testFindSelectionIndexPrefersRecordedWindowIndex() {
        RestServiceItem first = createItem("GET", "/activity/rewardDetail", "UserActivityAction", "rewardDetail");
        RestServiceItem second = createItem("GET", "/activity/rewardDetail", "AdminActivityAction", "rewardDetail");
        List<SearchResult<RestServiceItem>> results = List.of(
                result(first, 100, "url"),
                result(second, 90, "url")
        );

        assertEquals(1, SearchPopupModel.findSelectionIndex(results, first.getSearchSelectionKey(), 1));
    }

    public void testFindSelectionIndexFallsBackToFirstResult() {
        RestServiceItem first = createItem("GET", "/activity/list");
        List<SearchResult<RestServiceItem>> results = List.of(result(first, 100, "url"));

        assertEquals(0, SearchPopupModel.findSelectionIndex(results, "POST:/missing"));
    }

    public void testRefreshModuleFilterAddsModulesAfterIndexBecomesAvailable() {
        JComboBox<String> combo = new JComboBox<>();
        RestServiceItem item = createItem("GET", "/activity/list");
        item.setModule(getModule());

        SearchFilterModel.refreshModuleFilter(combo, List.of(), null, null);
        assertEquals(1, combo.getItemCount());

        SearchFilterModel.refreshModuleFilter(combo, List.of(item), null, null);

        assertEquals(2, combo.getItemCount());
        assertEquals(getModule().getName(), combo.getItemAt(1));
    }

    public void testRefreshModuleFilterPreservesExistingSelection() {
        JComboBox<String> combo = new JComboBox<>();
        RestServiceItem item = createItem("GET", "/activity/list");
        item.setModule(getModule());

        SearchFilterModel.refreshModuleFilter(combo, List.of(item), null, null);
        combo.setSelectedItem(getModule().getName());
        SearchFilterModel.refreshModuleFilter(combo, List.of(item), null, combo.getSelectedItem());

        assertEquals(getModule().getName(), combo.getSelectedItem());
    }

    public void testMoveFocusToResultsKeepsExistingSelection() {
        JBList<SearchResult<RestServiceItem>> list = new JBList<>(new DefaultListModel<>());
        RestServiceItem first = createItem("GET", "/activity/list");
        RestServiceItem second = createItem("GET", "/activity/rewardDetail");
        DefaultListModel<SearchResult<RestServiceItem>> model =
                (DefaultListModel<SearchResult<RestServiceItem>>) list.getModel();
        model.addElement(result(first, 100, "url"));
        model.addElement(result(second, 90, "url"));
        list.setSelectedIndex(1);

        SearchPopupActions.moveFocusToResults(list);

        assertEquals(1, list.getSelectedIndex());
    }

    public void testMoveFocusToResultsSelectsFirstWhenNothingSelected() {
        JBList<SearchResult<RestServiceItem>> list = new JBList<>(new DefaultListModel<>());
        RestServiceItem first = createItem("GET", "/activity/list");
        DefaultListModel<SearchResult<RestServiceItem>> model =
                (DefaultListModel<SearchResult<RestServiceItem>>) list.getModel();
        model.addElement(result(first, 100, "url"));

        SearchPopupActions.moveFocusToResults(list);

        assertEquals(0, list.getSelectedIndex());
    }

    public void testMoveSelectionFromSearchFieldMovesDownFromCurrentSelection() {
        JBList<SearchResult<RestServiceItem>> list = createListWithThreeItems();
        list.setSelectedIndex(1);

        SearchPopupActions.moveSelectionFromSearchField(list, 1);

        assertEquals(2, list.getSelectedIndex());
    }

    public void testMoveSelectionFromSearchFieldMovesUpFromCurrentSelection() {
        JBList<SearchResult<RestServiceItem>> list = createListWithThreeItems();
        list.setSelectedIndex(1);

        SearchPopupActions.moveSelectionFromSearchField(list, -1);

        assertEquals(0, list.getSelectedIndex());
    }

    public void testMoveSelectionFromSearchFieldSelectsFirstWhenNothingSelected() {
        JBList<SearchResult<RestServiceItem>> list = createListWithThreeItems();

        SearchPopupActions.moveSelectionFromSearchField(list, 1);

        assertEquals(0, list.getSelectedIndex());
    }

    public void testSelectAndRevealRepeatsRevealAfterLayout() {
        TrackingList list = new TrackingList();
        DefaultListModel<SearchResult<RestServiceItem>> model =
                (DefaultListModel<SearchResult<RestServiceItem>>) list.getModel();
        model.addElement(result(createItem("GET", "/activity/list"), 100, "url"));
        model.addElement(result(createItem("GET", "/activity/rewardDetail"), 90, "url"));

        SearchPopupActions.selectAndRevealIndex(list, 1);
        UIUtil.dispatchAllInvocationEvents();

        assertEquals(1, list.getSelectedIndex());
        assertTrue(list.ensureVisibleCalls >= 2);
        assertEquals(1, list.lastVisibleIndex);
    }

    public void testSelectAndRevealCanRestoreRecordedVisibleIndex() {
        TrackingList list = new TrackingList();
        DefaultListModel<SearchResult<RestServiceItem>> model =
                (DefaultListModel<SearchResult<RestServiceItem>>) list.getModel();
        model.addElement(result(createItem("GET", "/activity/list"), 100, "url"));
        model.addElement(result(createItem("GET", "/activity/rewardDetail"), 90, "url"));

        SearchPopupActions.selectAndRevealIndex(list, 1, 0);

        assertEquals(1, list.getSelectedIndex());
        assertEquals(0, list.lastVisibleIndex);
    }

    public void testSelectAndRevealCanRestoreScrollY() {
        JBList<SearchResult<RestServiceItem>> list = createListWithManyItems();
        list.setFixedCellHeight(20);
        JScrollPane scrollPane = new JScrollPane(list);
        scrollPane.setBounds(0, 0, 300, 100);
        list.setBounds(0, 0, 300, list.getPreferredSize().height);
        scrollPane.doLayout();

        SearchPopupActions.selectAndRevealIndex(list, 20, null, 200);
        UIUtil.dispatchAllInvocationEvents();

        assertEquals(20, list.getSelectedIndex());
        assertEquals(200, list.getVisibleRect().y);
    }

    public void testEnterActionIsInstalledOnFilterControls() {
        JPanel panel = new JPanel();
        JComboBox<String> comboBox = new JComboBox<>(new String[]{"All Modules"});
        JButton filterButton = new JButton("GET");
        panel.add(comboBox);
        panel.add(filterButton);
        AtomicInteger navigations = new AtomicInteger();

        SearchPopupActions.installEnterAction(panel, navigations::incrementAndGet);

        invokeEnterAction(comboBox);
        invokeEnterAction(filterButton);

        assertEquals(2, navigations.get());
    }

    private static void invokeEnterAction(JComponent component) {
        Object actionKey = component.getInputMap(JComponent.WHEN_FOCUSED)
                .get(KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_ENTER, 0));
        assertNotNull(actionKey);
        Action action = component.getActionMap().get(actionKey);
        assertNotNull(action);
        action.actionPerformed(new java.awt.event.ActionEvent(component,
                java.awt.event.ActionEvent.ACTION_PERFORMED, "enter"));
    }

    private static class TrackingList extends JBList<SearchResult<RestServiceItem>> {
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

    private JBList<SearchResult<RestServiceItem>> createListWithThreeItems() {
        JBList<SearchResult<RestServiceItem>> list = new JBList<>(new DefaultListModel<>());
        DefaultListModel<SearchResult<RestServiceItem>> model =
                (DefaultListModel<SearchResult<RestServiceItem>>) list.getModel();
        model.addElement(result(createItem("GET", "/activity/list"), 100, "url"));
        model.addElement(result(createItem("GET", "/activity/rewardDetail"), 90, "url"));
        model.addElement(result(createItem("POST", "/activity/add"), 80, "url"));
        return list;
    }

    private JBList<SearchResult<RestServiceItem>> createListWithManyItems() {
        JBList<SearchResult<RestServiceItem>> list = new JBList<>(new DefaultListModel<>());
        DefaultListModel<SearchResult<RestServiceItem>> model =
                (DefaultListModel<SearchResult<RestServiceItem>>) list.getModel();
        for (int i = 0; i < 40; i++) {
            model.addElement(result(createItem("GET", "/activity/" + i), 100 - i, "url"));
        }
        return list;
    }
}
