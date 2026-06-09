package com.sount.restful.search;

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiMethod;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import com.sount.restful.navigation.RestServiceItem;

import java.util.List;

public class SearchEngineTest extends BasePlatformTestCase {

    public void testClassMethodQueryMatchesControllerAndMethodFields() {
        RestServiceItem item = createItem("GET", "/api/users", "UserController", "getUser");

        List<SearchResult> results = SearchEngine.search(SearchQuery.parse("UserController#getUser"), List.of(item));

        assertEquals(1, results.size());
        assertSame(item, results.get(0).item());
    }

    public void testClassAndMethodTokensMatchControllerAndMethodFields() {
        RestServiceItem item = createItem("GET", "/api/users", "UserController", "getUser");

        List<SearchResult> results = SearchEngine.search(SearchQuery.parse("UserController getUser"), List.of(item));

        assertEquals(1, results.size());
        assertSame(item, results.get(0).item());
    }

    public void testClassOnlyQueryMatchesControllerField() {
        RestServiceItem item = createItem("GET", "/api/users", "UserController", "getUser");

        List<SearchResult> results = SearchEngine.search(SearchQuery.parse("UserController"), List.of(item));

        assertEquals(1, results.size());
        assertSame(item, results.get(0).item());
    }

    public void testMethodOnlyQueryMatchesMethodField() {
        RestServiceItem item = createItem("GET", "/api/users", "UserController", "getUser");

        List<SearchResult> results = SearchEngine.search(SearchQuery.parse("getUser"), List.of(item));

        assertEquals(1, results.size());
        assertSame(item, results.get(0).item());
    }

    private RestServiceItem createItem(String methodText, String url, String className, String methodName) {
        PsiJavaFile javaFile = (PsiJavaFile) myFixture.configureByText(className + ".java", """
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
