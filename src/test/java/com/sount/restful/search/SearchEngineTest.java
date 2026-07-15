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

    public void testRealRequestPathMatchesEndpointPathTemplate() {
        RestServiceItem item = createItem("GET", "/api/users/{id}", "UserController", "getUser");

        List<SearchResult> results = SearchEngine.search(SearchQuery.parse("GET /api/users/123"), List.of(item));

        assertEquals(1, results.size());
        assertSame(item, results.get(0).item());
    }

    public void testOriginalPathSearchRemainsFallbackWhenTemplateDoesNotMatch() {
        RestServiceItem item = createItem("GET", "/gateway-api/users", "UserController", "getUsers");

        List<SearchResult> results = SearchEngine.search(SearchQuery.parse("/gateway-api/users"), List.of(item));

        assertEquals(1, results.size());
        assertSame(item, results.get(0).item());
    }

    public void testGatewayPrefixDoesNotStripTheEndpointPathCandidate() {
        RestServiceItem item = createItem("GET", "/user/tryxx", "UserController", "tryxx");

        List<SearchResult> results = SearchEngine.search(SearchQuery.parse("/user/user/tryxx"), List.of(item),
                200, null, PathSearchOptions.parse("/user,/admin,/trade,/im,/game,/api"));

        assertEquals(1, results.size());
        assertSame(item, results.get(0).item());
    }

    public void testPartialPathMatchesAfterGatewayPrefixIsStripped() {
        RestServiceItem item = createItem("GET", "/user/tryxx", "UserController", "tryxx");

        List<SearchResult> results = SearchEngine.search(SearchQuery.parse("/user/user/try"), List.of(item),
                200, null, PathSearchOptions.parse("/user"));

        assertEquals(1, results.size());
        assertSame(item, results.get(0).item());
    }

    public void testRepeatedPrefixDoesNotMatchAnUnrelatedEndpointBySuffix() {
        RestServiceItem imItem = createItem("GET", "/im/center", "ImController", "center");
        RestServiceItem userItem = createItem("GET", "/user/center", "UserController", "center");

        List<SearchResult> results = SearchEngine.search(SearchQuery.parse("/im/im/cen"), List.of(imItem, userItem),
                200, null, PathSearchOptions.parse("/im"));

        assertEquals(1, results.size());
        assertSame(imItem, results.get(0).item());
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
