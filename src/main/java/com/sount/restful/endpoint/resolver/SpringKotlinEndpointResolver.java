package com.sount.restful.endpoint.resolver;

import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.search.GlobalSearchScope;
import com.sount.restful.annotations.PathMappingAnnotation;
import com.sount.restful.annotations.SpringRequestMethodAnnotation;
import com.sount.restful.common.spring.RequestMappingAnnotationHelper;
import com.sount.restful.method.RequestPath;
import com.sount.restful.endpoint.navigation.RestServiceItem;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.kotlin.asJava.LightClassUtil;
import org.jetbrains.kotlin.asJava.LightClassUtilsKt;
import org.jetbrains.kotlin.idea.stubindex.KotlinAnnotationsIndex;
import org.jetbrains.kotlin.psi.*;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

final class SpringKotlinEndpointResolver {
    private final SpringResolver owner;

    SpringKotlinEndpointResolver(@NotNull SpringResolver owner) {
        this.owner = owner;
    }

    @NotNull
    List<RestServiceItem> collect(@NotNull PathMappingAnnotation controllerAnnotation,
                                  @NotNull Project project,
                                  @NotNull GlobalSearchScope globalSearchScope,
                                  @NotNull Set<KtClass> processedKtClasses) {
        List<RestServiceItem> items = new ArrayList<>();
        try {
            var ktAnnotationEntries = KotlinAnnotationsIndex.Helper.get(controllerAnnotation.getShortName(), project, globalSearchScope);
            SpringResolver.LOG.debug("Found " + ktAnnotationEntries.size() + " Kotlin @" + controllerAnnotation.getShortName() + " annotations");
            for (KtAnnotationEntry ktAnnotationEntry : ktAnnotationEntries) {
                PsiElement modifierList = ktAnnotationEntry.getParent();
                if (modifierList == null || !(modifierList.getParent() instanceof KtClass ktClass)) {
                    continue;
                }

                if (!processedKtClasses.add(ktClass)) continue;

                List<RequestPath> classRequestPaths = getRequestPaths(ktClass);
                if (classRequestPaths.isEmpty()) {
                    continue;
                }

                addDeclaredKotlinMethods(items, ktClass, classRequestPaths);
                addInheritedKotlinMethods(items, ktClass, classRequestPaths);
            }
        } catch (ProcessCanceledException e) {
            throw e;
        } catch (Throwable e) {
            SpringResolver.LOG.debug("Kotlin annotation index not available for @" + controllerAnnotation.getShortName(), e);
        }
        return items;
    }

    private void addDeclaredKotlinMethods(@NotNull List<RestServiceItem> items,
                                          @NotNull KtClass ktClass,
                                          @NotNull List<RequestPath> classRequestPaths) {
        for (KtNamedFunction fun : getKtNamedFunctions(ktClass)) {
            List<RequestPath> methodRequestPaths = getRequestPaths(fun);
            if (methodRequestPaths.isEmpty()) {
                continue;
            }

            for (RequestPath classRequestPath : classRequestPaths) {
                for (RequestPath methodRequestPath : methodRequestPaths) {
                    items.add(owner.createRestServiceItem(fun, classRequestPath.getPath(), methodRequestPath));
                }
            }
        }
    }

    private void addInheritedKotlinMethods(@NotNull List<RestServiceItem> items,
                                           @NotNull KtClass ktClass,
                                           @NotNull List<RequestPath> classRequestPaths) {
        Set<String> declaredMethodNames = new HashSet<>();
        for (KtNamedFunction fun : getKtNamedFunctions(ktClass)) {
            declaredMethodNames.add(fun.getName());
        }

        for (PsiMethod method : getAllKtMethods(ktClass)) {
            if (declaredMethodNames.contains(method.getName())) {
                continue;
            }
            RequestPath[] methodRequestPaths = RequestMappingAnnotationHelper.getRequestPaths(method);
            if (isEmpty(methodRequestPaths)) {
                continue;
            }

            for (RequestPath classRequestPath : classRequestPaths) {
                for (RequestPath methodRequestPath : methodRequestPaths) {
                    items.add(owner.createRestServiceItem(getKtMethodSourceElement(method), classRequestPath.getPath(), methodRequestPath));
                }
            }
        }
    }

    private List<KtNamedFunction> getKtNamedFunctions(KtClass ktClass) {
        List<KtNamedFunction> ktNamedFunctions = new ArrayList<>();
        for (KtDeclaration declaration : ktClass.getDeclarations()) {
            if (declaration instanceof KtNamedFunction fun) {
                ktNamedFunctions.add(fun);
            }
        }
        return ktNamedFunctions;
    }

    private List<PsiMethod> getAllKtMethods(KtClass ktClass) {
        List<PsiMethod> methods = new ArrayList<>();
        try {
            if (LightClassUtil.INSTANCE.canGenerateLightClass(ktClass)) {
                var lightClass = LightClassUtilsKt.toLightClass(ktClass);
                if (lightClass != null) {
                    methods.addAll(owner.getClassMethodsIncludingParents(lightClass));
                }
            }
        } catch (Exception e) {
            SpringResolver.LOG.debug("Failed to get Kotlin light class methods", e);
        }
        return methods;
    }

    private PsiElement getKtMethodSourceElement(PsiMethod method) {
        try {
            PsiElement unwrapped = LightClassUtilsKt.getUnwrapped(method);
            if (unwrapped instanceof KtNamedFunction) {
                return unwrapped;
            }
        } catch (Exception e) {
            SpringResolver.LOG.debug("Failed to unwrap Kotlin light method", e);
        }
        return method;
    }

    private boolean isEmpty(RequestPath[] requestPaths) {
        return requestPaths == null || requestPaths.length == 0;
    }

    private List<RequestPath> getRequestPaths(KtClass ktClass) {
        return getRequestPathsFromOwner(ktClass);
    }

    private List<RequestPath> getRequestPaths(KtNamedFunction fun) {
        return getRequestPathsFromOwner(fun);
    }

    private List<RequestPath> getRequestPathsFromOwner(KtModifierListOwner owner) {
        String defaultPath = "/";
        KtModifierList modifierList = owner.getModifierList();
        if (modifierList == null) {
            return new ArrayList<>();
        }
        return getRequestMappings(defaultPath, modifierList.getAnnotationEntries());
    }

    private List<RequestPath> getRequestMappings(String defaultPath, List<KtAnnotationEntry> annotationEntries) {
        List<RequestPath> requestPaths = new ArrayList<>();
        for (KtAnnotationEntry entry : annotationEntries) {
            requestPaths.addAll(getRequestMappings(defaultPath, entry));
        }
        return requestPaths;
    }

    private List<RequestPath> getRequestMappings(String defaultPath, KtAnnotationEntry entry) {
        List<RequestPath> requestPaths = new ArrayList<>();
        List<String> methodList = new ArrayList<>();
        List<String> pathList = new ArrayList<>();

        String annotationName = entry.getCalleeExpression().getText();
        SpringRequestMethodAnnotation requestMethodAnnotation = SpringRequestMethodAnnotation.getByShortName(annotationName);
        if (requestMethodAnnotation == null) {
            return new ArrayList<>();
        }

        if (requestMethodAnnotation.methodName() != null) {
            methodList.add(requestMethodAnnotation.methodName());
        } else {
            methodList.addAll(getAttributeValues(entry, "method"));
        }

        if (entry.getValueArgumentList() != null) {
            List<String> mappingValues = getAttributeValues(entry, null);
            if (!mappingValues.isEmpty()) {
                pathList.addAll(mappingValues);
            } else {
                pathList.addAll(getAttributeValues(entry, "value"));
            }
            pathList.addAll(getAttributeValues(entry, "path"));
        }

        if (pathList.isEmpty()) pathList.add(defaultPath);

        if (!methodList.isEmpty()) {
            for (String method : methodList) {
                for (String path : pathList) {
                    requestPaths.add(new RequestPath(path, method));
                }
            }
        } else {
            for (String path : pathList) {
                requestPaths.add(new RequestPath(path, null));
            }
        }

        return requestPaths;
    }

    private List<String> parseArgumentValues(KtExpression argumentExpression) {
        List<String> values = new ArrayList<>();
        // Guard casts with instanceof: getText()-based prefix checks are not a reliable
        // indicator of the PSI node type, so an unwrapped/dot-qualified expression that
        // merely starts with "arrayOf" or "[" would otherwise throw ClassCastException and
        // silently drop the whole controller's endpoints (caught upstream).
        if (argumentExpression instanceof KtCallExpression callExpression
                && argumentExpression.getText().startsWith("arrayOf")) {
            for (KtValueArgument pathValueArgument : callExpression.getValueArguments()) {
                values.add(pathValueArgument.getText().replace("\"", ""));
            }
        } else if (argumentExpression instanceof KtCollectionLiteralExpression collectionLiteral
                && argumentExpression.getText().startsWith("[")) {
            for (KtExpression ktExpression : collectionLiteral.getInnerExpressions()) {
                values.add(ktExpression.getText().replace("\"", ""));
            }
        } else {
            PsiElement[] paths = argumentExpression.getChildren();
            values.add(paths.length == 0 ? "" : paths[0].getText());
        }
        return values;
    }

    private List<String> getAttributeValues(KtAnnotationEntry entry, String attribute) {
        KtValueArgumentList valueArgumentList = entry.getValueArgumentList();
        if (valueArgumentList == null) return new ArrayList<>();

        for (KtValueArgument ktValueArgument : valueArgumentList.getArguments()) {
            KtValueArgumentName argumentName = ktValueArgument.getArgumentName();
            KtExpression argumentExpression = ktValueArgument.getArgumentExpression();
            if (argumentExpression == null) continue;

            if ((argumentName == null && attribute == null) || (argumentName != null && argumentName.getText().equals(attribute))) {
                return parseArgumentValues(argumentExpression);
            }
        }

        return new ArrayList<>();
    }
}
