package com.sount.restful.navigation.action;

import com.intellij.ide.util.EditSourceUtil;
import com.intellij.navigation.ItemPresentation;
import com.intellij.navigation.NavigationItem;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.module.Module;
import com.intellij.pom.Navigatable;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.codeStyle.MinusculeMatcher;
import com.intellij.psi.codeStyle.NameUtil;
import com.intellij.util.text.matching.MatchingMode;
import com.sount.restful.common.ToolkitIcons;
import com.sount.restful.method.HttpMethod;
import com.sount.restful.method.action.ModuleHelper;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.kotlin.psi.KtClass;
import org.jetbrains.kotlin.psi.KtNamedFunction;

import javax.swing.*;
import java.util.Locale;
import java.util.Objects;

//RequestMappingNavigationItem
public class RestServiceItem implements NavigationItem {
    private PsiMethod psiMethod; //元素
    private PsiElement psiElement; //元素
    private Module module;

    private String requestMethod; //请求方法 get/post...
    private HttpMethod method;  //请求方法 get/post...

    private String url; //url mapping;
/*
    private String methodName; //方法名称

    private String hostContextPath; // todo 处理 http://
    private PsiClass psiClass;
    private boolean foundRequestBody;*/

    private Navigatable navigationElement;
    private String cachedLocationText; // pre-computed at construction time (inside read action)
    private String cachedJavadoc;      // pre-computed at construction time
    private String cachedModuleName;   // pre-computed at construction time
    private String cachedPackageName;  // pre-computed at construction time

    //        ((KtClass) ((KtClassBody) psiElement.getParent()).getParent()).getModifierList().getAnnotationEntries().get(0).getText()
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
        // Pre-compute PSI-dependent fields at construction time (inside read action)
        this.cachedLocationText = computeLocationText();
        this.cachedJavadoc = computeJavadoc();
        this.cachedPackageName = computePackageName();
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
        if (navigationElement != null) {
            // PSI access (isValid, canNavigate) requires read lock
            Navigatable nav = ReadAction.compute(() -> {
                if (psiElement == null || !psiElement.isValid()) return null;
                return navigationElement.canNavigate() ? navigationElement : null;
            });
            if (nav != null) {
                nav.navigate(requestFocus);
            }
            return;
        }

        // Compute the descriptor inside ReadAction (PSI access requires read lock),
        // then navigate outside ReadAction so the platform can acquire WriteIntentReadAction
        // for opening the editor without causing a nested-lock conflict.
        Navigatable navDescriptor = ReadAction.compute(() -> {
            if (psiElement == null || !psiElement.isValid()) return null;
            return EditSourceUtil.getDescriptor(psiElement);
        });
        if (navDescriptor != null) {
            navDescriptor.navigate(requestFocus);
        }
    }

    @Override
    public boolean canNavigate() {
        if (navigationElement != null) {
            // PSI access (isValid, canNavigate) requires read lock
            return ReadAction.compute(() -> psiElement != null && psiElement.isValid() && navigationElement.canNavigate());
        }
        if (psiElement == null) return false;
        return ReadAction.compute(() -> {
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
                || containsIgnoreCase(getModuleName(), normalizedPattern)) {
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

        //        对应的文件位置显示
        @Nullable
        @Override
        public String getLocationString() {
            String fileName = psiElement.getContainingFile().getName();

            String location = null;

            if (psiElement instanceof PsiMethod psiMethod) {
                location = psiMethod.getContainingClass().getName().concat("#").concat(psiMethod.getName());
            } else if (psiElement instanceof KtNamedFunction) {
                KtNamedFunction ktNamedFunction = (KtNamedFunction) RestServiceItem.this.psiElement;
                String className = ((KtClass) psiElement.getParent().getParent()).getName();
                location = className.concat("#").concat(ktNamedFunction.getName());
            }

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
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getFullUrl() {
        if (module == null) {
            return getUrl();
        }

        ModuleHelper moduleHelper = ModuleHelper.create(module);
        // 处理 Mapping 设置个 value
//        String fullUrl = moduleHelper.buildFullUrl(psiMethod);

        return moduleHelper.getServiceHostPrefix() + getUrl();
    }

/*    public String getFullUrlWithParams() {
        ModuleHelper moduleHelper = ModuleHelper.create(module);
        String urlWithParams = moduleHelper.buildFullUrlWithParams(psiMethod);
        return urlWithParams;
    }*/

    public void setModule(Module module) {
        this.module = module;
        this.cachedModuleName = null; // invalidate cache
    }

/*    public String getHostContextPath() {
        return hostContextPath;
    }

    public boolean isFoundRequestBody() {
        return foundRequestBody;
    }

    public void setFoundRequestBody(boolean foundRequestBody) {
        this.foundRequestBody = foundRequestBody;
    }*/

    public PsiElement getPsiElement() {
        return psiElement;
    }

    public String getMethodText() {
        return method != null ? method.name() : requestMethod;
    }

    public String getModuleName() {
        if (cachedModuleName != null) return cachedModuleName;
        cachedModuleName = module != null ? module.getName() : "";
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
        String methodText = getMethodText();
        return (methodText != null ? methodText : "UNKNOWN") + ":" + (url != null ? url : "");
    }

    public String getSearchSelectionKey() {
        return getEndpointKey() + ":" + getLocationText() + ":" + getModuleName();
    }

    public String getPackageName() {
        return cachedPackageName;
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
        if (psiElement instanceof PsiMethod psiMethod) {
            PsiElement docComment = psiMethod.getDocComment();
            if (docComment != null) {
                return docComment.getText();
            }
        }
        return "";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof RestServiceItem other)) return false;
        return Objects.equals(url, other.url) && Objects.equals(requestMethod, other.requestMethod);
    }

    @Override
    public int hashCode() {
        return Objects.hash(url, requestMethod);
    }

    private boolean containsIgnoreCase(String text, String expectedLowerCase) {
        return text != null && text.toLowerCase(Locale.ROOT).contains(expectedLowerCase);
    }
}
