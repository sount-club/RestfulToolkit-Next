package com.sount.restful.search.domain;

import com.sount.restful.method.HttpMethod;
import junit.framework.TestCase;

public class SearchQueryTest extends TestCase {

    // --- Full URL extraction ---

    public void testFullUrlExtractsPathOnly() {
        SearchQuery query = SearchQuery.parse("http://localhost:8080/api/users");

        assertEquals("/api/users", query.urlPattern());
        assertNull(query.methodFilter());
    }

    public void testFullUrlStripsQueryString() {
        SearchQuery query = SearchQuery.parse("http://localhost:8080/api/users?id=123&name=test");

        assertEquals("/api/users", query.urlPattern());
    }

    public void testFullUrlStripsFragment() {
        SearchQuery query = SearchQuery.parse("http://localhost:8080/api/users#list");

        assertEquals("/api/users", query.urlPattern());
    }

    public void testFullUrlStripsQueryAndFragment() {
        SearchQuery query = SearchQuery.parse("https://example.com/api/v2/orders?page=1#top");

        assertEquals("/api/v2/orders", query.urlPattern());
    }

    public void testFullUrlWithNoPathReturnsSlash() {
        SearchQuery query = SearchQuery.parse("http://localhost:8080");

        assertEquals("/", query.urlPattern());
    }

    public void testFullUrlWithPathOnlyNoTrailingSlash() {
        SearchQuery query = SearchQuery.parse("https://api.example.com/");

        assertEquals("/", query.urlPattern());
    }

    public void testHttpsUrlExtractsPath() {
        SearchQuery query = SearchQuery.parse("https://production.example.com/api/v1/users/42");

        assertEquals("/api/v1/users/42", query.urlPattern());
    }

    public void testFullUrlWithPortExtractsPath() {
        SearchQuery query = SearchQuery.parse("http://127.0.0.1:3000/internal/health");

        assertEquals("/internal/health", query.urlPattern());
    }

    // --- Method prefix with URL ---

    public void testMethodPrefixWithFullUrl() {
        SearchQuery query = SearchQuery.parse("GET http://localhost:8080/api/users");

        assertEquals(HttpMethod.GET, query.methodFilter());
        assertEquals("/api/users", query.urlPattern());
    }

    public void testMethodPrefixWithFullUrlAndQueryParams() {
        SearchQuery query = SearchQuery.parse("POST https://example.com/api/orders?status=pending");

        assertEquals(HttpMethod.POST, query.methodFilter());
        assertEquals("/api/orders", query.urlPattern());
    }

    // --- Non-URL input unchanged ---

    public void testPlainPathUnchanged() {
        SearchQuery query = SearchQuery.parse("/api/users");

        assertEquals("/api/users", query.urlPattern());
    }

    public void testClassMethodPatternUnchanged() {
        SearchQuery query = SearchQuery.parse("UserController#getUser");

        assertEquals("UserController", query.classNamePattern());
        assertEquals("getUser", query.methodNamePattern());
    }

    public void testNonHttpProtocolNotTreatedAsUrl() {
        SearchQuery query = SearchQuery.parse("ftp://files.example.com/data");

        // "ftp://" doesn't start with "http://" or "https://", so treated as plain text
        assertEquals("ftp://files.example.com/data", query.urlPattern());
    }
}
