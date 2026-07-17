package com.sount.restful.endpoint.resolver;

import com.intellij.testFramework.fixtures.BasePlatformTestCase;

public class EndpointResolverRegistryTest extends BasePlatformTestCase {

    public void testProjectRegistryKeepsSupportedResolversInOnePlace() {
        CompositeEndpointResolver resolver = EndpointResolverRegistry.forProject(getProject());

        assertEquals(2, resolver.strategies().size());
        assertTrue(resolver.strategies().get(0) instanceof SpringResolver);
        assertTrue(resolver.strategies().get(1) instanceof JaxrsResolver);
    }

    public void testModuleRegistryKeepsSupportedResolversInOnePlace() {
        CompositeEndpointResolver resolver = EndpointResolverRegistry.forModule(getModule());

        assertEquals(2, resolver.strategies().size());
        assertTrue(resolver.strategies().get(0) instanceof SpringResolver);
        assertTrue(resolver.strategies().get(1) instanceof JaxrsResolver);
    }
}
