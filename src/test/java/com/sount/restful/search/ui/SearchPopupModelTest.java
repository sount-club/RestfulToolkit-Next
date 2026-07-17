package com.sount.restful.search.ui;

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiMethod;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import com.sount.restful.endpoint.navigation.RestServiceItem;
import com.sount.restful.search.domain.SearchResult;

import java.util.List;

import static com.sount.restful.search.ui.SearchResultTestFactory.result;

public class SearchPopupModelTest extends BasePlatformTestCase {

    public void testFindSelectionIndexLivesOutsideSwingPopup() {
        RestServiceItem first = createItem("GET", "/activity/list", "UserActivityAction", "list");
        RestServiceItem second = createItem("GET", "/activity/list", "AdminActivityAction", "list");
        List<SearchResult<RestServiceItem>> results = List.of(
                result(first, 100, "url"),
                result(second, 90, "url")
        );

        assertEquals(1, SearchPopupModel.findSelectionIndex(results, second.getSearchSelectionKey(), null));
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
