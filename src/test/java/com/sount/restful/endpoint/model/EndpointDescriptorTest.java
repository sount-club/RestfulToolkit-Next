package com.sount.restful.endpoint.model;

import com.sount.restful.method.HttpMethod;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class EndpointDescriptorTest {

    @Test
    public void descriptorBuildsStableKeysAndSearchTextWithoutPsi() {
        EndpointDescriptor descriptor = new EndpointDescriptor(
                HttpMethod.GET,
                "GET",
                "/api/users",
                "UserController",
                "getUser",
                "demo.user",
                "Find user",
                "app"
        );

        assertEquals("GET:/api/users", descriptor.endpointKey());
        assertEquals("GET:/api/users:UserController#getUser:app", descriptor.searchSelectionKey());
        assertTrue(descriptor.searchableText().contains("/api/users"));
        assertTrue(descriptor.searchableText().contains("usercontroller"));
        assertTrue(descriptor.searchableText().contains("find user"));
    }

    @Test
    public void descriptorReturnsCachedLowercaseAndSearchTextInstances() {
        EndpointDescriptor descriptor = new EndpointDescriptor(
                HttpMethod.POST,
                "POST",
                "/API/Users",
                "UserController",
                "CreateUser",
                "demo.user",
                "Create User",
                "AdminModule"
        );

        assertSame(descriptor.lowerUrl(), descriptor.lowerUrl());
        assertSame(descriptor.lowerMethodName(), descriptor.lowerMethodName());
        assertSame(descriptor.lowerModuleName(), descriptor.lowerModuleName());
        assertSame(descriptor.searchableText(), descriptor.searchableText());
    }
}
