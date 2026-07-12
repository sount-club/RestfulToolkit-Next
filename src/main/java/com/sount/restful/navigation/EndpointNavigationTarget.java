package com.sount.restful.navigation;

import com.intellij.ide.util.EditSourceUtil;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.Navigatable;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.SmartPointerManager;
import com.intellij.psi.SmartPsiElementPointer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

final class EndpointNavigationTarget {
    private static final Logger LOG = Logger.getInstance(EndpointNavigationTarget.class);
    private final SmartPsiElementPointer<PsiElement> elementPointer;
    private final Project project;
    private final VirtualFile fallbackFile;

    EndpointNavigationTarget(@Nullable PsiElement psiElement) {
        this.elementPointer = psiElement != null
                ? SmartPointerManager.getInstance(psiElement.getProject()).createSmartPsiElementPointer(psiElement)
                : null;
        this.project = psiElement != null ? psiElement.getProject() : null;
        PsiFile containingFile = psiElement != null ? psiElement.getContainingFile() : null;
        this.fallbackFile = containingFile != null ? containingFile.getVirtualFile() : null;
    }

    @Nullable
    PsiElement getPsiElement() {
        return elementPointer != null ? elementPointer.getElement() : null;
    }

    boolean navigate(boolean requestFocus) {
        try {
            Navigatable navigatable = resolveNavigatable();
            if (navigatable == null) {
                return false;
            }
            navigatable.navigate(requestFocus);
            return true;
        } catch (ProcessCanceledException e) {
            throw e;
        } catch (RuntimeException e) {
            LOG.warn("Failed to navigate to REST endpoint", e);
            return false;
        }
    }

    boolean canNavigate() {
        try {
            return resolveNavigatable() != null;
        } catch (ProcessCanceledException e) {
            throw e;
        } catch (RuntimeException e) {
            LOG.warn("Failed to check REST endpoint navigation target", e);
            return false;
        }
    }

    private @Nullable Navigatable resolveNavigatable() {
        return ReadAction.computeBlocking(() -> {
            PsiElement psiElement = getPsiElement();
            if (psiElement == null || !psiElement.isValid()) return resolveFileFallback();
            // EditSourceUtil.getDescriptor / canNavigate trigger AST loading, which runs a
            // stub-index consistency check that can throw UpToDateStubIndexMismatch (a
            // platform-level index/PSI inconsistency) even though isValid() passed. Each
            // attempt is guarded so navigation degrades gracefully instead of crashing the
            // key handler on the EDT.
            Navigatable descriptor = safeGetDescriptor(psiElement);
            if (descriptor != null) return descriptor;
            if (psiElement instanceof Navigatable navigatable && safeCanNavigate(navigatable)) {
                return navigatable;
            }
            return resolveFileFallback();
        });
    }

    private @Nullable Navigatable safeGetDescriptor(@NotNull PsiElement psiElement) {
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
            if (project == null || fallbackFile == null || !fallbackFile.isValid()) return null;
            return new OpenFileDescriptor(project, fallbackFile);
        } catch (ProcessCanceledException e) {
            throw e;
        } catch (RuntimeException e) {
            LOG.warn("File-level navigation fallback also failed", e);
            return null;
        }
    }
}
