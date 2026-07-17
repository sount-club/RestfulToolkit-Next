package com.sount.restful.endpoint.resolver;

import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.project.Project;
import com.intellij.psi.JavaRecursiveElementVisitor;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiExpressionList;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiLiteralExpression;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiMethodCallExpression;
import com.intellij.psi.PsiMethodReferenceExpression;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.PsiSearchHelper;
import com.sount.restful.method.HttpMethod;
import com.sount.restful.method.RequestPath;
import com.sount.restful.endpoint.navigation.RestServiceItem;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

final class SpringWebFluxRouterFunctionResolver {
    private final SpringResolver owner;

    SpringWebFluxRouterFunctionResolver(@NotNull SpringResolver owner) {
        this.owner = owner;
    }

    @NotNull
    List<RestServiceItem> collect(@NotNull Project project, @NotNull GlobalSearchScope scope) {
        List<RestServiceItem> items = new ArrayList<>();
        Set<PsiJavaFile> candidateFiles = findCandidateFiles(project, scope);

        for (PsiJavaFile psiJavaFile : candidateFiles) {
            try {
                psiJavaFile.accept(new RouterFunctionVisitor(items));
            } catch (ProcessCanceledException e) {
                throw e;
            } catch (Throwable e) {
                SpringResolver.LOG.debug("Failed to inspect Java file for WebFlux router functions", e);
            }
        }

        return items;
    }

    private static @NotNull Set<PsiJavaFile> findCandidateFiles(@NotNull Project project,
                                                                 @NotNull GlobalSearchScope scope) {
        Set<PsiJavaFile> candidates = new LinkedHashSet<>();
        PsiSearchHelper searchHelper = PsiSearchHelper.getInstance(project);
        // A router can be declared through a static import or a RouterFunction-returning
        // bean, so searching only the fluent method names misses otherwise valid files.
        collectFilesContaining(searchHelper, "RouterFunction", scope, candidates);
        collectFilesContaining(searchHelper, "RouterFunctions", scope, candidates);
        collectFilesContaining(searchHelper, "route", scope, candidates);
        collectFilesContaining(searchHelper, "andRoute", scope, candidates);
        collectFilesContaining(searchHelper, "nest", scope, candidates);
        return candidates;
    }

    private static void collectFilesContaining(@NotNull PsiSearchHelper searchHelper,
                                               @NotNull String word,
                                               @NotNull GlobalSearchScope scope,
                                               @NotNull Set<PsiJavaFile> candidates) {
        searchHelper.processAllFilesWithWord(word, scope, psiFile -> {
            if (psiFile instanceof PsiJavaFile javaFile) {
                candidates.add(javaFile);
            }
            return true;
        }, true);
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
