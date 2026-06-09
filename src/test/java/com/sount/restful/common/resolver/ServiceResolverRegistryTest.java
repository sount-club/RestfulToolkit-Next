package com.sount.restful.common.resolver;

import com.intellij.testFramework.fixtures.BasePlatformTestCase;

public class ServiceResolverRegistryTest extends BasePlatformTestCase {

    public void testProjectRegistryKeepsSupportedResolversInOnePlace() {
        ServiceResolver[] resolvers = ServiceResolverRegistry.forProject(getProject());

        assertEquals(2, resolvers.length);
        assertTrue(resolvers[0] instanceof SpringResolver);
        assertTrue(resolvers[1] instanceof JaxrsResolver);
    }

    public void testModuleRegistryKeepsSupportedResolversInOnePlace() {
        ServiceResolver[] resolvers = ServiceResolverRegistry.forModule(getModule());

        assertEquals(2, resolvers.length);
        assertTrue(resolvers[0] instanceof SpringResolver);
        assertTrue(resolvers[1] instanceof JaxrsResolver);
    }
}
