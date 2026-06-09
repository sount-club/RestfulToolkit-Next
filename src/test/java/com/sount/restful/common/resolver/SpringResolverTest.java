package com.sount.restful.common.resolver;

import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import com.sount.restful.navigation.RestServiceItem;
import org.jetbrains.kotlin.psi.KtNamedFunction;

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

    public void testFindAllEndpointsPreservesDuplicatePathsFromDifferentControllers() {
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
        myFixture.configureByText("UserController.java", """
                package demo;

                import org.springframework.web.bind.annotation.GetMapping;
                import org.springframework.web.bind.annotation.RestController;

                @RestController
                class UserController {
                    @GetMapping("/api/users")
                    public String users() {
                        return "";
                    }
                }
                """);
        myFixture.configureByText("AdminController.java", """
                package demo;

                import org.springframework.web.bind.annotation.GetMapping;
                import org.springframework.web.bind.annotation.RestController;

                @RestController
                class AdminController {
                    @GetMapping("/api/users")
                    public String users() {
                        return "";
                    }
                }
                """);

        List<RestServiceItem> endpoints = BaseServiceResolver.findAllEndpoints(getProject());

        assertEquals(2, endpoints.size());
    }

    public void testFindEndpointsFromSuperClass() {
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
        myFixture.configureByText("RequestMapping.java", """
                package org.springframework.web.bind.annotation;
                public @interface RequestMapping {
                    String[] value() default {};
                    String[] path() default {};
                }
                """);
        // Parent class with endpoints - same package as child
        myFixture.configureByText("UserController.java", """
                package demo;

                import org.springframework.web.bind.annotation.GetMapping;
                import org.springframework.web.bind.annotation.RequestMapping;
                import org.springframework.web.bind.annotation.RestController;

                abstract class BaseController {
                    @GetMapping("/{id}")
                    public String getById() { return null; }
                }

                @RestController
                @RequestMapping("/users")
                public class UserController extends BaseController {
                    @GetMapping("/special")
                    public String special() { return ""; }
                }
                """);

        List<RestServiceItem> endpoints = BaseServiceResolver.findAllEndpoints(getProject());

        // Should find 2 endpoints: /users/{id} (inherited), /users/special (own)
        assertEquals(2, endpoints.size());

        List<String> urls = endpoints.stream()
                .map(RestServiceItem::getUrl)
                .toList();
        assertTrue("Should contain /users/{id}", urls.contains("/users/{id}"));
        assertTrue("Should contain /users/special", urls.contains("/users/special"));
    }

    public void testObjectMethodsAreNotIncluded() {
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
        myFixture.configureByText("SimpleController.java", """
                package demo;

                import org.springframework.web.bind.annotation.GetMapping;
                import org.springframework.web.bind.annotation.RestController;

                @RestController
                public class SimpleController {
                    @GetMapping("/api/test")
                    public String test() {
                        return "";
                    }
                }
                """);

        List<RestServiceItem> endpoints = BaseServiceResolver.findAllEndpoints(getProject());

        // Should only find 1 endpoint, not Object methods like equals, hashCode, toString
        assertEquals(1, endpoints.size());
        assertEquals("/api/test", endpoints.get(0).getUrl());
    }

    public void testKotlinEndpointsKeepSourceFunctionElement() {
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
        myFixture.configureByText("RequestMapping.java", """
                package org.springframework.web.bind.annotation;
                public @interface RequestMapping {
                    String[] value() default {};
                    String[] path() default {};
                }
                """);
        myFixture.configureByText("UserController.kt", """
                package demo

                import org.springframework.web.bind.annotation.GetMapping
                import org.springframework.web.bind.annotation.RequestMapping
                import org.springframework.web.bind.annotation.RestController

                @RestController
                @RequestMapping("/users")
                class UserController {
                    @GetMapping("/source")
                    fun source(): String = ""
                }
                """);

        List<RestServiceItem> endpoints = BaseServiceResolver.findAllEndpoints(getProject());

        RestServiceItem endpoint = endpoints.stream()
                .filter(item -> "/users/source".equals(item.getUrl()))
                .findFirst()
                .orElseThrow();
        assertTrue(endpoint.getPsiElement() instanceof KtNamedFunction);
    }
}
