package com.sount.restful.common.spring;

import com.intellij.psi.PsiAnnotation;
import junit.framework.TestCase;

import java.lang.reflect.Proxy;

public class RequestMappingAnnotationHelperTest extends TestCase {

    public void testUnsupportedWhenAnnotationQualifiedNameCannotBeResolved() {
        PsiAnnotation annotation = annotationThrowingFromQualifiedName();

        assertFalse(RequestMappingAnnotationHelper.isSupportedRequestMappingAnnotation(annotation));
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
