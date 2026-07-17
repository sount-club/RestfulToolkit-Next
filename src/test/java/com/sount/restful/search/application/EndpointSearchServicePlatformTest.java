package com.sount.restful.search.application;

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiMethod;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import com.sount.restful.endpoint.navigation.RestServiceItem;
import com.sount.restful.search.domain.PathSearchOptions;
import com.sount.restful.search.domain.SearchResult;

import java.util.List;

/**
 * 覆盖搜索应用服务的模块过滤、计数和最近访问结果契约。
 */
public class EndpointSearchServicePlatformTest extends BasePlatformTestCase {

    public void testComputationFiltersModuleBeforeCountingAndScoring() {
        RestServiceItem included = createItem("GET", "/api/users", "UserController", "users");
        included.setModule(getModule(), "");
        RestServiceItem excluded = createItem("GET", "/other/users", "OtherController", "users");
        SearchRequest request = SearchRequest.create("users", getModule(), null, 200);

        SearchResponse response = EndpointSearchService.computeResponse(request,
                List.of(included, excluded), true, item -> 0L, item -> 0, PathSearchOptions.EMPTY);

        assertEquals(1, response.totalCount());
        assertEquals(1, response.results().size());
        assertSame(included, response.results().get(0).item());
        assertTrue(response.indexReady());
        assertEquals(List.of("users"), response.highlightTokens());
    }

    public void testRecentResultsKeepsOnlyTopTwentyByLastAccessTime() {
        List<RestServiceItem> items = java.util.stream.IntStream.range(0, 25)
                .mapToObj(i -> createItem("GET", "/activity/" + i, "ActivityAction" + i, "endpoint"))
                .toList();

        List<SearchResult<RestServiceItem>> results = EndpointSearchService.buildRecentResults(items, item -> {
            String url = item.getUrl();
            return Long.parseLong(url.substring(url.lastIndexOf('/') + 1));
        });

        assertEquals(20, results.size());
        assertEquals("/activity/24", results.get(0).item().getUrl());
        assertEquals("/activity/5", results.get(19).item().getUrl());
    }

    /**
     * 创建带稳定类名和方法名的端点测试对象。
     */
    private RestServiceItem createItem(String methodText, String url, String className, String methodName) {
        PsiJavaFile javaFile = (PsiJavaFile) myFixture.configureByText(
                url.replace("/", "_") + "_" + className + ".java", """
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
