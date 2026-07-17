package com.sount.restful.endpoint.resolver;


import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtil;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.project.Project;
import com.intellij.psi.CommonClassNames;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.search.GlobalSearchScope;
import com.sount.restful.method.RequestPath;
import com.sount.restful.endpoint.navigation.RestServiceItem;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 单一框架端点解析策略的模板基类。
 *
 * <p>该类统一 scope 创建、异常转换、父类方法遍历和端点对象构建；多策略组合由
 * {@link CompositeEndpointResolver} 负责。</p>
 */
public abstract class AbstractEndpointResolver implements EndpointResolver {
    private static final com.intellij.openapi.diagnostic.Logger LOG =
            com.intellij.openapi.diagnostic.Logger.getInstance(AbstractEndpointResolver.class);

    private final @Nullable Module module;
    private final @Nullable Project project;
    private final EndpointResolutionContext resolutionContext;

    /**
     * 创建模块范围解析策略，并使用独立的单次解析上下文。
     */
    protected AbstractEndpointResolver(@NotNull Module module) {
        this(module, new EndpointResolutionContext());
    }

    /**
     * 创建模块范围解析策略，并复用组合解析器提供的配置快照。
     */
    AbstractEndpointResolver(@NotNull Module module, @NotNull EndpointResolutionContext resolutionContext) {
        this.module = module;
        this.project = null;
        this.resolutionContext = resolutionContext;
    }

    /**
     * 创建项目范围解析策略，并使用独立的单次解析上下文。
     */
    protected AbstractEndpointResolver(@NotNull Project project) {
        this(project, new EndpointResolutionContext());
    }

    /**
     * 创建项目范围解析策略，并复用组合解析器提供的配置快照。
     */
    AbstractEndpointResolver(@NotNull Project project, @NotNull EndpointResolutionContext resolutionContext) {
        this.module = null;
        this.project = project;
        this.resolutionContext = resolutionContext;
    }

    /**
     * 按模板流程解析模块范围端点。
     */
    @Override
    public final List<RestServiceItem> resolveModuleEndpoints() {
        if (module == null) {
            return new ArrayList<>();
        }
        return collectEndpoints(module.getProject(), GlobalSearchScope.moduleScope(module));
    }

    /**
     * 由具体框架策略实现端点发现逻辑。
     */
    protected abstract @NotNull List<RestServiceItem> collectEndpoints(
            @NotNull Project project,
            @NotNull GlobalSearchScope globalSearchScope);

    /**
     * 收集当前类及父类声明的方法，遇到局部 PSI 异常时保留已收集结果。
     */
    @NotNull
    protected List<PsiMethod> getClassMethodsIncludingParents(@NotNull PsiClass psiClass) {
        List<PsiMethod> allMethods = new ArrayList<>();
        PsiClass currentClass = psiClass;
        while (currentClass != null) {
            try {
                if (CommonClassNames.JAVA_LANG_OBJECT.equals(currentClass.getQualifiedName())) {
                    break;
                }
                Collections.addAll(allMethods, currentClass.getMethods());
                currentClass = currentClass.getSuperClass();
            } catch (ProcessCanceledException e) {
                throw e;
            } catch (Throwable e) {
                LOG.debug("Failed to inspect class methods while resolving REST endpoints", e);
                break;
            }
        }
        return allMethods;
    }

    /**
     * 按模板流程解析项目范围端点，并把系统性 PSI/索引错误转换为可重试异常。
     */
    @Override
    public final List<RestServiceItem> resolveProjectEndpoints() {
        Project targetProject = project != null ? project : module != null ? module.getProject() : null;
        if (targetProject == null) {
            return new ArrayList<>();
        }
        try {
            List<RestServiceItem> items = collectEndpoints(
                    targetProject, GlobalSearchScope.projectScope(targetProject));
            return items != null ? items : new ArrayList<>();
        } catch (ProcessCanceledException e) {
            throw e;
        } catch (Throwable e) {
            throw new EndpointResolutionException(
                    "Failed to resolve REST endpoints while project indexes are changing", e);
        }

    }

    /**
     * 依据类级路径与方法级映射创建端点，并应用当前解析周期的模块配置快照。
     *
     * <p><b>性能约束：</b>模块查找每个端点最多执行一次，context-path 由共享解析上下文缓存；
     * 本方法不得为每个端点重复创建配置解析器。</p>
     */
    @NotNull
    protected RestServiceItem createRestServiceItem(PsiElement psiMethod, String classUriPath, RequestPath requestMapping) {
        if (!classUriPath.startsWith("/")) classUriPath = "/".concat(classUriPath);
        if (!classUriPath.endsWith("/")) classUriPath = classUriPath.concat("/");

        String methodPath = requestMapping.getPath();

        if (methodPath.startsWith("/")) methodPath = methodPath.substring(1);
        String requestPath = classUriPath + methodPath;

        RestServiceItem item = new RestServiceItem(psiMethod, requestMapping.getMethod(), requestPath);
        Module endpointModule = module != null ? module : ModuleUtil.findModuleForPsiElement(psiMethod);
        if (endpointModule != null) {
            item.setModule(endpointModule, resolutionContext.getContextPath(endpointModule));
        }
        return item;
    }
}
