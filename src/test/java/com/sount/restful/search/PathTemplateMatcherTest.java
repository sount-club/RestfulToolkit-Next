package com.sount.restful.search;

import junit.framework.TestCase;

import java.util.List;

public class PathTemplateMatcherTest extends TestCase {

    public void testMatchesPathVariableAgainstRealRequestValue() {
        assertTrue(PathTemplateMatcher.matches("/users/{id}", "/users/123"));
        assertTrue(PathTemplateMatcher.matches("/orders/{id:\\d+}", "/orders/42"));
        assertFalse(PathTemplateMatcher.matches("/users/{id}", "/users/123/profile"));
    }

    public void testMatchesSpringWildcards() {
        assertTrue(PathTemplateMatcher.matches("/assets/*/detail", "/assets/image/detail"));
        assertTrue(PathTemplateMatcher.matches("/assets/**", "/assets/image/icons/logo.svg"));
        assertTrue(PathTemplateMatcher.matches("/assets/{*path}", "/assets/image/icons/logo.svg"));
    }

    public void testKeepsRawPathAndAddsGatewayAndContextNormalizedCandidates() {
        List<String> candidates = PathTemplateMatcher.candidates("/gateway/app/users/123", "/app", List.of("/gateway"));

        assertEquals(List.of("/gateway/app/users/123", "/app/users/123", "/users/123"), candidates);
    }

    public void testOnlyStripsPrefixesOnSegmentBoundaries() {
        List<String> candidates = PathTemplateMatcher.candidates("/gateway-api/users", "", List.of("/gateway"));

        assertEquals(List.of("/gateway-api/users"), candidates);
    }

    public void testParsesGatewayPrefixConfiguration() {
        assertEquals(List.of("/gateway", "/api"), PathSearchOptions.parse("gateway, /api\n/gateway/").gatewayPrefixes());
    }
}
