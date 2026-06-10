package com.sount.restful.common;

import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiModifierList;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;

import java.lang.reflect.Proxy;

public class PsiAnnotationHelperTest extends BasePlatformTestCase {

    public void testAttributeValuesReturnEmptyListForMissingAnnotation() {
        assertTrue(PsiAnnotationHelper.getAnnotationAttributeValues(null, "value").isEmpty());
        assertNull(PsiAnnotationHelper.getAnnotationAttributeValue(null, "value"));
    }

    public void testQualifiedNameReturnsNullWhenPsiResolutionFails() {
        PsiAnnotation annotation = annotationThrowingFromQualifiedName();

        assertNull(PsiAnnotationHelper.getQualifiedName(annotation));
        assertFalse(PsiAnnotationHelper.hasQualifiedName(annotation, "javax.ws.rs.GET"));
    }

    public void testFindAnnotationReturnsNullForMissingModifierList() {
        assertNull(PsiAnnotationHelper.findAnnotation(null, "org.springframework.web.bind.annotation.GetMapping"));
    }

    public void testFindAnnotationMatchesSafely() {
        myFixture.configureByText("GetMapping.java", """
                package org.springframework.web.bind.annotation;
                public @interface GetMapping {}
                """);
        PsiJavaFile javaFile = (PsiJavaFile) myFixture.configureByText("UserController.java", """
                package demo;
                import org.springframework.web.bind.annotation.GetMapping;

                public class UserController {
                    @GetMapping
                    public void users() {}
                }
                """);
        PsiClass psiClass = javaFile.getClasses()[0];
        PsiModifierList modifierList = psiClass.findMethodsByName("users", false)[0].getModifierList();

        assertNotNull(PsiAnnotationHelper.findAnnotation(modifierList, "org.springframework.web.bind.annotation.GetMapping"));
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
