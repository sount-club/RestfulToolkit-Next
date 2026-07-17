package com.sount.restful.endpoint.navigation;

import com.intellij.navigation.ItemPresentation;
import com.intellij.navigation.NavigationItem;
import com.intellij.openapi.module.Module;
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
import com.sount.restful.endpoint.model.EndpointDescriptor;
import com.sount.restful.search.domain.MatchField;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.kotlin.psi.KtClass;
import org.jetbrains.kotlin.psi.KtNamedFunction;

import javax.swing.*;
import java.util.Locale;
import java.util.Objects;

//RequestMappingNavigationItem
public class RestServiceItem implements NavigationItem {
    private final PsiMethod psiMethod; //元素
    private final PsiElement psiElement; //元素
    private final EndpointNavigationTarget navigationTarget;
    private Module module;

    private final String requestMethod; //请求方法 get/post...
    private final HttpMethod method;  //请求方法 get/post...

    private final String url; //url mapping;

    private final String cachedJavadoc;      // pre-computed at construction time
    private String cachedModuleName = "";   // pre-computed when the module is assigned
    private String cachedContextPath = ""; // pre-computed when the module is assigned
    private final String cachedPackageName;  // pre-computed at construction time
    private final String cachedDescription;    // pre-computed at construction time
    private final String cachedControllerName; // pre-computed at construction time
    private final String cachedMethodName;     // pre-computed at construction time
    private EndpointDescriptor descriptor;

    public RestServiceItem(PsiElement psiElement, String requestMethod, String urlPath) {
        this.psiElement = psiElement;
        this.navigationTarget = new EndpointNavigationTarget(psiElement);
        this.psiMethod = psiElement instanceof PsiMethod methodElement ? methodElement : null;
        this.requestMethod = requestMethod;
        HttpMethod resolvedMethod = null;
        if (requestMethod != null) {
            resolvedMethod = HttpMethod.getByRequestMethod(requestMethod);
        }
        this.method = resolvedMethod;

        this.url = urlPath;
        // Pre-compute all PSI-dependent fields at construction time (inside read action)
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
        tryNavigate(requestFocus);
    }

    /**
     * Attempts to navigate to this endpoint and reports whether IntelliJ accepted the target.
     * Search UIs use this to avoid closing before navigation has actually started.
     */
    public boolean tryNavigate(boolean requestFocus) {
        return navigationTarget.navigate(requestFocus);
    }

    @Override
    public boolean canNavigate() {
        return navigationTarget.canNavigate();
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
            return "(" + getLocationText() + ")";
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

    public HttpMethod getMethod() {
        return method;
    }

    public String getUrl() {
        return url;
    }

    public String getFullUrl() {
        if (module == null) {
            return getUrl();
        }

        ModuleHelper moduleHelper = ModuleHelper.create(module);
        return moduleHelper.getServiceHostPrefix() + getUrl();
    }

    /**
     * 为端点绑定模块，并立即读取该模块的 context-path。
     *
     * <p><b>兼容约束：</b>该入口保留给独立构造端点的调用方；批量 resolver 应使用带快照值的重载，
     * 避免为每个端点重复扫描配置。</p>
     */
    public void setModule(Module module) {
        String contextPath = module != null ? new ModuleHelper(module).getContextPath() : "";
        setModule(module, contextPath);
    }

    /**
     * 为端点应用解析阶段已经读取的模块配置快照，并重建不可变 descriptor。
     *
     * <p><b>性能约束：</b>本方法不得再次读取模块配置或 PSI；调用方负责保证 context-path
     * 来自当前索引重建周期。</p>
     *
     * @param module 端点所属模块
     * @param contextPath 当前解析周期缓存的 context-path
     */
    @ApiStatus.Internal
    public void setModule(Module module, @NotNull String contextPath) {
        this.module = module;
        this.cachedModuleName = module != null ? module.getName() : "";
        this.cachedContextPath = contextPath;
        rebuildDescriptor();
    }

    /**
     * Returns the Spring context path captured while this endpoint was indexed.
     * Search uses the cached value so background scoring never needs a PSI read.
     */
    public String getContextPath() {
        return cachedContextPath;
    }

    public PsiElement getPsiElement() {
        return psiElement;
    }

    public String getMethodText() {
        return method != null ? method.name() : requestMethod;
    }

    public String getModuleName() {
        return descriptor.moduleName() != null ? descriptor.moduleName() : "";
    }

    public String getLocationText() {
        return descriptor.locationText();
    }

    public String getEndpointKey() {
        return descriptor.endpointKey();
    }

    public String getSearchSelectionKey() {
        return descriptor.searchSelectionKey();
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
                String qualifiedName = PsiAnnotationHelper.getQualifiedName(annotation);
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
