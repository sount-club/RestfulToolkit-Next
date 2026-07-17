package com.sount.restful.search.application;

import com.sount.restful.method.HttpMethod;
import junit.framework.TestCase;

public class SearchRequestTest extends TestCase {

    public void testUiMethodFilterIsAppliedWhenQueryHasNoMethod() {
        SearchRequest request = SearchRequest.create("users", null, HttpMethod.GET, 200);

        assertEquals(HttpMethod.GET, request.query().methodFilter());
        assertFalse(request.isRecentMode());
    }

    public void testExplicitQueryMethodKeepsPriorityOverUiFilter() {
        SearchRequest request = SearchRequest.create("POST users", null, HttpMethod.GET, 200);

        assertEquals(HttpMethod.POST, request.query().methodFilter());
        assertEquals(HttpMethod.GET, request.methodFilter());
    }

    public void testOnlyEmptyTextWithoutMethodUsesRecentMode() {
        assertTrue(SearchRequest.create("", null, null, 200).isRecentMode());
        assertFalse(SearchRequest.create("", null, HttpMethod.GET, 200).isRecentMode());
    }
}
