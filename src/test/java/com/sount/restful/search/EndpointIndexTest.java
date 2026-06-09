package com.sount.restful.search;

import com.intellij.psi.PsiFile;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;

public class EndpointIndexTest extends BasePlatformTestCase {

    public void testJavaSourceFileWithoutRestAnnotationsCanInvalidateEndpointIndex() {
        PsiFile file = myFixture.configureByText("BaseController.java", """
                package demo;

                abstract class BaseController {
                    public String getById() { return null; }
                }
                """);

        assertTrue(EndpointIndex.canAffectEndpointIndex(file));
    }
}
