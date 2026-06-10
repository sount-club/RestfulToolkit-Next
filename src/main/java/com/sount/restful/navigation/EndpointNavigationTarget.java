package com.sount.restful.navigation;

import com.intellij.ide.util.EditSourceUtil;
import com.intellij.openapi.application.ReadAction;
import com.intellij.pom.Navigatable;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.Nullable;

final class EndpointNavigationTarget {
    private final PsiElement psiElement;
    private final Navigatable fallbackNavigatable;

    EndpointNavigationTarget(@Nullable PsiElement psiElement) {
        this.psiElement = psiElement;
        this.fallbackNavigatable = psiElement instanceof Navigatable navigatable ? navigatable : null;
    }

    @Nullable
    PsiElement getPsiElement() {
        return psiElement;
    }

    void navigate(boolean requestFocus) {
        Navigatable navigatable = resolveNavigatable();
        if (navigatable != null) {
            navigatable.navigate(requestFocus);
        }
    }

    boolean canNavigate() {
        return resolveNavigatable() != null;
    }

    private @Nullable Navigatable resolveNavigatable() {
        return ReadAction.computeBlocking(() -> {
            if (psiElement == null || !psiElement.isValid()) return null;
            Navigatable descriptor = EditSourceUtil.getDescriptor(psiElement);
            if (descriptor != null && descriptor.canNavigate()) return descriptor;
            return fallbackNavigatable != null && fallbackNavigatable.canNavigate() ? fallbackNavigatable : null;
        });
    }
}
