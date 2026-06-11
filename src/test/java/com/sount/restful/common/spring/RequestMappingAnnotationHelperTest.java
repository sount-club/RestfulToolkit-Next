package com.sount.restful.common.spring;

import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.testFramework.fixtures.CodeInsightFixtureTestCase;
import com.sount.restful.method.HttpMethod;
import com.sount.restful.method.RequestPath;

import java.lang.reflect.Proxy;

public class RequestMappingAnnotationHelperTest extends CodeInsightFixtureTestCase {
    public void testUnsupportedWhenAnnotationQualifiedNameCannotBeResolved() {
        PsiAnnotation annotation = annotationThrowingFromQualifiedName();

        assertFalse(RequestMappingAnnotationHelper.isSupportedRequestMappingAnnotation(annotation));
    }

    public void testResolvesRequestMappingFromImplementedFeignClientInterfaceMethod() {
        addSpringAnnotationStubs();

        myFixture.addFileToProject("example/StoreController.java", """
                package example;

                import java.util.List;
                import org.springframework.cloud.openfeign.FeignClient;
                import org.springframework.web.bind.annotation.RequestMapping;
                import org.springframework.web.bind.annotation.RequestMethod;
                import org.springframework.web.bind.annotation.RestController;

                @FeignClient("stores")
                interface StoreClient {
                    @RequestMapping(method = RequestMethod.GET, value = "/stores")
                    List getStores();
                }

                @RestController
                class StoreController implements StoreClient {
                    public List getStores() {
                        return null;
                    }
                }
                """);

        PsiClass controller = JavaPsiFacade.getInstance(getProject()).findClass(
                "example.StoreController", GlobalSearchScope.projectScope(getProject()));
        assertNotNull(controller);

        PsiMethod[] methods = controller.findMethodsByName("getStores", false);
        assertEquals(1, methods.length);

        RequestPath[] requestPaths = RequestMappingAnnotationHelper.getRequestPaths(methods[0]);

        assertEquals(1, requestPaths.length);
        assertEquals("/stores", requestPaths[0].getPath());
        assertEquals("RequestMethod.GET", requestPaths[0].getMethod());
        assertEquals(HttpMethod.GET, HttpMethod.getByRequestMethod(requestPaths[0].getMethod()));
    }

    public void testResolvesClassRequestMappingFromImplementedInterface() {
        addSpringAnnotationStubs();

        myFixture.addFileToProject("example/StoreController.java", """
                package example;

                import org.springframework.web.bind.annotation.RequestMapping;
                import org.springframework.web.bind.annotation.RestController;

                @RequestMapping("/api")
                interface StoreApi {
                }

                @RestController
                class StoreController implements StoreApi {
                }
                """);

        PsiClass controller = findClass("example.StoreController");

        assertEquals("/api", RequestMappingAnnotationHelper.getRequestPaths(controller).get(0).getPath());
        assertEquals("/api", RequestMappingAnnotationHelper.getOneRequestMappingPath(controller));
    }

    public void testOneMethodPathUsesImplementedInterfaceMappingBeforeMethodNameFallback() {
        addSpringAnnotationStubs();

        myFixture.addFileToProject("example/StoreController.java", """
                package example;

                import java.util.List;
                import org.springframework.web.bind.annotation.RequestMapping;
                import org.springframework.web.bind.annotation.RequestMethod;
                import org.springframework.web.bind.annotation.RestController;

                interface StoreClient {
                    @RequestMapping(method = RequestMethod.GET, path = "/stores")
                    List getStores();
                }

                @RestController
                class StoreController implements StoreClient {
                    public List getStores() {
                        return null;
                    }
                }
                """);

        PsiMethod method = findClass("example.StoreController").findMethodsByName("getStores", false)[0];

        assertEquals("/stores", RequestMappingAnnotationHelper.getOneRequestMappingPath(method));
    }

    private void addSpringAnnotationStubs() {
        myFixture.addFileToProject("org/springframework/web/bind/annotation/RequestMapping.java", """
                package org.springframework.web.bind.annotation;

                public @interface RequestMapping {
                    RequestMethod[] method() default {};
                    String[] value() default {};
                    String[] path() default {};
                }
                """);
        myFixture.addFileToProject("org/springframework/web/bind/annotation/RequestMethod.java", """
                package org.springframework.web.bind.annotation;

                public enum RequestMethod {
                    GET
                }
                """);
        myFixture.addFileToProject("org/springframework/web/bind/annotation/RestController.java", """
                package org.springframework.web.bind.annotation;

                public @interface RestController {
                }
                """);
        myFixture.addFileToProject("org/springframework/cloud/openfeign/FeignClient.java", """
                package org.springframework.cloud.openfeign;

                public @interface FeignClient {
                    String value() default "";
                }
                """);
    }

    private PsiClass findClass(String qualifiedName) {
        PsiClass psiClass = JavaPsiFacade.getInstance(getProject()).findClass(
                qualifiedName, GlobalSearchScope.projectScope(getProject()));
        assertNotNull(psiClass);
        return psiClass;
    }

    private PsiAnnotation annotationThrowingFromQualifiedName() {
        return (PsiAnnotation) Proxy.newProxyInstance(
                getClass().getClassLoader(),
                new Class[]{PsiAnnotation.class},
                (proxy, method, args) -> {
                    if ("getQualifiedName".equals(method.getName())) {
                        throw new RuntimeException("Outdated stub in index");
                    }
                    if ("toString".equals(method.getName())) {
                        return "BrokenPsiAnnotation";
                    }
                    return null;
                }
        );
    }
}
