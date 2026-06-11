package com.sount.restful.method;

import junit.framework.TestCase;

public class RequestPathTest extends TestCase {
    public void testConcatNormalizesSlashes() {
        RequestPath requestPath = new RequestPath("/stores", "GET");

        requestPath.concat(new RequestPath("/api/", null));

        assertEquals("/api/stores", requestPath.getPath());
        assertEquals("GET", requestPath.getMethod());
    }

    public void testConcatKeepsRootMethodPathAtClassPath() {
        RequestPath requestPath = new RequestPath("/", "GET");

        requestPath.concat(new RequestPath("/api", null));

        assertEquals("/api/", requestPath.getPath());
    }
}
