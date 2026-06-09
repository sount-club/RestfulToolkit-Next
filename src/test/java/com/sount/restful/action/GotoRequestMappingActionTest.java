package com.sount.restful.action;

import junit.framework.TestCase;

public class GotoRequestMappingActionTest extends TestCase {

    public void testExtractPathFromLocalhostUrl() {
        assertEquals("/api/users", GotoRequestMappingAction.extractPath("http://localhost:8080/api/users"));
    }

    public void testExtractPathStripsQueryParams() {
        assertEquals("/api/users", GotoRequestMappingAction.extractPath("http://localhost:8080/api/users?id=1&name=foo"));
    }

    public void testExtractPathStripsFragment() {
        assertEquals("/api/users", GotoRequestMappingAction.extractPath("http://localhost:8080/api/users#section"));
    }

    public void testExtractPathStripsQueryAndFragment() {
        assertEquals("/api/orders", GotoRequestMappingAction.extractPath("https://example.com/api/orders?page=1#top"));
    }

    public void testExtractPathFromHttpsUrl() {
        assertEquals("/v2/health", GotoRequestMappingAction.extractPath("https://api.example.com/v2/health"));
    }

    public void testExtractPathNoPathReturnsSlash() {
        assertEquals("/", GotoRequestMappingAction.extractPath("http://localhost:8080"));
    }

    public void testExtractPathRootPath() {
        assertEquals("/", GotoRequestMappingAction.extractPath("http://localhost:8080/"));
    }

    public void testExtractPathWithPort() {
        assertEquals("/internal/status", GotoRequestMappingAction.extractPath("http://127.0.0.1:3000/internal/status"));
    }

    public void testExtractPathDeepPath() {
        assertEquals("/api/v1/users/42/posts",
                GotoRequestMappingAction.extractPath("http://localhost:8080/api/v1/users/42/posts"));
    }
}
