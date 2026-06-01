package com.sount.restful.search;

import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.ui.EditorTextField;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import com.sount.restful.common.PsiMethodHelper;

import javax.swing.JComponent;
import javax.swing.JScrollPane;
import java.awt.Component;
import java.awt.Container;

public class UnifiedSearchPreviewTest extends BasePlatformTestCase {

    public void testCleanJavadocRemovesCommentMarkers() {
        String raw = """
                /**
                 * 查询活动奖励配置详情
                 * @param id 活动 ID
                 */""";

        assertEquals("查询活动奖励配置详情\n@param id 活动 ID", UnifiedSearchPreview.cleanJavadoc(raw));
    }

    public void testDisplayOrEmptyUsesFallbackForBlankText() {
        assertEquals("No query parameters", UnifiedSearchPreview.displayOrEmpty("", "No query parameters"));
        assertEquals("id=1", UnifiedSearchPreview.displayOrEmpty("id=1", "No query parameters"));
    }

    public void testBuildSourceTextCanAvoidPackageLookupOnEdt() {
        assertEquals("ActivityAction#rewardDetail  [admin]",
                UnifiedSearchPreview.buildSourceText("ActivityAction#rewardDetail", "admin", ""));
    }

    public void testPreviewDoesNotUseOuterScrollPane() {
        UnifiedSearchPreview preview = new UnifiedSearchPreview(getProject());

        assertFalse(preview.getComponent(0) instanceof JScrollPane);
    }

    public void testMethodCodeSectionReplacesRequestSchemaSections() {
        UnifiedSearchPreview preview = new UnifiedSearchPreview(getProject());

        JComponent methodCodeSection = findComponentByName(preview, "methodCodeSection");
        JComponent requestBodySection = findComponentByName(preview, "requestBodySection");

        assertNotNull(methodCodeSection);
        assertNull(requestBodySection);
    }

    public void testMethodCodePreviewUsesEditorTextField() {
        UnifiedSearchPreview preview = new UnifiedSearchPreview(getProject());

        EditorTextField editor = findComponentByType(preview, EditorTextField.class);

        assertNotNull(editor);
        assertTrue(editor.isViewer());
    }

    public void testMethodCodeEditorDisablesSoftWraps() {
        UnifiedSearchPreview preview = new UnifiedSearchPreview(getProject());
        EditorTextField editor = findComponentByType(preview, EditorTextField.class);

        assertNotNull(editor);
        preview.addNotify();
        EditorEx editorEx = editor.getEditor(true);

        assertNotNull(editorEx);
        assertFalse(editorEx.getSettings().isUseSoftWraps());
        assertTrue(editorEx.getSettings().isLineNumbersShown());
        preview.removeNotify();
    }

    public void testMethodCodeTextComesFromPsiElement() {
        var javaFile = myFixture.configureByText("LoginAction.java", """
                package demo;

                class LoginAction {
                    void login(String username) {
                        System.out.println(username);
                    }
                }
                """);
        var loginMethod = ((com.intellij.psi.PsiJavaFile) javaFile)
                .getClasses()[0]
                .findMethodsByName("login", false)[0];

        String methodCode = UnifiedSearchPreview.buildMethodCode(loginMethod);

        assertTrue(methodCode.contains("void login(String username)"));
        assertTrue(methodCode.contains("System.out.println(username);"));
    }

    public void testMethodCodeFileTypeComesFromContainingFile() {
        var javaFile = myFixture.configureByText("LoginAction.java", """
                package demo;

                class LoginAction {
                    void login() {}
                }
                """);
        var loginMethod = ((com.intellij.psi.PsiJavaFile) javaFile)
                .getClasses()[0]
                .findMethodsByName("login", false)[0];

        assertEquals(javaFile.getFileType(), UnifiedSearchPreview.resolveMethodCodeFileType(loginMethod));
    }

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
