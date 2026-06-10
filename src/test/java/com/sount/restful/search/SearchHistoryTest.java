package com.sount.restful.search;

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiMethod;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import com.sount.restful.navigation.RestServiceItem;

public class SearchHistoryTest extends BasePlatformTestCase {

    public void testRegisteredAsProjectService() {
        assertNotNull(SearchHistory.getInstance(getProject()));
    }

    public void testRecordsSelectedEndpointByQuery() {
        RestServiceItem item = createItem("GET", "/activity/rewardDetail");
        SearchHistory history = new SearchHistory();

        history.recordSelectedEndpoint("/activity", item);

        assertEquals(item.getSearchSelectionKey(), history.getSelectedEndpointKey("/activity"));
        assertNull(history.getSelectedEndpointKey("/other"));
    }

    public void testIgnoresBlankQuerySelection() {
        RestServiceItem item = createItem("GET", "/activity/rewardDetail");
        SearchHistory history = new SearchHistory();

        history.recordSelectedEndpoint("", item);

        assertNull(history.getSelectedEndpointKey(""));
    }

    public void testRecordsWindowStateByQuery() {
        SearchHistory history = new SearchHistory();

        history.recordWindowState("/activity", 12, 8, 240);

        assertEquals(Integer.valueOf(12), history.getSelectedIndex("/activity"));
        assertEquals(Integer.valueOf(8), history.getFirstVisibleIndex("/activity"));
        assertEquals(Integer.valueOf(240), history.getScrollY("/activity"));
        assertNull(history.getSelectedIndex("/other"));
    }

    public void testPersistentMapsArePrunedToBoundedSize() {
        SearchHistory history = new SearchHistory();
        PsiMethod method = createMethod();

        for (int i = 0; i < SearchHistory.MAX_TRACKED_ENTRIES + 25; i++) {
            RestServiceItem item = new RestServiceItem(method, "GET", "/activity/" + i);
            history.recordAccess(item);
            history.recordSelectedEndpoint("query-" + i, item);
            history.recordWindowState("query-" + i, i, i, i);
        }

        SearchHistory.State state = history.getState();
        assertTrue(state.accessTimes.size() <= SearchHistory.MAX_TRACKED_ENTRIES);
        assertTrue(state.useCountByEndpoint.size() <= SearchHistory.MAX_TRACKED_ENTRIES);
        assertTrue(state.selectedEndpointsByQuery.size() <= SearchHistory.MAX_TRACKED_ENTRIES);
        assertTrue(state.selectedIndexByQuery.size() <= SearchHistory.MAX_TRACKED_ENTRIES);
        assertTrue(state.firstVisibleIndexByQuery.size() <= SearchHistory.MAX_TRACKED_ENTRIES);
        assertTrue(state.scrollYByQuery.size() <= SearchHistory.MAX_TRACKED_ENTRIES);
        assertNull(history.getSelectedEndpointKey("query-0"));
        assertNull(history.getSelectedIndex("query-0"));
    }

    private RestServiceItem createItem(String methodText, String url) {
        return new RestServiceItem(createMethod(), methodText, url);
    }

    private PsiMethod createMethod() {
        PsiJavaFile javaFile = (PsiJavaFile) myFixture.configureByText("ActivityAction.java", """
                package demo;

                public class ActivityAction {
                    public void rewardDetail() {}
                }
                """);
        PsiClass psiClass = javaFile.getClasses()[0];
        return psiClass.findMethodsByName("rewardDetail", false)[0];
    }
}
