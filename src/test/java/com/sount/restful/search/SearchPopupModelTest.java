package com.sount.restful.search;

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiMethod;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import com.sount.restful.navigation.RestServiceItem;

import java.util.List;

public class SearchPopupModelTest extends BasePlatformTestCase {

    public void testFindSelectionIndexLivesOutsideSwingPopup() {
        RestServiceItem first = createItem("GET", "/activity/list", "UserActivityAction", "list");
        RestServiceItem second = createItem("GET", "/activity/list", "AdminActivityAction", "list");
        List<SearchResult> results = List.of(
                new SearchResult(first, 100, "url"),
                new SearchResult(second, 90, "url")
        );

        assertEquals(1, SearchPopupModel.findSelectionIndex(results, second.getSearchSelectionKey(), null));
    }

    public void testRecentResultsLivesOutsideSwingPopup() {
        List<RestServiceItem> items = java.util.stream.IntStream.range(0, 25)
                .mapToObj(i -> createItem("GET", "/activity/" + i, "ActivityAction" + i, "endpoint"))
                .toList();

        List<SearchResult> results = SearchPopupModel.buildRecentResults(items, item -> {
            String url = item.getUrl();
            return Long.parseLong(url.substring(url.lastIndexOf('/') + 1));
        });

        assertEquals(20, results.size());
        assertEquals("/activity/24", results.get(0).item().getUrl());
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
}
