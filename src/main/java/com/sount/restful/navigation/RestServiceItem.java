package com.sount.restful.navigation;

import com.intellij.ide.util.EditSourceUtil;
import com.intellij.navigation.ItemPresentation;
import com.intellij.navigation.NavigationItem;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.module.Module;
import com.intellij.pom.Navigatable;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.codeStyle.MinusculeMatcher;
import com.intellij.psi.codeStyle.NameUtil;
import com.intellij.util.text.matching.MatchingMode;
import com.sount.restful.common.PsiAnnotationHelper;
import com.sount.restful.common.ToolkitIcons;
import com.sount.restful.method.HttpMethod;
import com.sount.restful.method.ModuleHelper;
import com.sount.restful.search.EndpointDescriptor;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.kotlin.psi.KtClass;
import org.jetbrains.kotlin.psi.KtNamedFunction;

import javax.swing.*;
import java.util.Locale;
import java.util.Objects;

//RequestMappingNavigationItem
public class RestServiceItem implements NavigationItem {
    private PsiMethod psiMethod; //元素
    private final PsiElement psiElement; //元素
    private Module module;

    private final String requestMethod; //请求方法 get/post...
    private HttpMethod method;  //请求方法 get/post...

    private String url; //url mapping;

    private Navigatable navigationElement;
    private String cachedLocationText; // pre-computed at construction time (inside read action)
    private String cachedJavadoc;      // pre-computed at construction time
    private String cachedModuleName;   // pre-computed at construction time
    private final String cachedPackageName;  // pre-computed at construction time
    private final String cachedDescription;    // pre-computed at construction time
    private final String cachedControllerName; // pre-computed at construction time
    private final String cachedMethodName;     // pre-computed at construction time
    private String cachedSearchSelectionKey;
    private EndpointDescriptor descriptor;

    public RestServiceItem(PsiElement psiElement, String requestMethod, String urlPath) {
        this.psiElement = psiElement;
        if (psiElement instanceof PsiMethod) {
            this.psiMethod = (PsiMethod) psiElement;
        }
        this.requestMethod = requestMethod;
        if (requestMethod != null) {
            method = HttpMethod.getByRequestMethod(requestMethod);
        }

        this.url = urlPath;
        if (psiElement instanceof Navigatable) {
            navigationElement = (Navigatable) psiElement;
        }
        // Pre-compute all PSI-dependent fields at construction time (inside read action)
        this.cachedLocationText = computeLocationText();
        this.cachedJavadoc = computeJavadoc();
        this.cachedPackageName = computePackageName();
        this.cachedControllerName = computeControllerName();
        this.cachedMethodName = computeMethodName();
        this.cachedDescription = computeDescription();
        rebuildDescriptor();
    }

    @Nullable
    @Override
    public String getName() {
        return this.url;
    }

    @Nullable
    @Override
    public ItemPresentation getPresentation() {
        return new RestServiceItemPresentation();
    }

    @Override
    public void navigate(boolean requestFocus) {
        // PSI access (isValid, canNavigate, getTextOffset) requires read lock.
        // Compute descriptor inside ReadAction, navigate outside.
        // EditSourceUtil.getDescriptor() → getTextOffset() needs read lock.
        // OpenFileDescriptor.navigate() uses WriteIntentReadAction internally.
        Navigatable navDescriptor = ReadAction.computeBlocking(() -> {
            if (psiElement == null || !psiElement.isValid()) return null;
            Navigatable descriptor = EditSourceUtil.getDescriptor(psiElement);
            if (descriptor != null) return descriptor;
            return navigationElement != null && navigationElement.canNavigate() ? navigationElement : null;
        });
        if (navDescriptor != null) {
            navDescriptor.navigate(requestFocus);
        }
    }

    @Override
    public boolean canNavigate() {
        if (navigationElement != null) {
            // PSI access (isValid, canNavigate) requires read lock
            return ReadAction.computeBlocking(() -> psiElement != null && psiElement.isValid() && navigationElement.canNavigate());
        }
        if (psiElement == null) return false;
        return ReadAction.computeBlocking(() -> {
            if (!psiElement.isValid()) return false;
            Navigatable descriptor = EditSourceUtil.getDescriptor(psiElement);
            return descriptor != null && descriptor.canNavigate();
        });
    }

    @Override
    public boolean canNavigateToSource() {
        return canNavigate();
    }


    /*匹配*/
    public boolean matches(String queryText) {
        String pattern = queryText == null ? "" : queryText.trim();
        if (pattern.isEmpty() || pattern.equals("/")) return true;

        String normalizedPattern = pattern.toLowerCase(Locale.ROOT);
        if (containsIgnoreCase(getMethodText(), normalizedPattern)
                || containsIgnoreCase(getLocationText(), normalizedPattern)
                || containsIgnoreCase(getModuleName(), normalizedPattern)
                || containsIgnoreCase(getDescription(), normalizedPattern)
                || containsIgnoreCase(getControllerName(), normalizedPattern)
                || containsIgnoreCase(getMethodName(), normalizedPattern)) {
            return true;
        }

        MinusculeMatcher matcher = NameUtil.buildMatcher("*" + pattern)
                .withMatchingMode(MatchingMode.IGNORE_CASE)
                .build();
        return matcher.matches(this.url);
    }

    private class RestServiceItemPresentation implements ItemPresentation {
        @Nullable
        @Override
        public String getPresentableText() {
            return url;
        }

        @Nullable
        @Override
        public String getLocationString() {
            // Use pre-computed cached value to avoid PSI access on EDT without ReadAction.
            // IntelliJ's tree renderer may call this during paint/layout on EDT.
            String location = cachedLocationText != null ? cachedLocationText : "";
            return "(" + location + ")";
        }

        @Nullable
        @Override
        public Icon getIcon(boolean unused) {
//            System.out.println(unused + "  " + this.getPresentableText());
            return ToolkitIcons.METHOD.get(method);
        }
    }

    public Module getModule() {
        return module;
    }

    public PsiMethod getPsiMethod() {
        return psiMethod;
    }

    public void setPsiMethod(PsiMethod psiMethod) {
        this.psiMethod = psiMethod;
    }

    public HttpMethod getMethod() {
        return method;
    }

    public void setMethod(HttpMethod method) {
        this.method = method;
        rebuildDescriptor();
        this.cachedSearchSelectionKey = null;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
        rebuildDescriptor();
        this.cachedSearchSelectionKey = null;
    }

    public String getFullUrl() {
        if (module == null) {
            return getUrl();
        }

        ModuleHelper moduleHelper = ModuleHelper.create(module);
        return moduleHelper.getServiceHostPrefix() + getUrl();
    }

    public void setModule(Module module) {
        this.module = module;
        this.cachedModuleName = module != null ? module.getName() : "";
        rebuildDescriptor();
        this.cachedSearchSelectionKey = null;
    }

    public PsiElement getPsiElement() {
        return psiElement;
    }

    public String getMethodText() {
        return method != null ? method.name() : requestMethod;
    }

    public String getModuleName() {
        if (cachedModuleName != null) return cachedModuleName;
        cachedModuleName = module != null ? module.getName() : "";
        rebuildDescriptor();
        return cachedModuleName;
    }

    public String getLocationText() {
        if (cachedLocationText != null) return cachedLocationText;
        cachedLocationText = computeLocationText();
        return cachedLocationText;
    }

    private String computeLocationText() {
        if (psiElement instanceof PsiMethod methodElement) {
            PsiClass containingClass = methodElement.getContainingClass();
            String className = containingClass != null ? containingClass.getName() : "";
            return className + "#" + methodElement.getName();
        }
        if (psiElement instanceof KtNamedFunction ktNamedFunction) {
            String className = "";
            if (psiElement.getParent() != null && psiElement.getParent().getParent() instanceof KtClass ktClass) {
                className = ktClass.getName();
            }
            return (className == null ? "" : className) + "#" + ktNamedFunction.getName();
        }
        return "";
    }

    public String getEndpointKey() {
        return descriptor.endpointKey();
    }

    public String getSearchSelectionKey() {
        if (cachedSearchSelectionKey == null) {
            cachedSearchSelectionKey = descriptor.searchSelectionKey();
        }
        return cachedSearchSelectionKey;
    }

    public String getPackageName() {
        return descriptor.packageName() != null ? descriptor.packageName() : "";
    }

    private String computePackageName() {
        if (psiElement instanceof PsiMethod methodElement) {
            PsiClass containingClass = methodElement.getContainingClass();
            if (containingClass != null) {
                String qualifiedName = containingClass.getQualifiedName();
                if (qualifiedName != null) {
                    int lastDot = qualifiedName.lastIndexOf('.');
                    return lastDot > 0 ? qualifiedName.substring(0, lastDot) : "";
                }
            }
        }
        if (psiElement instanceof KtNamedFunction) {
            if (psiElement.getParent() != null && psiElement.getParent().getParent() instanceof KtClass ktClass) {
                String qualifiedName = ktClass.getFqName().asString();
                int lastDot = qualifiedName.lastIndexOf('.');
                return lastDot > 0 ? qualifiedName.substring(0, lastDot) : "";
            }
        }
        return "";
    }

    public String getJavadoc() {
        if (cachedJavadoc != null) return cachedJavadoc;
        cachedJavadoc = computeJavadoc();
        return cachedJavadoc;
    }

    private String computeJavadoc() {
        if (psiElement instanceof PsiMethod method) {
            PsiElement docComment = method.getDocComment();
            if (docComment != null) {
                return docComment.getText();
            }
        }
        return "";
    }

    public String getDescription() {
        return descriptor.description() != null ? descriptor.description() : "";
    }

    private String computeDescription() {
        // 1. Try annotation-based descriptions
        if (psiElement instanceof PsiMethod method) {
            for (PsiAnnotation annotation : method.getAnnotations()) {
                String qualifiedName = annotation.getQualifiedName();
                if (qualifiedName == null) continue;
                // @ApiOperation("xxx")
                if ("io.swagger.annotations.ApiOperation".equals(qualifiedName)) {
                    String value = PsiAnnotationHelper.getAnnotationAttributeValue(annotation, "value");
                    if (value != null && !value.isBlank()) return value;
                }
                // @Operation(summary = "xxx")
                if ("io.swagger.v3.oas.annotations.Operation".equals(qualifiedName)) {
                    String summary = PsiAnnotationHelper.getAnnotationAttributeValue(annotation, "summary");
                    if (summary != null && !summary.isBlank()) return summary;
                }
            }
        }
        // 2. Fall back to javadoc
        if (cachedJavadoc != null && !cachedJavadoc.isBlank()) {
            return cleanJavadocText(cachedJavadoc);
        }
        return "";
    }

    private static String cleanJavadocText(String text) {
        return text.replace("/**", "").replace("*/", "")
                .replaceAll("(?m)^\\s*\\*\\s?", "").trim();
    }

    public String getControllerName() {
        return descriptor.controllerName() != null ? descriptor.controllerName() : "";
    }

    private String computeControllerName() {
        if (psiElement instanceof PsiMethod methodElement) {
            PsiClass containingClass = methodElement.getContainingClass();
            return containingClass != null ? containingClass.getName() : "";
        }
        if (psiElement instanceof KtNamedFunction) {
            if (psiElement.getParent() != null && psiElement.getParent().getParent() instanceof KtClass ktClass) {
                return ktClass.getName();
            }
        }
        return "";
    }

    public String getMethodName() {
        return descriptor.methodName() != null ? descriptor.methodName() : "";
    }

    private String computeMethodName() {
        if (psiElement instanceof PsiMethod methodElement) {
            return methodElement.getName();
        }
        if (psiElement instanceof KtNamedFunction ktNamedFunction) {
            return ktNamedFunction.getName();
        }
        return "";
    }

    /**
     * Returns a pre-lowered concatenation of all searchable fields.
     * Pre-computed at construction time for fast repeated search scoring.
     */
    public String getSearchableText() {
        return descriptor.searchableText();
    }

    // --- Lowercase cache accessors for SearchEngine ---

    public String getLowerUrl() { return descriptor.lowerUrl(); }
    public String getLowerMethodName() { return descriptor.lowerMethodName(); }
    public String getLowerModuleName() { return descriptor.lowerModuleName(); }
    public String getLowerControllerName() { return descriptor.lowerControllerName(); }
    public String getLowerDescription() { return descriptor.lowerDescription(); }
    public String getLowerHttpMethod() { return descriptor.lowerMethodText(); }

    public EndpointDescriptor getDescriptor() {
        return descriptor;
    }

    private void rebuildDescriptor() {
        descriptor = new EndpointDescriptor(
                method,
                requestMethod,
                url,
                cachedControllerName,
                cachedMethodName,
                cachedPackageName,
                cachedDescription,
                cachedModuleName
        );
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof RestServiceItem other)) return false;
        return Objects.equals(url, other.url) && Objects.equals(getMethodText(), other.getMethodText());
    }

    @Override
    public int hashCode() {
        return Objects.hash(url, getMethodText());
    }

    private boolean containsIgnoreCase(String text, String expectedLowerCase) {
        return text != null && text.toLowerCase(Locale.ROOT).contains(expectedLowerCase);
    }
}
