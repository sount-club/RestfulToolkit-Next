package com.sount.restful.endpoint.navigation;

import com.intellij.navigation.ItemPresentation;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.project.Project;
import com.intellij.pom.Navigatable;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.impl.FakePsiElement;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import com.sount.restful.endpoint.model.EndpointDescriptor;
import org.jetbrains.annotations.Nullable;
import org.jspecify.annotations.NonNull;

import java.lang.reflect.Method;
import java.util.Arrays;

public class RestServiceItemTest extends BasePlatformTestCase {

    public void testNavigateUsesCachedNavigatableElement() {
        TrackingNavigatablePsiElement element = new TrackingNavigatablePsiElement(getProject());
        RestServiceItem item = new RestServiceItem(element, "GET", "/activity/list");

        assertTrue(item.tryNavigate(true));

        assertEquals(1, element.navigateCalls);
        assertTrue(element.lastRequestFocus);
    }

    public void testNavigationTargetOwnsPsiNavigation() {
        TrackingNavigatablePsiElement element = new TrackingNavigatablePsiElement(getProject());
        EndpointNavigationTarget target = new EndpointNavigationTarget(element);

        assertTrue(target.navigate(true));

        assertEquals(1, element.navigateCalls);
        assertSame(element, target.getPsiElement());
    }

    public void testInvalidPsiElementDoesNotPretendNavigationSucceeded() {
        TrackingNavigatablePsiElement element = new TrackingNavigatablePsiElement(getProject());
        RestServiceItem item = new RestServiceItem(element, "GET", "/activity/list");
        element.valid = false;

        assertFalse(item.tryNavigate(true));
        assertEquals(0, element.navigateCalls);
    }

    public void testRemovedMethodFallsBackToContainingFile() {
        PsiJavaFile file = (PsiJavaFile) myFixture.configureByText("FallbackController.java", """
                package demo;
                class FallbackController {
                    void endpoint() {}
                }
                """);
        PsiMethod method = file.getClasses()[0].findMethodsByName("endpoint", false)[0];
        RestServiceItem item = new RestServiceItem(method, "GET", "/fallback");

        WriteCommandAction.runWriteCommandAction(getProject(), method::delete);

        assertTrue(item.tryNavigate(false));
    }

    public void testRestServiceItemDoesNotExposeUnusedMutableSetters() {
        assertFalse(hasMethod("setPsiMethod"));
        assertFalse(hasMethod("setMethod"));
        assertFalse(hasMethod("setUrl"));
    }

    public void testDescriptorRemainsSingleSourceForSearchFields() {
        PsiJavaFile file = (PsiJavaFile) myFixture.configureByText("UserController.java", """
                package demo;
                class UserController {
                    void getUser() {}
                }
                """);
        PsiMethod method = file.getClasses()[0].findMethodsByName("getUser", false)[0];
        RestServiceItem item = new RestServiceItem(method, "GET", "/API/Users");
        item.setModule(getModule());

        EndpointDescriptor descriptor = item.getDescriptor();
        assertEquals(descriptor.endpointKey(), item.getEndpointKey());
        assertEquals(descriptor.searchSelectionKey(), item.getSearchSelectionKey());
        assertEquals(descriptor.searchableText(), item.getSearchableText());
        assertEquals(descriptor.lowerUrl(), item.getLowerUrl());
        assertEquals(descriptor.lowerMethodName(), item.getLowerMethodName());
        assertEquals(descriptor.lowerModuleName(), item.getLowerModuleName());
        assertEquals(descriptor.lowerControllerName(), item.getLowerControllerName());
        assertEquals(descriptor.lowerDescription(), item.getLowerDescription());
        assertEquals(descriptor.lowerMethodText(), item.getLowerHttpMethod());
    }

    public void testResolverCanApplyCachedModuleContextWithoutChangingDescriptorContract() {
        RestServiceItem item = new RestServiceItem(createMethod(), "GET", "/cached");

        item.setModule(getModule(), "/application");

        assertSame(getModule(), item.getModule());
        assertEquals("/application", item.getContextPath());
        assertEquals(getModule().getName(), item.getModuleName());
        assertEquals(item.getDescriptor().searchSelectionKey(), item.getSearchSelectionKey());
    }

    private PsiMethod createMethod() {
        PsiJavaFile file = (PsiJavaFile) myFixture.configureByText("CachedController.java", """
                package demo;
                class CachedController {
                    void cached() {}
                }
                """);
        return file.getClasses()[0].findMethodsByName("cached", false)[0];
    }

    private boolean hasMethod(String name) {
        return Arrays.stream(RestServiceItem.class.getMethods())
                .map(Method::getName)
                .anyMatch(name::equals);
    }

    private static final class TrackingNavigatablePsiElement extends FakePsiElement implements Navigatable {
        private final Project project;
        private int navigateCalls;
        private boolean lastRequestFocus;
        private boolean valid = true;

        private TrackingNavigatablePsiElement(Project project) {
            this.project = project;
        }

        @Override
        public void navigate(boolean requestFocus) {
            navigateCalls++;
            lastRequestFocus = requestFocus;
        }

        @Override
        public boolean canNavigate() {
            return true;
        }

        @Override
        public boolean canNavigateToSource() {
            return true;
        }

        @Override
        public boolean isValid() {
            return valid;
        }

        @Override
        public @NonNull Project getProject() {
            return project;
        }

        @Override
        public PsiFile getContainingFile() {
            return null;
        }

        @Override
        public PsiElement getParent() {
            return null;
        }

        @Override
        public @Nullable ItemPresentation getPresentation() {
            return null;
        }
    }
}
