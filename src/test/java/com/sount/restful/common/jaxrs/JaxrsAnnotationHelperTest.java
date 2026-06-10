package com.sount.restful.common.jaxrs;

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiMethod;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;

public class JaxrsAnnotationHelperTest extends BasePlatformTestCase {

    public void testHttpMethodWithoutPathUsesMethodName() {
        myFixture.configureByText("GET.java", """
                package javax.ws.rs;
                public @interface GET {}
                """);
        PsiJavaFile javaFile = (PsiJavaFile) myFixture.configureByText("UserResource.java", """
                package demo;

                import javax.ws.rs.GET;

                public class UserResource {
                    @GET
                    public String findUser() {
                        return "";
                    }
                }
                """);
        PsiClass psiClass = javaFile.getClasses()[0];

        PsiMethod method = psiClass.findMethodsByName("findUser", false)[0];

        assertEquals("findUser", JaxrsAnnotationHelper.getMethodUriPath(method));
    }
}
