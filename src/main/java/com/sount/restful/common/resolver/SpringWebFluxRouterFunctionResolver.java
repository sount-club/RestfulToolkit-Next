package com.sount.restful.common.resolver;

import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.JavaRecursiveElementVisitor;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiExpressionList;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiLiteralExpression;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiMethodCallExpression;
import com.intellij.psi.PsiMethodReferenceExpression;
import com.intellij.psi.search.FileTypeIndex;
import com.intellij.psi.search.GlobalSearchScope;
import com.sount.restful.method.HttpMethod;
import com.sount.restful.method.RequestPath;
import com.sount.restful.navigation.RestServiceItem;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

final class SpringWebFluxRouterFunctionResolver {
    private final SpringResolver owner;

    SpringWebFluxRouterFunctionResolver(@NotNull SpringResolver owner) {
        this.owner = owner;
    }

    @NotNull
    List<RestServiceItem> collect(@NotNull Project project, @NotNull GlobalSearchScope scope) {
        List<RestServiceItem> items = new ArrayList<>();
        Collection<VirtualFile> javaFiles = FileTypeIndex.getFiles(JavaFileType.INSTANCE, scope);
        PsiManager psiManager = PsiManager.getInstance(project);

        for (VirtualFile javaFile : javaFiles) {
            try {
                PsiFile psiFile = psiManager.findFile(javaFile);
                if (psiFile instanceof PsiJavaFile psiJavaFile) {
                    psiJavaFile.accept(new RouterFunctionVisitor(items));
                }
            } catch (ProcessCanceledException e) {
                throw e;
            } catch (Throwable e) {
                SpringResolver.LOG.debug("Failed to inspect Java file for WebFlux router functions", e);
            }
        }

        return items;
    }

    private final class RouterFunctionVisitor extends JavaRecursiveElementVisitor {
        private final List<RestServiceItem> items;

        private RouterFunctionVisitor(List<RestServiceItem> items) {
            this.items = items;
        }

        @Override
        public void visitMethodCallExpression(@NotNull PsiMethodCallExpression expression) {
            super.visitMethodCallExpression(expression);

            if (!isRouterFunctionCall(expression)) {
                return;
            }

            RequestPath requestPath = getRequestPath(expression);
            if (requestPath == null) {
                return;
            }

            PsiElement navigationElement = getHandlerTarget(expression);
            items.add(owner.createRestServiceItem(navigationElement != null ? navigationElement : expression,
                    "", requestPath));
        }
    }

    private static boolean isRouterFunctionCall(PsiMethodCallExpression expression) {
        String methodName = expression.getMethodExpression().getReferenceName();
        return "route".equals(methodName) || "andRoute".equals(methodName);
    }

    private static @Nullable RequestPath getRequestPath(PsiMethodCallExpression routeCall) {
        PsiExpression[] routeArguments = getArguments(routeCall);
        if (routeArguments.length == 0 || !(routeArguments[0] instanceof PsiMethodCallExpression predicateCall)) {
            return null;
        }

        String methodName = predicateCall.getMethodExpression().getReferenceName();
        HttpMethod httpMethod = HttpMethod.getByRequestMethod(methodName);
        if (httpMethod == null) {
            return null;
        }

        PsiExpression[] predicateArguments = getArguments(predicateCall);
        if (predicateArguments.length == 0 || !(predicateArguments[0] instanceof PsiLiteralExpression literal)) {
            return null;
        }

        Object value = literal.getValue();
        if (!(value instanceof String path) || path.isEmpty()) {
            return null;
        }

        return new RequestPath(path, httpMethod.name());
    }

    private static PsiExpression[] getArguments(PsiMethodCallExpression expression) {
        PsiExpressionList argumentList = expression.getArgumentList();
        return argumentList.getExpressions();
    }

    private static @Nullable PsiElement getHandlerTarget(PsiMethodCallExpression routeCall) {
        PsiExpression[] routeArguments = getArguments(routeCall);
        if (routeArguments.length < 2) {
            return null;
        }

        if (routeArguments[1] instanceof PsiMethodReferenceExpression methodReference) {
            PsiElement resolved = methodReference.resolve();
            if (resolved instanceof PsiMethod) {
                return resolved;
            }
        }

        return null;
    }
}
