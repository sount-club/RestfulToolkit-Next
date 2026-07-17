package com.sount.restful.search.domain;

import com.sount.restful.endpoint.model.EndpointDescriptor;
import com.sount.restful.method.HttpMethod;
import junit.framework.TestCase;

import java.util.List;
import java.util.Set;

public class SearchEngineTest extends TestCase {

    public void testClassMethodQueryMatchesControllerAndMethodFields() {
        SearchDocument<Object> item = createDocument("GET", "/api/users", "UserController", "getUser");

        List<SearchResult<Object>> results = SearchEngine.search(
                SearchQuery.parse("UserController#getUser"), List.of(item));

        assertEquals(1, results.size());
        assertSame(item.item(), results.get(0).item());
    }

    public void testClassAndMethodTokensMatchControllerAndMethodFields() {
        SearchDocument<Object> item = createDocument("GET", "/api/users", "UserController", "getUser");

        List<SearchResult<Object>> results = SearchEngine.search(
                SearchQuery.parse("UserController getUser"), List.of(item));

        assertEquals(1, results.size());
        assertSame(item.item(), results.get(0).item());
    }

    public void testClassOnlyQueryMatchesControllerField() {
        SearchDocument<Object> item = createDocument("GET", "/api/users", "UserController", "getUser");

        List<SearchResult<Object>> results = SearchEngine.search(SearchQuery.parse("UserController"), List.of(item));

        assertEquals(1, results.size());
        assertSame(item.item(), results.get(0).item());
    }

    public void testMethodOnlyQueryMatchesMethodField() {
        SearchDocument<Object> item = createDocument("GET", "/api/users", "UserController", "getUser");

        List<SearchResult<Object>> results = SearchEngine.search(SearchQuery.parse("getUser"), List.of(item));

        assertEquals(1, results.size());
        assertSame(item.item(), results.get(0).item());
    }

    public void testRealRequestPathMatchesEndpointPathTemplate() {
        SearchDocument<Object> item = createDocument("GET", "/api/users/{id}", "UserController", "getUser");

        List<SearchResult<Object>> results = SearchEngine.search(
                SearchQuery.parse("GET /api/users/123"), List.of(item));

        assertEquals(1, results.size());
        assertSame(item.item(), results.get(0).item());
    }

    public void testOriginalPathSearchRemainsFallbackWhenTemplateDoesNotMatch() {
        SearchDocument<Object> item = createDocument("GET", "/gateway-api/users", "UserController", "getUsers");

        List<SearchResult<Object>> results = SearchEngine.search(
                SearchQuery.parse("/gateway-api/users"), List.of(item));

        assertEquals(1, results.size());
        assertSame(item.item(), results.get(0).item());
    }

    public void testGatewayPrefixDoesNotStripTheEndpointPathCandidate() {
        SearchDocument<Object> item = createDocument("GET", "/user/tryxx", "UserController", "tryxx");

        List<SearchResult<Object>> results = SearchEngine.search(SearchQuery.parse("/user/user/tryxx"), List.of(item),
                200, null, PathSearchOptions.parse("/user,/admin,/trade,/im,/game,/api"));

        assertEquals(1, results.size());
        assertSame(item.item(), results.get(0).item());
    }

    public void testPartialPathMatchesAfterGatewayPrefixIsStripped() {
        SearchDocument<Object> item = createDocument("GET", "/user/tryxx", "UserController", "tryxx");

        List<SearchResult<Object>> results = SearchEngine.search(SearchQuery.parse("/user/user/try"), List.of(item),
                200, null, PathSearchOptions.parse("/user"));

        assertEquals(1, results.size());
        assertSame(item.item(), results.get(0).item());
    }

    public void testRepeatedPrefixDoesNotMatchAnUnrelatedEndpointBySuffix() {
        SearchDocument<Object> imItem = createDocument("GET", "/im/center", "ImController", "center");
        SearchDocument<Object> userItem = createDocument("GET", "/user/center", "UserController", "center");

        List<SearchResult<Object>> results = SearchEngine.search(
                SearchQuery.parse("/im/im/cen"), List.of(imItem, userItem),
                200, null, PathSearchOptions.parse("/im"));

        assertEquals(1, results.size());
        assertSame(imItem.item(), results.get(0).item());
    }

    public void testMatchedFieldsKeepControllerAndMethodDimensions() {
        SearchDocument<Object> item = createDocument("GET", "/api/users", "UserController", "getUser");

        List<SearchResult<Object>> results = SearchEngine.search(
                SearchQuery.parse("UserController getUser"), List.of(item));

        assertEquals(1, results.size());
        assertEquals(Set.of(MatchField.CONTROLLER_NAME, MatchField.METHOD_NAME),
                results.get(0).matchedFields());
    }

    public void testPathAndMethodFilterKeepExistingScoreAndFields() {
        SearchDocument<Object> item = createDocument("GET", "/api/users", "UserController", "getUsers");

        List<SearchResult<Object>> results = SearchEngine.search(
                SearchQuery.parse("GET /api/users"), List.of(item));

        assertEquals(1, results.size());
        SearchResult<Object> result = results.get(0);
        assertEquals(275, result.score());
        assertEquals(Set.of(MatchField.HTTP_METHOD, MatchField.PATH), result.matchedFields());
    }

    public void testUseCountBonusRemainsCappedAtFiftyPoints() {
        SearchDocument<Object> item = createDocument("GET", "/api/users", "UserController", "getUser");

        List<SearchResult<Object>> results = SearchEngine.search(
                SearchQuery.parse("getUser"), List.of(item), 200, ignored -> 500);

        assertEquals(1, results.size());
        assertEquals(140, results.get(0).score());
    }

    public void testPartialHttpMethodTokenKeepsExistingTextMatch() {
        SearchDocument<Object> item = createDocument("GET", "/api/users", "UserController", "listUsers");

        List<SearchResult<Object>> results = SearchEngine.search(
                SearchQuery.parse("users ge"), List.of(item));

        assertEquals(1, results.size());
        assertSame(item.item(), results.get(0).item());
        assertEquals(95, results.get(0).score());
    }

    public void testBoundedSearchRetainsHighestScoringResult() {
        SearchDocument<Object> exact = createDocument("GET", "/api/exact", "UserController", "findUser");
        SearchDocument<Object> contains = createDocument("GET", "/api/contains", "UserController", "findUsers");

        List<SearchResult<Object>> results = SearchEngine.search(
                SearchQuery.parse("findUser"), List.of(exact, contains), 1);

        assertEquals(1, results.size());
        assertSame(exact.item(), results.get(0).item());
        assertEquals(90, results.get(0).score());
    }

    public void testEmptyQueryKeepsAlphabeticalOrderingAndResultLimit() {
        SearchDocument<Object> itemB = createDocument("GET", "/b", "BController", "findB");
        SearchDocument<Object> itemA = createDocument("GET", "/a", "AController", "findA");
        SearchDocument<Object> itemC = createDocument("GET", "/c", "CController", "findC");

        List<SearchResult<Object>> results = SearchEngine.search(
                SearchQuery.EMPTY, List.of(itemB, itemA, itemC), 2);

        assertEquals(2, results.size());
        assertSame(itemA.item(), results.get(0).item());
        assertSame(itemB.item(), results.get(1).item());
    }

    /**
     * 创建完全不依赖 IntelliJ PSI 的领域搜索文档。
     */
    private SearchDocument<Object> createDocument(
            String methodText, String url, String className, String methodName) {
        EndpointDescriptor descriptor = new EndpointDescriptor(
                HttpMethod.getByRequestMethod(methodText), methodText, url, className, methodName,
                "demo", "", "");
        return new SearchDocument<>(new Object(), descriptor, "");
    }
}
