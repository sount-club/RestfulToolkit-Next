package com.sount.restful.search;

import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import com.sount.restful.common.PsiMethodHelper;

import javax.swing.*;
import java.awt.*;


public class UnifiedSearchPreviewTest extends BasePlatformTestCase {


    public void testRequestBodyJsonIsBuiltFromRequestBodyParameter() {
        myFixture.configureByText("RequestBody.java", """
                package org.springframework.web.bind.annotation;
                public @interface RequestBody {}
                """);
        var javaFile = myFixture.configureByText("LoginAction.java", """
                package demo;
                
                import org.springframework.web.bind.annotation.RequestBody;
                
                class LoginAction {
                    void login(@RequestBody LoginRequest request) {}
                }
                
                class LoginRequest {
                    String username;
                    boolean rememberMe;
                }
                """);

        var loginMethod = ((com.intellij.psi.PsiJavaFile) javaFile)
                .getClasses()[0]
                .findMethodsByName("login", false)[0];

        String requestBody = PsiMethodHelper.create(loginMethod).buildRequestBodyJson();

        assertTrue(requestBody.contains("\"username\": \"demoData\""));
        assertTrue(requestBody.contains("\"rememberMe\": true"));
    }

    public void testRestControllerComplexParameterIsTreatedAsRequestBody() {
        myFixture.configureByText("RestController.java", """
                package org.springframework.web.bind.annotation;
                public @interface RestController {}
                """);
        var javaFile = myFixture.configureByText("LoginAction.java", """
                package demo;
                
                import org.springframework.web.bind.annotation.RestController;
                
                @RestController
                class LoginAction {
                    void login(LoginRequest request, String source) {}
                }
                
                class LoginRequest {
                    String username;
                    boolean rememberMe;
                }
                """);

        var loginMethod = ((com.intellij.psi.PsiJavaFile) javaFile)
                .getClasses()[0]
                .findMethodsByName("login", false)[0];

        PsiMethodHelper helper = PsiMethodHelper.create(loginMethod);
        String requestBody = helper.buildRequestBodyJson();
        String params = helper.buildParamString();

        assertTrue(requestBody.contains("\"username\": \"demoData\""));
        assertTrue(requestBody.contains("\"rememberMe\": true"));
        assertEquals("source=demoData", params);
    }

    public void testControllerComplexParameterIsNotImplicitRequestBody() {
        myFixture.configureByText("Controller.java", """
                package org.springframework.stereotype;
                public @interface Controller {}
                """);
        var javaFile = myFixture.configureByText("LoginAction.java", """
                package demo;
                
                import org.springframework.stereotype.Controller;
                
                @Controller
                class LoginAction {
                    void login(LoginRequest request) {}
                }
                
                class LoginRequest {
                    String username;
                }
                """);

        var loginMethod = ((com.intellij.psi.PsiJavaFile) javaFile)
                .getClasses()[0]
                .findMethodsByName("login", false)[0];

        PsiMethodHelper helper = PsiMethodHelper.create(loginMethod);

        assertNull(helper.buildRequestBodyJson());
        assertEquals("username=demoData", helper.buildParamString());
    }

    private static JComponent findComponentByName(Container container, String name) {
        for (Component component : container.getComponents()) {
            if (component instanceof JComponent jComponent && name.equals(jComponent.getName())) {
                return jComponent;
            }
            if (component instanceof Container childContainer) {
                JComponent found = findComponentByName(childContainer, name);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    private static <T extends Component> T findComponentByType(Container container, Class<T> type) {
        for (Component component : container.getComponents()) {
            if (type.isInstance(component)) {
                return type.cast(component);
            }
            if (component instanceof Container childContainer) {
                T found = findComponentByType(childContainer, type);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }
}
