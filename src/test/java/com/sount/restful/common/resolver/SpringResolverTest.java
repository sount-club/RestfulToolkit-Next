package com.sount.restful.common.resolver;

import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import com.sount.restful.navigation.action.RestServiceItem;

import java.util.List;

public class SpringResolverTest extends BasePlatformTestCase {

    public void testFindAllEndpointsSkipsControllerAnnotationsOutsideClasses() {
        myFixture.configureByText("Controller.java", """
                package org.springframework.stereotype;
                public @interface Controller {}
                """);
        myFixture.configureByText("RestController.java", """
                package org.springframework.web.bind.annotation;
                public @interface RestController {}
                """);
        myFixture.configureByText("GetMapping.java", """
                package org.springframework.web.bind.annotation;
                public @interface GetMapping {
                    String[] value() default {};
                    String[] path() default {};
                }
                """);
        myFixture.configureByText("DemoController.java", """
                package demo;

                import org.springframework.stereotype.Controller;
                import org.springframework.web.bind.annotation.GetMapping;
                import org.springframework.web.bind.annotation.RestController;

                @RestController
                class DemoController {
                    @GetMapping("/api/users")
                    public String users() {
                        return "";
                    }

                    @Controller
                    public void helper() {}
                }
                """);

        List<RestServiceItem> endpoints = BaseServiceResolver.findAllEndpoints(getProject());

        assertEquals(1, endpoints.size());
        assertEquals("/api/users", endpoints.get(0).getUrl());
    }
}
