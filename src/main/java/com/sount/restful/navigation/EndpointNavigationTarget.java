package com.sount.restful.navigation;

import com.intellij.ide.util.EditSourceUtil;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.Navigatable;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.Nullable;

final class EndpointNavigationTarget {
    private static final Logger LOG = Logger.getInstance(EndpointNavigationTarget.class);
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
            // EditSourceUtil.getDescriptor / canNavigate trigger AST loading, which runs a
            // stub-index consistency check that can throw UpToDateStubIndexMismatch (a
            // platform-level index/PSI inconsistency) even though isValid() passed. Each
            // attempt is guarded so navigation degrades gracefully instead of crashing the
            // key handler on the EDT.
            Navigatable descriptor = safeGetDescriptor();
            if (descriptor != null) return descriptor;
            if (fallbackNavigatable != null && safeCanNavigate(fallbackNavigatable)) {
                return fallbackNavigatable;
            }
            return resolveFileFallback();
        });
    }

    private @Nullable Navigatable safeGetDescriptor() {
        try {
            Navigatable descriptor = EditSourceUtil.getDescriptor(psiElement);
            if (descriptor != null && descriptor.canNavigate()) return descriptor;
        } catch (ProcessCanceledException e) {
            throw e;
        } catch (RuntimeException e) {
            LOG.warn("Failed to resolve navigation descriptor for " + psiElement
                    + " (stub/index may be inconsistent); falling back", e);
        }
        return null;
    }

    private boolean safeCanNavigate(Navigatable navigatable) {
        try {
            return navigatable.canNavigate();
        } catch (ProcessCanceledException e) {
            throw e;
        } catch (RuntimeException e) {
            LOG.warn("Fallback navigable canNavigate failed", e);
            return false;
        }
    }

    /** Last-resort fallback: open the containing file without a precise (AST-derived) offset. */
    private @Nullable Navigatable resolveFileFallback() {
        try {
            PsiFile file = psiElement.getContainingFile();
            if (file == null) return null;
            VirtualFile vFile = file.getVirtualFile();
            if (vFile == null) return null;
            return new OpenFileDescriptor(psiElement.getProject(), vFile);
        } catch (ProcessCanceledException e) {
            throw e;
        } catch (RuntimeException e) {
            LOG.warn("File-level navigation fallback also failed", e);
            return null;
        }
    }
}
