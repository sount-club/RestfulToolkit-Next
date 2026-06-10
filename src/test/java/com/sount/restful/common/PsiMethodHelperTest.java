package com.sount.restful.common;

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiMethod;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;

public class PsiMethodHelperTest extends BasePlatformTestCase {

    public void testNonRestMethodReturnsNullServicePathsWithoutThrowing() {
        PsiJavaFile javaFile = (PsiJavaFile) myFixture.configureByText("PlainService.java", """
                package demo;

                public class PlainService {
                    public String calculate() {
                        return "";
                    }
                }
                """);
        PsiClass psiClass = javaFile.getClasses()[0];
        PsiMethod method = psiClass.findMethodsByName("calculate", false)[0];
        PsiMethodHelper helper = PsiMethodHelper.create(method);

        assertNull(helper.buildServiceUriPath());
        assertNull(helper.buildServiceUriPathWithParams());
        assertNull(helper.buildFullUrl());
        assertNull(helper.buildFullUrlWithParams());
    }
}
