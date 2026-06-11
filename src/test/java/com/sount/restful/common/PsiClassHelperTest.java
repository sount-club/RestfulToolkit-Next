package com.sount.restful.common;

import com.intellij.psi.PsiJavaFile;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;

public class PsiClassHelperTest extends BasePlatformTestCase {
    public void testConvertClassToJsonHandlesListFieldsAsArrays() {
        PsiJavaFile javaFile = (PsiJavaFile) myFixture.configureByText("Store.java", """
                package demo;

                import java.util.List;

                class Store {
                    String name;
                    List<String> tags;
                    List<Address> addresses;
                }

                class Address {
                    String city;
                }
                """);

        String json = PsiClassHelper.create(javaFile.getClasses()[0]).convertClassToJSON(getProject(), true);

        assertTrue(json.contains("\"tags\": ["));
        assertTrue(json.contains("\"demoData\""));
        assertTrue(json.contains("\"addresses\": ["));
        assertTrue(json.contains("\"city\": \"demoData\""));
    }

    public void testConvertClassToJsonStopsSelfRecursion() {
        PsiJavaFile javaFile = (PsiJavaFile) myFixture.configureByText("Node.java", """
                package demo;

                import java.util.List;

                class Node {
                    String name;
                    Node parent;
                    List<Node> children;
                }
                """);

        String json = PsiClassHelper.create(javaFile.getClasses()[0]).convertClassToJSON(getProject(), true);

        assertTrue(json.contains("\"parent\""));
        assertTrue(json.contains("\"children\""));
        assertTrue(json.length() < 1000);
    }

    public void testJavaBaseTypeDefaultValueSupportsJavaTimeTypes() {
        assertEquals("2024-01-01", PsiClassHelper.getJavaBaseTypeDefaultValue("LocalDate"));
        assertEquals("12:00:00", PsiClassHelper.getJavaBaseTypeDefaultValue("LocalTime"));
        assertEquals("2024-01-01T12:00:00", PsiClassHelper.getJavaBaseTypeDefaultValue("LocalDateTime"));
    }
}
