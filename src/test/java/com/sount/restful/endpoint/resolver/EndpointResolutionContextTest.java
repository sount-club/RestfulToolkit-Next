package com.sount.restful.endpoint.resolver;

import com.intellij.testFramework.fixtures.BasePlatformTestCase;

import java.util.concurrent.atomic.AtomicInteger;

public class EndpointResolutionContextTest extends BasePlatformTestCase {

    public void testLoadsModuleContextPathOnlyOncePerResolution() {
        AtomicInteger loadCount = new AtomicInteger();
        EndpointResolutionContext context = new EndpointResolutionContext(module -> {
            loadCount.incrementAndGet();
            return "/api";
        });

        assertEquals("/api", context.getContextPath(getModule()));
        assertEquals("/api", context.getContextPath(getModule()));
        assertEquals(1, loadCount.get());
    }

    public void testNullModuleAndLoaderResultAreNormalized() {
        EndpointResolutionContext context = new EndpointResolutionContext(module -> null);

        assertEquals("", context.getContextPath(null));
        assertEquals("", context.getContextPath(getModule()));
        assertEquals("", context.getContextPath(getModule()));
    }
}
