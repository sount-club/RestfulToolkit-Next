package com.sount.restful.navigation;

import com.intellij.navigation.ItemPresentation;
import com.intellij.openapi.project.Project;
import com.intellij.pom.Navigatable;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.impl.FakePsiElement;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import org.jetbrains.annotations.Nullable;
import org.jspecify.annotations.NonNull;

import java.lang.reflect.Method;
import java.util.Arrays;

public class RestServiceItemTest extends BasePlatformTestCase {

    public void testNavigateUsesCachedNavigatableElement() {
        TrackingNavigatablePsiElement element = new TrackingNavigatablePsiElement(getProject());
        RestServiceItem item = new RestServiceItem(element, "GET", "/activity/list");

        item.navigate(true);

        assertEquals(1, element.navigateCalls);
        assertTrue(element.lastRequestFocus);
    }

    public void testNavigationTargetOwnsPsiNavigation() {
        TrackingNavigatablePsiElement element = new TrackingNavigatablePsiElement(getProject());
        EndpointNavigationTarget target = new EndpointNavigationTarget(element);

        target.navigate(true);

        assertEquals(1, element.navigateCalls);
        assertSame(element, target.getPsiElement());
    }

    public void testRestServiceItemDoesNotExposeUnusedMutableSetters() {
        assertFalse(hasMethod("setPsiMethod"));
        assertFalse(hasMethod("setMethod"));
        assertFalse(hasMethod("setUrl"));
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
            return true;
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
