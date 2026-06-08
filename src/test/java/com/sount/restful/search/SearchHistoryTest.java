package com.sount.restful.search;

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiMethod;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import com.sount.restful.navigation.action.RestServiceItem;

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

    private RestServiceItem createItem(String methodText, String url) {
        PsiJavaFile javaFile = (PsiJavaFile) myFixture.configureByText("ActivityAction.java", """
                package demo;

                public class ActivityAction {
                    public void rewardDetail() {}
                }
                """);
        PsiClass psiClass = javaFile.getClasses()[0];
        PsiMethod method = psiClass.findMethodsByName("rewardDetail", false)[0];
        return new RestServiceItem(method, methodText, url);
    }
}
